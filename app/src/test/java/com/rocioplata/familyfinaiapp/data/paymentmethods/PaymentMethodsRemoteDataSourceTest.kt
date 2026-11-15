package com.rocioplata.familyfinaiapp.data.paymentmethods

import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PaymentMethodsRemoteDataSourceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun source(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): PaymentMethodsRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
            install(SessionAuth) { accessToken = { "tok" } }
        }
        return PaymentMethodsRemoteDataSource(client, "http://localhost:3000")
    }

    private fun reply(
        status: HttpStatusCode,
        body: String = "",
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(body, status, jsonHeaders) }

    private fun errorBody(code: String) = """{"error":"$code","message":"texto que no se lee"}"""

    private fun methodJson(id: String, name: String, status: String = "ACTIVE") =
        """{"id":"$id","familyId":"f-1","name":"$name","status":"$status"}"""

    private fun <T> PaymentMethodResult<T>.failure(): PaymentMethodFailure =
        (this as PaymentMethodResult.Failure).failure

    private fun <T> PaymentMethodResult<T>.value(): T = (this as PaymentMethodResult.Success).value

    private fun HttpRequestData.json(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text) as JsonObject

    // --- Listado ---

    @Test
    fun listado_pideTodosLosMediosConArchivadosYEnviaBearer() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var include: String? = null
        var auth: String? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            include = it.url.parameters["includeDeprecated"]
            auth = it.headers[HttpHeaders.Authorization]
            respond("[]", HttpStatusCode.OK, jsonHeaders)
        }

        source.paymentMethods("f-1")

        assertEquals(HttpMethod.Get, method)
        assertEquals("/families/f-1/payment-methods", path)
        assertEquals("true", include)
        assertEquals("Bearer tok", auth)
    }

    @Test
    fun listado_conservaElOrdenYMapeaActivosYArchivados() = runTest {
        val body = "[" + listOf(
            methodJson("m-2", "Transferencia"),
            methodJson("m-1", "Efectivo"),
            methodJson("m-3", "Tarjeta vieja", "DEPRECATED"),
        ).joinToString(",") + "]"

        val result = source(reply(HttpStatusCode.OK, body)).paymentMethods("f-1")

        assertEquals(
            listOf(
                PaymentMethod("m-2", "Transferencia", PaymentMethodStatus.ACTIVE),
                PaymentMethod("m-1", "Efectivo", PaymentMethodStatus.ACTIVE),
                PaymentMethod("m-3", "Tarjeta vieja", PaymentMethodStatus.DEPRECATED),
            ),
            result.value(),
        )
    }

    @Test
    fun listadoVacio_esExito() = runTest {
        assertEquals(emptyList<PaymentMethod>(), source(reply(HttpStatusCode.OK, "[]")).paymentMethods("f-1").value())
    }

    @Test
    fun listado_estadoDesconocido_esInvalidData() = runTest {
        val body = "[" + methodJson("m-1", "Efectivo", "ARCHIVED") + "]"
        assertEquals(
            PaymentMethodFailure.InvalidData,
            source(reply(HttpStatusCode.OK, body)).paymentMethods("f-1").failure(),
        )
    }

    @Test
    fun listado_jsonIlegibleOFormaErronea_esInvalidData() = runTest {
        assertEquals(
            PaymentMethodFailure.InvalidData,
            source(reply(HttpStatusCode.OK, "no es json")).paymentMethods("f-1").failure(),
        )
        assertEquals(
            PaymentMethodFailure.InvalidData,
            source(reply(HttpStatusCode.OK, """{"id":"m-1"}""")).paymentMethods("f-1").failure(),
        )
    }

    // --- Alta y renombrado ---

    @Test
    fun alta_enviaElNombreYMapeaElMedioCreado() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body: JsonObject? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            body = it.json()
            respond(methodJson("m-9", "Tarjeta Santander"), HttpStatusCode.Created, jsonHeaders)
        }

        val result = source.createPaymentMethod("f-1", "Tarjeta Santander")

        assertEquals(HttpMethod.Post, method)
        assertEquals("/families/f-1/payment-methods", path)
        assertEquals(setOf("name"), body!!.keys)
        assertEquals(JsonPrimitive("Tarjeta Santander"), body!!["name"])
        assertEquals(PaymentMethod("m-9", "Tarjeta Santander", PaymentMethodStatus.ACTIVE), result.value())
    }

    @Test
    fun alta_conEstadoDesconocido_esInvalidData() = runTest {
        val source = source(reply(HttpStatusCode.Created, methodJson("m-9", "X", "OTRO")))
        assertEquals(PaymentMethodFailure.InvalidData, source.createPaymentMethod("f-1", "X").failure())
    }

    @Test
    fun alta_con200EnLugarDe201_noEsExito() = runTest {
        val source = source(reply(HttpStatusCode.OK, methodJson("m-9", "X")))
        assertEquals(PaymentMethodFailure.Unknown, source.createPaymentMethod("f-1", "X").failure())
    }

    @Test
    fun renombrar_enviaPatchConNameYAceptaElOk() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body: JsonObject? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            body = it.json()
            respond(methodJson("m-1", "Tarjeta Débito Santander"), HttpStatusCode.OK, jsonHeaders)
        }

        val result = source.renamePaymentMethod("f-1", "m-1", "Tarjeta Débito Santander")

        assertEquals(HttpMethod.Patch, method)
        assertEquals("/families/f-1/payment-methods/m-1", path)
        assertEquals(setOf("name"), body!!.keys)
        assertEquals(JsonPrimitive("Tarjeta Débito Santander"), body!!["name"])
        assertEquals(PaymentMethodResult.Success(Unit), result)
    }

    // --- Archivar y eliminar ---

    @Test
    fun archivar_enviaPostADeprecateYAcepta204() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            respond("", HttpStatusCode.NoContent)
        }

        val result = source.deprecatePaymentMethod("f-1", "m-1")

        assertEquals(HttpMethod.Post, method)
        assertEquals("/families/f-1/payment-methods/m-1/deprecate", path)
        assertEquals(PaymentMethodResult.Success(Unit), result)
    }

    @Test
    fun eliminar_enviaDeleteYAcepta204() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            respond("", HttpStatusCode.NoContent)
        }

        val result = source.deletePaymentMethod("f-1", "m-1")

        assertEquals(HttpMethod.Delete, method)
        assertEquals("/families/f-1/payment-methods/m-1", path)
        assertEquals(PaymentMethodResult.Success(Unit), result)
    }

    @Test
    fun identificadoresConCaracteresEspeciales_seCodificanEnLaRuta() = runTest {
        var path: String? = null
        val source = source {
            path = it.url.encodedPath
            respond("", HttpStatusCode.NoContent)
        }

        source.deletePaymentMethod("f/1", "m 1")

        assertEquals("/families/f%2F1/payment-methods/m%201", path)
    }

    // --- Errores ---

    @Test
    fun duplicado_conAmbosPrefijos() = runTest {
        for (code in listOf(
            "FINANCIAL_TRACKING.DUPLICATE_PAYMENT_METHOD_NAME",
            "FINANCIAL.DUPLICATE_PAYMENT_METHOD_NAME",
        )) {
            val source = source(reply(HttpStatusCode.Conflict, errorBody(code)))
            assertEquals(PaymentMethodFailure.DuplicateName, source.createPaymentMethod("f-1", "X").failure())
            assertEquals(
                PaymentMethodFailure.DuplicateName,
                source.renamePaymentMethod("f-1", "m-1", "X").failure(),
            )
        }
    }

    @Test
    fun nombreInvalido_conAmbosPrefijos() = runTest {
        for (code in listOf(
            "FINANCIAL_TRACKING.INVALID_PAYMENT_METHOD_NAME",
            "FINANCIAL.INVALID_PAYMENT_METHOD_NAME",
        )) {
            val source = source(reply(HttpStatusCode.BadRequest, errorBody(code)))
            assertEquals(PaymentMethodFailure.InvalidName, source.createPaymentMethod("f-1", "X").failure())
        }
    }

    @Test
    fun esPredeterminadoDeAlguien_alArchivarYAlEliminar_conAmbosPrefijos() = runTest {
        for (code in listOf(
            "FINANCIAL_TRACKING.PAYMENT_METHOD_IS_SOMEONES_DEFAULT",
            "FINANCIAL.PAYMENT_METHOD_IS_SOMEONES_DEFAULT",
        )) {
            val source = source(reply(HttpStatusCode.Conflict, errorBody(code)))
            assertEquals(
                PaymentMethodFailure.IsSomeonesDefault,
                source.deprecatePaymentMethod("f-1", "m-1").failure(),
            )
            assertEquals(
                PaymentMethodFailure.IsSomeonesDefault,
                source.deletePaymentMethod("f-1", "m-1").failure(),
            )
        }
    }

    @Test
    fun conMovimientosAsociados_alEliminar_conAmbosPrefijos() = runTest {
        for (code in listOf(
            "FINANCIAL_TRACKING.PAYMENT_METHOD_HAS_ASSOCIATED_ITEMS",
            "FINANCIAL.PAYMENT_METHOD_HAS_ASSOCIATED_ITEMS",
        )) {
            val source = source(reply(HttpStatusCode.Conflict, errorBody(code)))
            assertEquals(
                PaymentMethodFailure.HasAssociatedItems,
                source.deletePaymentMethod("f-1", "m-1").failure(),
            )
        }
    }

    @Test
    fun rolInsuficiente_conLosTresCodigos() = runTest {
        for (code in listOf(
            "FAMILY.INSUFFICIENT_ROLE",
            "FINANCIAL_TRACKING.INSUFFICIENT_ROLE",
            "FINANCIAL.INSUFFICIENT_ROLE",
        )) {
            val source = source(reply(HttpStatusCode.Forbidden, errorBody(code)))
            assertEquals(PaymentMethodFailure.InsufficientRole, source.paymentMethods("f-1").failure())
        }
    }

    @Test
    fun noEncontrado_conCualquierCodigoOCuerpo() = runTest {
        for (body in listOf(errorBody("FINANCIAL_TRACKING.PAYMENT_METHOD_NOT_FOUND"), errorBody("OTRO"), "")) {
            val source = source(reply(HttpStatusCode.NotFound, body))
            assertEquals(PaymentMethodFailure.NotFound, source.deletePaymentMethod("f-1", "m-1").failure())
            assertEquals(PaymentMethodFailure.NotFound, source.deprecatePaymentMethod("f-1", "m-1").failure())
        }
    }

    @Test
    fun noAutorizadoTrasLaRenovacion_esUnauthorized() = runTest {
        // El plugin de sesión no tiene renovación configurada en este test: el 401 llega tal cual.
        val source = source(reply(HttpStatusCode.Unauthorized, errorBody("AUTH.UNAUTHORIZED")))
        assertEquals(PaymentMethodFailure.Unauthorized, source.paymentMethods("f-1").failure())
    }

    @Test
    fun errorDelServidorOCodigoNoConocido_esUnknown() = runTest {
        assertEquals(
            PaymentMethodFailure.Unknown,
            source(reply(HttpStatusCode.InternalServerError, errorBody("X"))).paymentMethods("f-1").failure(),
        )
        assertEquals(
            PaymentMethodFailure.Unknown,
            source(reply(HttpStatusCode.BadRequest, errorBody("FINANCIAL_TRACKING.PAYMENT_METHOD_NOT_ACTIVE")))
                .paymentMethods("f-1").failure(),
        )
        assertEquals(
            PaymentMethodFailure.Unknown,
            source(reply(HttpStatusCode.BadGateway, "<html>no es json</html>")).paymentMethods("f-1").failure(),
        )
    }

    @Test
    fun fallosDeRed_incluidaLaEsperaExcesiva_sonNetwork() = runTest {
        assertEquals(
            PaymentMethodFailure.Network,
            source { throw IOException("sin red") }.paymentMethods("f-1").failure(),
        )
        assertEquals(
            PaymentMethodFailure.Network,
            source { throw HttpRequestTimeoutException("http://localhost", 1L) }
                .createPaymentMethod("f-1", "X").failure(),
        )
    }

    @Test
    fun cancelacion_seRelanzaEnTodasLasOperaciones() {
        val source = source { throw CancellationException("cancelada") }
        assertThrows(CancellationException::class.java) { runBlocking { source.paymentMethods("f-1") } }
        assertThrows(CancellationException::class.java) { runBlocking { source.createPaymentMethod("f-1", "X") } }
        assertThrows(CancellationException::class.java) {
            runBlocking { source.renamePaymentMethod("f-1", "m-1", "X") }
        }
        assertThrows(CancellationException::class.java) { runBlocking { source.deprecatePaymentMethod("f-1", "m-1") } }
        assertThrows(CancellationException::class.java) { runBlocking { source.deletePaymentMethod("f-1", "m-1") } }
    }
}
