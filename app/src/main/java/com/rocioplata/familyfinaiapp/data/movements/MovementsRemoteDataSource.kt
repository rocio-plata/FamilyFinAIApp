package com.rocioplata.familyfinaiapp.data.movements

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementChanges
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MonthBounds
import com.rocioplata.familyfinaiapp.domain.movements.NewMovement
import com.rocioplata.familyfinaiapp.domain.movements.NoteUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Llamadas de movimientos (contrato §3.4.3). [client] es el cliente autenticado: añade el Bearer y renueva la
 * sesión por su cuenta. [dates] convierte entre el día de un movimiento y los instantes del backend (BG-09).
 * Nunca lanza por red o servidor; relanza la cancelación.
 *
 * Las respuestas de cambio (`PATCH`, `DELETE`) no se leen: el repositorio aplica lo que él mismo envió, así una
 * edición correcta no falla por un campo que el backend no devuelva.
 */
class MovementsRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
    private val dates: MovementDates,
) {
    /**
     * Movimientos del rango. `from` y `to` se envían siempre juntos (BG-02) y el listado no viene ordenado ni
     * paginado (BG-01): ordenarlo es cosa del repositorio.
     */
    suspend fun movements(familyId: String, bounds: MonthBounds): MovementResult<List<Movement>> =
        send(HttpStatusCode.OK, request = {
            client.get {
                itemsUrl(familyId)
                parameter("from", bounds.from.toIsoString())
                parameter("to", bounds.to.toIsoString())
                applyTimeouts()
            }
        }) { dtos: List<MovementDto> ->
            val movements = dtos.map {
                it.toDomain() ?: return@send MovementResult.Failure(MovementFailure.InvalidData)
            }
            MovementResult.Success(movements)
        }

    /**
     * Registra el movimiento siempre con medio de pago y moneda (RF-07, BG-08, BG-10). La respuesta del alta no
     * trae `recordedBy` ni `createdAt` (plan, "Datos y contratos"), así que los pone quien llama; del backend
     * solo se leen el id y el tipo, que lo fija la categoría.
     */
    suspend fun createMovement(
        familyId: String,
        movement: NewMovement,
        recordedBy: String,
        createdAt: Instant,
    ): MovementResult<Movement> {
        val body = buildJsonObject {
            put("amount", movement.amount.amountMinor)
            put("currency", movement.amount.currency)
            put("categoryId", movement.categoryId)
            put("paymentMethodId", movement.paymentMethodId)
            movement.tagId?.let { put("tagId", it) }
            put("title", movement.title)
            movement.note?.let { put("note", it) }
            put("occurredOn", dates.instantToSend(movement.date).toIsoString())
        }
        return send(HttpStatusCode.Created, request = {
            client.post { itemsUrl(familyId); jsonBody(body) }
        }) { dto: CreatedDto ->
            val type = movementTypeOf(dto.type)
            if (dto.id.isBlank() || type == null) {
                return@send MovementResult.Failure(MovementFailure.InvalidData)
            }
            MovementResult.Success(
                Movement(
                    id = dto.id,
                    type = type,
                    amount = movement.amount,
                    categoryId = movement.categoryId,
                    tagId = movement.tagId,
                    paymentMethodId = movement.paymentMethodId,
                    title = movement.title,
                    note = movement.note,
                    date = movement.date,
                    recordedBy = recordedBy,
                    createdAt = createdAt,
                ),
            )
        }
    }

    /**
     * Edita los datos del movimiento con solo lo modificado; la categoría y la etiqueta no van aquí (RF-10),
     * se cambian con [reclassifyMovement]. `NoteUpdate.Remove` envía `note: null`, que borra las observaciones.
     */
    suspend fun updateMovement(
        familyId: String,
        movementId: String,
        changes: MovementChanges,
    ): MovementResult<Unit> {
        require(changes.hasDataChanges) { "Una edición sin cambios en los datos no se envía al backend" }
        val body = buildJsonObject {
            changes.amount?.let {
                put("amount", it.amountMinor)
                put("currency", it.currency)
            }
            changes.title?.let { put("title", it) }
            when (val note = changes.note) {
                NoteUpdate.Unchanged -> Unit
                NoteUpdate.Remove -> put("note", JsonNull)
                is NoteUpdate.Set -> put("note", note.text)
            }
            changes.date?.let { put("occurredOn", dates.instantToSend(it).toIsoString()) }
            changes.paymentMethodId?.let { put("paymentMethodId", it) }
        }
        return sendDiscardingBody(HttpStatusCode.OK) {
            client.patch { itemsUrl(familyId, movementId); jsonBody(body) }
        }
    }

    /** Cambia categoría y etiqueta; una etiqueta nula se envía como `null` explícito para quitarla. */
    suspend fun reclassifyMovement(
        familyId: String,
        movementId: String,
        categoryId: String,
        tagId: String?,
    ): MovementResult<Unit> {
        val body = buildJsonObject {
            put("newCategoryId", categoryId)
            if (tagId == null) put("newTagId", JsonNull) else put("newTagId", tagId)
        }
        return sendDiscardingBody(HttpStatusCode.OK) {
            client.patch { itemsUrl(familyId, movementId, "category"); jsonBody(body) }
        }
    }

    /** Sin cuerpo ni `Content-Type`: el backend rechaza un `DELETE` con tipo JSON y cuerpo vacío. */
    suspend fun deleteMovement(familyId: String, movementId: String): MovementResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) {
            client.delete { itemsUrl(familyId, movementId); applyTimeouts() }
        }

    private fun HttpRequestBuilder.jsonBody(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body)
        applyTimeouts()
    }

    private fun HttpRequestBuilder.itemsUrl(familyId: String, vararg more: String) {
        url {
            takeFrom(baseUrl.trimEnd('/'))
            appendPathSegments("families", familyId, "items", *more, encodeSlash = true)
        }
    }

    private fun HttpRequestBuilder.applyTimeouts() {
        timeout {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    private suspend inline fun <reified D, T> send(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
        map: (D) -> MovementResult<T>,
    ): MovementResult<T> {
        val response = when (val call = call(expected, request)) {
            is MovementResult.Failure -> return call
            is MovementResult.Success -> call.value
        }
        return try {
            map(response.body<D>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MovementResult.Failure(MovementFailure.InvalidData)
        }
    }

    private suspend inline fun sendDiscardingBody(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
    ): MovementResult<Unit> = when (val call = call(expected, request)) {
        is MovementResult.Failure -> call
        is MovementResult.Success -> MovementResult.Success(Unit)
    }

    private suspend inline fun call(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
    ): MovementResult<HttpResponse> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return MovementResult.Failure(MovementFailure.Network)
        } catch (e: Exception) {
            return MovementResult.Failure(MovementFailure.Unknown)
        }
        return if (response.status == expected) {
            MovementResult.Success(response)
        } else {
            MovementResult.Failure(failureOf(response))
        }
    }

    // Solo se interpreta `error`; `message` no se lee ni se registra. El código manda sobre el estado porque
    // un `404` puede ser una categoría o un medio de pago que ya no existen y no el movimiento.
    private suspend fun failureOf(response: HttpResponse): MovementFailure {
        val code = try {
            response.body<ErrorDto>().error
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return when {
            response.status == HttpStatusCode.Forbidden -> MovementFailure.NotAllowed
            code in FUTURE_DATE_CODES -> MovementFailure.FutureDate
            code in CATEGORY_UNAVAILABLE_CODES -> MovementFailure.CategoryUnavailable
            code in TAG_UNAVAILABLE_CODES -> MovementFailure.TagUnavailable
            code in PAYMENT_METHOD_UNAVAILABLE_CODES -> MovementFailure.PaymentMethodUnavailable
            code in TYPE_MISMATCH_CODES -> MovementFailure.TypeMismatch
            code == CURRENCY_MISMATCH_CODE -> MovementFailure.CurrencyMismatch
            code in NOT_FOUND_CODES || response.status == HttpStatusCode.NotFound -> MovementFailure.NotFound
            response.status == HttpStatusCode.Unauthorized -> MovementFailure.Unauthorized
            response.status == HttpStatusCode.BadRequest -> MovementFailure.InvalidData
            else -> MovementFailure.Unknown
        }
    }

    private fun MovementDto.toDomain(): Movement? {
        val movementType = movementTypeOf(type) ?: return null
        if (amount.amount < 0 || !Money.isKnown(amount.currency)) return null
        val occurred = parseInstant(occurredOn) ?: return null
        val created = parseInstant(createdAt) ?: return null
        return Movement(
            id = id,
            type = movementType,
            amount = Money(amount.amount, amount.currency),
            categoryId = categoryId,
            tagId = tagId,
            paymentMethodId = paymentMethodId,
            title = title,
            note = note,
            date = dates.dayOf(occurred),
            recordedBy = recordedBy,
            createdAt = created,
        )
    }

    private fun movementTypeOf(value: String): MovementType? = MovementType.entries.firstOrNull { it.name == value }

    private fun parseInstant(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    // El backend interpreta el instante con `new Date(...)`: milisegundos y `Z` bastan y evitan nanosegundos.
    private fun Instant.toIsoString(): String = truncatedTo(ChronoUnit.MILLIS).toString()

    @Serializable
    private data class MoneyDto(val amount: Long, val currency: String)

    @Serializable
    private data class MovementDto(
        val id: String,
        val recordedBy: String,
        val type: String,
        val amount: MoneyDto,
        val categoryId: String,
        val tagId: String? = null,
        val paymentMethodId: String? = null,
        val title: String,
        val note: String? = null,
        val occurredOn: String,
        val createdAt: String,
    )

    @Serializable
    private data class CreatedDto(val id: String, val type: String)

    @Serializable
    private data class ErrorDto(val error: String? = null)

    private companion object {
        // El contrato documenta `FINANCIAL.*`, pero el backend emite `FINANCIAL_TRACKING.*` (BG-07). Se
        // aceptan los dos prefijos para no depender de cuándo se alinee la documentación.
        private fun financial(vararg codes: String) =
            codes.flatMap { listOf("FINANCIAL_TRACKING.$it", "FINANCIAL.$it") }.toSet()

        val FUTURE_DATE_CODES = financial("FUTURE_TRANSACTION_DATE")
        val CATEGORY_UNAVAILABLE_CODES = financial("CATEGORY_NOT_ACTIVE", "CATEGORY_NOT_FOUND")
        val TAG_UNAVAILABLE_CODES = financial("TAG_NOT_ACTIVE", "TAG_DOES_NOT_BELONG_TO_CATEGORY")
        val PAYMENT_METHOD_UNAVAILABLE_CODES = financial(
            "PAYMENT_METHOD_NOT_ACTIVE",
            "PAYMENT_METHOD_NOT_FOUND",
            "NO_DEFAULT_PAYMENT_METHOD_SET",
        )
        val TYPE_MISMATCH_CODES = financial("CANNOT_RECLASSIFY_ACROSS_TYPES")
        val NOT_FOUND_CODES = financial("FINANCIAL_ITEM_NOT_FOUND")
        const val CURRENCY_MISMATCH_CODE = "SHARED.CURRENCY_MISMATCH"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
