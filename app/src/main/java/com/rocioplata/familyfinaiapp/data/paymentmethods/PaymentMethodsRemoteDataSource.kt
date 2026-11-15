package com.rocioplata.familyfinaiapp.data.paymentmethods

import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Llamadas de medios de pago (contrato §3.4.1). [client] es el cliente autenticado: añade el Bearer y
 * renueva la sesión por su cuenta. Nunca lanza por red o servidor; relanza la cancelación.
 */
class PaymentMethodsRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /** Todos los medios de la familia, archivados incluidos, en el orden que devuelve el backend. */
    suspend fun paymentMethods(familyId: String): PaymentMethodResult<List<PaymentMethod>> =
        send(HttpStatusCode.OK, request = {
            client.get {
                paymentMethodsUrl(familyId)
                parameter("includeDeprecated", true)
                applyTimeouts()
            }
        }) { dtos: List<PaymentMethodDto> ->
            val methods = dtos.map {
                it.toDomain() ?: return@send PaymentMethodResult.Failure(PaymentMethodFailure.InvalidData)
            }
            PaymentMethodResult.Success(methods)
        }

    suspend fun createPaymentMethod(familyId: String, name: String): PaymentMethodResult<PaymentMethod> {
        val body = buildJsonObject { put("name", name) }
        return send(HttpStatusCode.Created, request = {
            client.post { paymentMethodsUrl(familyId); jsonBody(body) }
        }) { dto: PaymentMethodDto ->
            val method = dto.toDomain()
                ?: return@send PaymentMethodResult.Failure(PaymentMethodFailure.InvalidData)
            PaymentMethodResult.Success(method)
        }
    }

    suspend fun renamePaymentMethod(
        familyId: String,
        paymentMethodId: String,
        name: String,
    ): PaymentMethodResult<Unit> {
        val body = buildJsonObject { put("name", name) }
        return sendDiscardingBody(HttpStatusCode.OK) {
            client.patch { paymentMethodsUrl(familyId, paymentMethodId); jsonBody(body) }
        }
    }

    suspend fun deprecatePaymentMethod(familyId: String, paymentMethodId: String): PaymentMethodResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) {
            client.post { paymentMethodsUrl(familyId, paymentMethodId, "deprecate"); applyTimeouts() }
        }

    suspend fun deletePaymentMethod(familyId: String, paymentMethodId: String): PaymentMethodResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) {
            client.delete { paymentMethodsUrl(familyId, paymentMethodId); applyTimeouts() }
        }

    private fun HttpRequestBuilder.jsonBody(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body)
        applyTimeouts()
    }

    private fun HttpRequestBuilder.paymentMethodsUrl(familyId: String, vararg more: String) {
        url {
            takeFrom(baseUrl.trimEnd('/'))
            appendPathSegments("families", familyId, "payment-methods", *more, encodeSlash = true)
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
        map: (D) -> PaymentMethodResult<T>,
    ): PaymentMethodResult<T> {
        val response = when (val call = call(expected, request)) {
            is PaymentMethodResult.Failure -> return call
            is PaymentMethodResult.Success -> call.value
        }
        return try {
            map(response.body<D>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PaymentMethodResult.Failure(PaymentMethodFailure.InvalidData)
        }
    }

    /** Para las respuestas sin cuerpo útil (`204`) o cuyo resultado ya conoce el repositorio. */
    private suspend inline fun sendDiscardingBody(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
    ): PaymentMethodResult<Unit> = when (val call = call(expected, request)) {
        is PaymentMethodResult.Failure -> call
        is PaymentMethodResult.Success -> PaymentMethodResult.Success(Unit)
    }

    private suspend inline fun call(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
    ): PaymentMethodResult<HttpResponse> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return PaymentMethodResult.Failure(PaymentMethodFailure.Network)
        } catch (e: Exception) {
            return PaymentMethodResult.Failure(PaymentMethodFailure.Unknown)
        }
        return if (response.status == expected) {
            PaymentMethodResult.Success(response)
        } else {
            PaymentMethodResult.Failure(failureOf(response))
        }
    }

    // Solo se interpreta `error`; `message` no se lee ni se registra.
    private suspend fun failureOf(response: HttpResponse): PaymentMethodFailure {
        val code = try {
            response.body<ErrorDto>().error
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return when {
            response.status == HttpStatusCode.NotFound -> PaymentMethodFailure.NotFound
            code in INSUFFICIENT_ROLE_CODES -> PaymentMethodFailure.InsufficientRole
            code in DUPLICATE_NAME_CODES -> PaymentMethodFailure.DuplicateName
            code in INVALID_NAME_CODES -> PaymentMethodFailure.InvalidName
            code in IS_SOMEONES_DEFAULT_CODES -> PaymentMethodFailure.IsSomeonesDefault
            code in HAS_ASSOCIATED_ITEMS_CODES -> PaymentMethodFailure.HasAssociatedItems
            response.status == HttpStatusCode.Unauthorized -> PaymentMethodFailure.Unauthorized
            else -> PaymentMethodFailure.Unknown
        }
    }

    private fun PaymentMethodDto.toDomain(): PaymentMethod? {
        val methodStatus = PaymentMethodStatus.entries.firstOrNull { it.name == status } ?: return null
        return PaymentMethod(id, name, methodStatus)
    }

    @Serializable
    private data class PaymentMethodDto(
        val id: String,
        val name: String,
        val status: String,
    )

    @Serializable
    private data class ErrorDto(val error: String? = null)

    private companion object {
        // El contrato (§4 y §9) documenta los códigos como `FINANCIAL.*`, pero el backend los emite como
        // `FINANCIAL_TRACKING.*` (leído en su código, ver BG-04). Se aceptan los dos prefijos para no
        // depender de cuándo se alinee la documentación.
        private fun financial(code: String) = setOf("FINANCIAL_TRACKING.$code", "FINANCIAL.$code")

        val INSUFFICIENT_ROLE_CODES = setOf("FAMILY.INSUFFICIENT_ROLE") + financial("INSUFFICIENT_ROLE")
        val DUPLICATE_NAME_CODES = financial("DUPLICATE_PAYMENT_METHOD_NAME")
        val INVALID_NAME_CODES = financial("INVALID_PAYMENT_METHOD_NAME")
        val IS_SOMEONES_DEFAULT_CODES = financial("PAYMENT_METHOD_IS_SOMEONES_DEFAULT")
        val HAS_ASSOCIATED_ITEMS_CODES = financial("PAYMENT_METHOD_HAS_ASSOCIATED_ITEMS")
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
