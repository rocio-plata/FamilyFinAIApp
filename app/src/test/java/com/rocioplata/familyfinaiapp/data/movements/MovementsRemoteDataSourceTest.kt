package com.rocioplata.familyfinaiapp.data.movements

import com.rocioplata.familyfinaiapp.data.network.SessionAuth
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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MovementsRemoteDataSourceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val zone = ZoneId.of("America/Santiago")

    // 2026-11-16 15:00 UTC = 12:00 en Santiago (verano, UTC-3).
    private val now = Instant.parse("2026-11-16T15:00:00.123456Z")
    private val dates = MovementDates(Clock.fixed(now, zone))

    private fun source(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): MovementsRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
            install(SessionAuth) { accessToken = { "tok" } }
        }
        return MovementsRemoteDataSource(client, "http://localhost:3000", dates)
    }

    private fun reply(
        status: HttpStatusCode,
        body: String = "",
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(body, status, jsonHeaders) }

    private fun errorBody(code: String) = """{"error":"$code","message":"texto que no se lee"}"""

    private fun movementJson(
        id: String = "i-1",
        type: String = "EXPENSE",
        paymentMethod: String? = "\"m-1\"",
        occurredOn: String = "2026-11-10T15:00:00.000Z",
        amount: String = """{"amount":25990,"currency":"CLP"}""",
    ) = """{"id":"$id","familyId":"f-1","recordedBy":"u-1","type":"$type","amount":$amount,
        |"categoryId":"c-1","tagId":"t-1",${paymentMethod?.let { "\"paymentMethodId\":$it," } ?: ""}
        |"title":"Súper","note":"nota","occurredOn":"$occurredOn","createdAt":"2026-11-10T15:05:00.000Z"}"""
        .trimMargin()

    private fun <T> MovementResult<T>.failure(): MovementFailure = (this as MovementResult.Failure).failure

    private fun <T> MovementResult<T>.value(): T = (this as MovementResult.Success).value

    private fun HttpRequestData.json(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text) as JsonObject

    private val bounds = MonthBounds(
        Instant.parse("2026-11-01T03:00:00Z"),
        Instant.parse("2026-12-01T02:59:59.999Z"),
    )

    private val newMovement = NewMovement(
        amount = Money(25990, "CLP"),
        categoryId = "c-1",
        tagId = "t-1",
        paymentMethodId = "m-1",
        title = "Súper",
        note = "nota",
        date = LocalDate.of(2026, 11, 10),
    )

    // --- Listado ---

    @Test
    fun listado_pideElMesConFromYToJuntosYEnviaBearer() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var from: String? = null
        var to: String? = null
        var auth: String? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            from = it.url.parameters["from"]
            to = it.url.parameters["to"]
            auth = it.headers[HttpHeaders.Authorization]
            respond("[]", HttpStatusCode.OK, jsonHeaders)
        }

        source.movements("f-1", bounds)

        assertEquals(HttpMethod.Get, method)
        assertEquals("/families/f-1/items", path)
        assertEquals("2026-11-01T03:00:00Z", from)
        assertEquals("2026-12-01T02:59:59.999Z", to)
        assertEquals("Bearer tok", auth)
    }

    @Test
    fun listado_mapeaCadaCampoYConvierteElInstanteAlDiaDelDispositivo() = runTest {
        // 02:30 UTC del día 11 es todavía el día 10 en Santiago.
        val source = source(reply(HttpStatusCode.OK, "[${movementJson(occurredOn = "2026-11-11T02:30:00.000Z")}]"))

        val movement = source.movements("f-1", bounds).value().single()

        assertEquals(
            Movement(
                id = "i-1",
                type = MovementType.EXPENSE,
                amount = Money(25990, "CLP"),
                categoryId = "c-1",
                tagId = "t-1",
                paymentMethodId = "m-1",
                title = "Súper",
                note = "nota",
                date = LocalDate.of(2026, 11, 10),
                recordedBy = "u-1",
                createdAt = Instant.parse("2026-11-10T15:05:00Z"),
            ),
            movement,
        )
    }

    @Test
    fun listado_respuestaSinMedioDePago_quedaNulo() = runTest {
        val source = source(reply(HttpStatusCode.OK, "[${movementJson(paymentMethod = null)}]"))

        assertNull(source.movements("f-1", bounds).value().single().paymentMethodId)
    }

    @Test
    fun listado_medioDePagoNulo_quedaNulo() = runTest {
        val source = source(reply(HttpStatusCode.OK, "[${movementJson(paymentMethod = "null")}]"))

        assertNull(source.movements("f-1", bounds).value().single().paymentMethodId)
    }

    @Test
    fun listado_tipoIngreso_seLee() = runTest {
        val source = source(reply(HttpStatusCode.OK, "[${movementJson(type = "INCOME")}]"))

        assertEquals(MovementType.INCOME, source.movements("f-1", bounds).value().single().type)
    }

    @Test
    fun listado_vacio_esExitoSinMovimientos() = runTest {
        val source = source(reply(HttpStatusCode.OK, "[]"))

        assertTrue(source.movements("f-1", bounds).value().isEmpty())
    }

    @Test
    fun listado_jsonInesperado_esInvalidData() = runTest {
        val source = source(reply(HttpStatusCode.OK, """{"no":"es una lista"}"""))

        assertEquals(MovementFailure.InvalidData, source.movements("f-1", bounds).failure())
    }

    @Test
    fun listado_tipoDesconocido_esInvalidData() = runTest {
        val source = source(reply(HttpStatusCode.OK, "[${movementJson(type = "TRANSFER")}]"))

        assertEquals(MovementFailure.InvalidData, source.movements("f-1", bounds).failure())
    }

    @Test
    fun listado_monedaDesconocida_esInvalidData() = runTest {
        val amount = """{"amount":10,"currency":"ZZZZ"}"""
        val source = source(reply(HttpStatusCode.OK, "[${movementJson(amount = amount)}]"))

        assertEquals(MovementFailure.InvalidData, source.movements("f-1", bounds).failure())
    }

    @Test
    fun listado_fechaIlegible_esInvalidData() = runTest {
        val source = source(reply(HttpStatusCode.OK, "[${movementJson(occurredOn = "ayer")}]"))

        assertEquals(MovementFailure.InvalidData, source.movements("f-1", bounds).failure())
    }

    @Test
    fun listado_unElementoInvalido_descartaTodaLaRespuesta() = runTest {
        val body = "[${movementJson()},${movementJson(id = "i-2", type = "X")}]"
        val source = source(reply(HttpStatusCode.OK, body))

        assertEquals(MovementFailure.InvalidData, source.movements("f-1", bounds).failure())
    }

    // --- Alta ---

    @Test
    fun alta_enviaTodosLosCamposConMedioDePagoYMoneda() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body: JsonObject? = null
        var contentType: String? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            body = it.json()
            contentType = it.body.contentType?.toString()
            respond("""{"id":"i-9","type":"EXPENSE"}""", HttpStatusCode.Created, jsonHeaders)
        }

        source.createMovement("f-1", newMovement, "u-1", now)

        assertEquals(HttpMethod.Post, method)
        assertEquals("/families/f-1/items", path)
        assertTrue(contentType!!.startsWith("application/json"))
        val sent = body!!
        assertEquals(JsonPrimitive(25990), sent["amount"])
        assertEquals(JsonPrimitive("CLP"), sent["currency"])
        assertEquals(JsonPrimitive("c-1"), sent["categoryId"])
        assertEquals(JsonPrimitive("m-1"), sent["paymentMethodId"])
        assertEquals(JsonPrimitive("t-1"), sent["tagId"])
        assertEquals(JsonPrimitive("Súper"), sent["title"])
        assertEquals(JsonPrimitive("nota"), sent["note"])
        // Día pasado: 12:00 locales de Santiago (UTC-3) = 15:00 UTC.
        assertEquals(JsonPrimitive("2026-11-10T15:00:00Z"), sent["occurredOn"])
    }

    @Test
    fun alta_sinEtiquetaNiObservaciones_noLasEnvia() = runTest {
        var body: JsonObject? = null
        val source = source {
            body = it.json()
            respond("""{"id":"i-9","type":"EXPENSE"}""", HttpStatusCode.Created, jsonHeaders)
        }

        source.createMovement("f-1", newMovement.copy(tagId = null, note = null), "u-1", now)

        assertFalse(body!!.containsKey("tagId"))
        assertFalse(body!!.containsKey("note"))
    }

    @Test
    fun alta_hoyEnviaElInstanteActualSinNanosegundos() = runTest {
        var body: JsonObject? = null
        val source = source {
            body = it.json()
            respond("""{"id":"i-9","type":"EXPENSE"}""", HttpStatusCode.Created, jsonHeaders)
        }

        source.createMovement("f-1", newMovement.copy(date = LocalDate.of(2026, 11, 16)), "u-1", now)

        assertEquals(JsonPrimitive("2026-11-16T15:00:00.123Z"), body!!["occurredOn"])
    }

    @Test
    fun alta_completaElMovimientoConLoEnviadoYElTipoDelBackend() = runTest {
        val source = source(reply(HttpStatusCode.Created, """{"id":"i-9","type":"INCOME"}"""))

        val movement = source.createMovement("f-1", newMovement, "u-7", now).value()

        assertEquals(
            Movement(
                id = "i-9",
                type = MovementType.INCOME,
                amount = Money(25990, "CLP"),
                categoryId = "c-1",
                tagId = "t-1",
                paymentMethodId = "m-1",
                title = "Súper",
                note = "nota",
                date = LocalDate.of(2026, 11, 10),
                recordedBy = "u-7",
                createdAt = now,
            ),
            movement,
        )
    }

    @Test
    fun alta_respuestaSinId_esInvalidData() = runTest {
        val source = source(reply(HttpStatusCode.Created, """{"type":"EXPENSE"}"""))

        assertEquals(MovementFailure.InvalidData, source.createMovement("f-1", newMovement, "u-1", now).failure())
    }

    @Test
    fun alta_estado200_noEsExito() = runTest {
        val source = source(reply(HttpStatusCode.OK, """{"id":"i-9","type":"EXPENSE"}"""))

        assertEquals(MovementFailure.Unknown, source.createMovement("f-1", newMovement, "u-1", now).failure())
    }

    // --- Edición ---

    @Test
    fun edicion_enviaSoloLosCamposModificados() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body: JsonObject? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            body = it.json()
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }

        val result = source.updateMovement("f-1", "i-1", MovementChanges(title = "Jumbo"))

        assertTrue(result is MovementResult.Success)
        assertEquals(HttpMethod.Patch, method)
        assertEquals("/families/f-1/items/i-1", path)
        assertEquals(setOf("title"), body!!.keys)
        assertEquals(JsonPrimitive("Jumbo"), body!!["title"])
    }

    @Test
    fun edicion_montoEnviaMontoYMoneda() = runTest {
        var body: JsonObject? = null
        val source = source {
            body = it.json()
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }

        source.updateMovement("f-1", "i-1", MovementChanges(amount = Money(28000, "CLP")))

        assertEquals(setOf("amount", "currency"), body!!.keys)
        assertEquals(JsonPrimitive(28000), body!!["amount"])
        assertEquals(JsonPrimitive("CLP"), body!!["currency"])
    }

    @Test
    fun edicion_quitarObservacionesEnviaNullExplicito() = runTest {
        var body: JsonObject? = null
        val source = source {
            body = it.json()
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }

        source.updateMovement("f-1", "i-1", MovementChanges(note = NoteUpdate.Remove))

        assertEquals(setOf("note"), body!!.keys)
        assertEquals(JsonNull, body!!["note"])
    }

    @Test
    fun edicion_nuevasObservacionesYFechaYMedioDePago() = runTest {
        var body: JsonObject? = null
        val source = source {
            body = it.json()
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }

        source.updateMovement(
            "f-1",
            "i-1",
            MovementChanges(
                note = NoteUpdate.Set("otra"),
                date = LocalDate.of(2026, 11, 2),
                paymentMethodId = "m-2",
            ),
        )

        assertEquals(setOf("note", "occurredOn", "paymentMethodId"), body!!.keys)
        assertEquals("otra", body!!["note"]!!.jsonPrimitive.content)
        assertEquals("2026-11-02T15:00:00Z", body!!["occurredOn"]!!.jsonPrimitive.content)
        assertEquals("m-2", body!!["paymentMethodId"]!!.jsonPrimitive.content)
    }

    @Test
    fun edicion_sinCambiosDeDatos_esUnErrorDeProgramacion() {
        val source = source(reply(HttpStatusCode.OK, "{}"))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { source.updateMovement("f-1", "i-1", MovementChanges()) }
        }
    }

    // --- Reclasificación ---

    @Test
    fun reclasificacion_enviaCategoriaYEtiqueta() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body: JsonObject? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            body = it.json()
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }

        val result = source.reclassifyMovement("f-1", "i-1", "c-2", "t-2")

        assertTrue(result is MovementResult.Success)
        assertEquals(HttpMethod.Patch, method)
        assertEquals("/families/f-1/items/i-1/category", path)
        assertEquals(JsonPrimitive("c-2"), body!!["newCategoryId"])
        assertEquals(JsonPrimitive("t-2"), body!!["newTagId"])
    }

    @Test
    fun reclasificacion_sinEtiquetaEnviaNullExplicito() = runTest {
        var body: JsonObject? = null
        val source = source {
            body = it.json()
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }

        source.reclassifyMovement("f-1", "i-1", "c-2", null)

        assertEquals(JsonNull, body!!["newTagId"])
    }

    // --- Eliminación ---

    @Test
    fun eliminacion_noEnviaCuerpoNiContentType() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var contentType: String? = null
        var hasContentTypeHeader = true
        val source = source {
            method = it.method
            path = it.url.encodedPath
            contentType = it.body.contentType?.toString()
            hasContentTypeHeader = it.headers.contains(HttpHeaders.ContentType)
            respond("", HttpStatusCode.NoContent)
        }

        val result = source.deleteMovement("f-1", "i-1")

        assertTrue(result is MovementResult.Success)
        assertEquals(HttpMethod.Delete, method)
        assertEquals("/families/f-1/items/i-1", path)
        assertNull(contentType)
        assertFalse(hasContentTypeHeader)
    }

    // --- Errores ---

    private val prefixes = listOf("FINANCIAL_TRACKING", "FINANCIAL")

    private fun assertMapsEveryPrefix(
        status: HttpStatusCode,
        code: String,
        expected: MovementFailure,
    ) = runTest {
        for (prefix in prefixes) {
            val source = source(reply(status, errorBody("$prefix.$code")))
            assertEquals("$prefix.$code", expected, source.movements("f-1", bounds).failure())
            assertEquals("$prefix.$code", expected, source.deleteMovement("f-1", "i-1").failure())
        }
    }

    @Test
    fun error_fechaFutura() =
        assertMapsEveryPrefix(HttpStatusCode.BadRequest, "FUTURE_TRANSACTION_DATE", MovementFailure.FutureDate)

    @Test
    fun error_categoriaNoActiva() =
        assertMapsEveryPrefix(HttpStatusCode.BadRequest, "CATEGORY_NOT_ACTIVE", MovementFailure.CategoryUnavailable)

    @Test
    fun error_categoriaNoEncontrada_aunqueSea404() =
        assertMapsEveryPrefix(HttpStatusCode.NotFound, "CATEGORY_NOT_FOUND", MovementFailure.CategoryUnavailable)

    @Test
    fun error_etiquetaNoActiva() =
        assertMapsEveryPrefix(HttpStatusCode.BadRequest, "TAG_NOT_ACTIVE", MovementFailure.TagUnavailable)

    @Test
    fun error_etiquetaDeOtraCategoria() = assertMapsEveryPrefix(
        HttpStatusCode.BadRequest,
        "TAG_DOES_NOT_BELONG_TO_CATEGORY",
        MovementFailure.TagUnavailable,
    )

    @Test
    fun error_medioDePagoNoActivo() = assertMapsEveryPrefix(
        HttpStatusCode.BadRequest,
        "PAYMENT_METHOD_NOT_ACTIVE",
        MovementFailure.PaymentMethodUnavailable,
    )

    @Test
    fun error_medioDePagoNoEncontrado_aunqueSea404() = assertMapsEveryPrefix(
        HttpStatusCode.NotFound,
        "PAYMENT_METHOD_NOT_FOUND",
        MovementFailure.PaymentMethodUnavailable,
    )

    @Test
    fun error_sinMedioPredeterminado() = assertMapsEveryPrefix(
        HttpStatusCode.BadRequest,
        "NO_DEFAULT_PAYMENT_METHOD_SET",
        MovementFailure.PaymentMethodUnavailable,
    )

    @Test
    fun error_reclasificarEntreTipos() = assertMapsEveryPrefix(
        HttpStatusCode.BadRequest,
        "CANNOT_RECLASSIFY_ACROSS_TYPES",
        MovementFailure.TypeMismatch,
    )

    @Test
    fun error_movimientoNoEncontrado() =
        assertMapsEveryPrefix(HttpStatusCode.NotFound, "FINANCIAL_ITEM_NOT_FOUND", MovementFailure.NotFound)

    @Test
    fun error_monedaDistinta() = runTest {
        val source = source(reply(HttpStatusCode.Conflict, errorBody("SHARED.CURRENCY_MISMATCH")))

        assertEquals(MovementFailure.CurrencyMismatch, source.movements("f-1", bounds).failure())
    }

    @Test
    fun error_403_esNotAllowedSeaCualSeaElCodigo() = runTest {
        for (code in listOf("FAMILY.NOT_A_MEMBER", "FAMILY.INSUFFICIENT_ROLE", "FINANCIAL_TRACKING.INSUFFICIENT_ROLE")) {
            val source = source(reply(HttpStatusCode.Forbidden, errorBody(code)))
            assertEquals(code, MovementFailure.NotAllowed, source.movements("f-1", bounds).failure())
        }
        val noBody = source(reply(HttpStatusCode.Forbidden))
        assertEquals(MovementFailure.NotAllowed, noBody.deleteMovement("f-1", "i-1").failure())
    }

    @Test
    fun error_404SinCodigo_esNotFound() = runTest {
        val source = source(reply(HttpStatusCode.NotFound))

        assertEquals(MovementFailure.NotFound, source.deleteMovement("f-1", "i-1").failure())
    }

    @Test
    fun error_400SinCodigoConocido_esInvalidData() = runTest {
        val source = source(reply(HttpStatusCode.BadRequest, errorBody("FINANCIAL_TRACKING.INVALID_TITLE")))

        assertEquals(MovementFailure.InvalidData, source.createMovement("f-1", newMovement, "u-1", now).failure())
    }

    @Test
    fun error_401_esUnauthorized() = runTest {
        val source = source(reply(HttpStatusCode.Unauthorized, errorBody("AUTH.UNAUTHORIZED")))

        assertEquals(MovementFailure.Unauthorized, source.movements("f-1", bounds).failure())
    }

    @Test
    fun error_500_esUnknown() = runTest {
        val source = source(reply(HttpStatusCode.InternalServerError, errorBody("INTERNAL")))

        assertEquals(MovementFailure.Unknown, source.movements("f-1", bounds).failure())
    }

    @Test
    fun error_cuerpoDeErrorIlegible_seTrataPorEstado() = runTest {
        val source = source(reply(HttpStatusCode.InternalServerError, "<html>"))

        assertEquals(MovementFailure.Unknown, source.movements("f-1", bounds).failure())
    }

    @Test
    fun error_ioException_esNetwork() = runTest {
        val source = source { throw IOException("sin red") }

        assertEquals(MovementFailure.Network, source.movements("f-1", bounds).failure())
        assertEquals(MovementFailure.Network, source.createMovement("f-1", newMovement, "u-1", now).failure())
        assertEquals(MovementFailure.Network, source.deleteMovement("f-1", "i-1").failure())
    }

    @Test
    fun error_esperaExcesiva_esNetwork() = runTest {
        val source = source { throw HttpRequestTimeoutException("http://localhost", 15_000) }

        assertEquals(MovementFailure.Network, source.movements("f-1", bounds).failure())
    }

    @Test
    fun error_excepcionInesperada_esUnknown() = runTest {
        val source = source { throw IllegalStateException("fallo") }

        assertEquals(MovementFailure.Unknown, source.movements("f-1", bounds).failure())
    }

    @Test
    fun cancelacion_seRelanza() {
        val source = source { throw CancellationException("cancelado") }

        assertThrows(CancellationException::class.java) {
            runBlocking { source.movements("f-1", bounds) }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { source.deleteMovement("f-1", "i-1") }
        }
    }

    @Test
    fun mesPedidoConLimitesDelDominio() = runTest {
        var from: String? = null
        var to: String? = null
        val source = source {
            from = it.url.parameters["from"]
            to = it.url.parameters["to"]
            respond("[]", HttpStatusCode.OK, jsonHeaders)
        }

        source.movements("f-1", dates.boundsOf(YearMonth.of(2026, 11)))

        // Noviembre en Santiago (UTC-3 en verano austral).
        assertEquals("2026-11-01T03:00:00Z", from)
        assertEquals("2026-12-01T02:59:59.999Z", to)
    }
}
