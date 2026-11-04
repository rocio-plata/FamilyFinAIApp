package com.rocioplata.familyfinaiapp.data.auth

import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.AuthFailure
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class AuthRemoteDataSourceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val sessionJson =
        """{"userId":"u-1","email":"a@b.cl","displayName":"Rocío","accessToken":"acc","refreshToken":"ref"}"""

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }

    private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): AuthRemoteDataSource {
        val client = client(handler)
        return AuthRemoteDataSource(client, client, "http://localhost:3000/")
    }

    /** Clientes separados para comprobar cuál usa cada endpoint. */
    private class Calls {
        var publicCalls = 0
        var apiCalls = 0
    }

    private fun splitSource(
        calls: Calls,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = AuthRemoteDataSource(
        publicClient = client { calls.publicCalls++; handler(it) },
        apiClient = client { calls.apiCalls++; handler(it) },
        baseUrl = "http://localhost:3000",
    )

    private fun reply(status: HttpStatusCode, body: String): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(body, status, jsonHeaders) }

    private fun error(code: String) = """{"error":"$code","message":"texto del backend"}"""

    @Test
    fun login_exito_devuelveSesionYEnviaCredenciales() = runTest {
        var request: HttpRequestData? = null
        var sentBody: String? = null
        val source = source {
            request = it
            sentBody = it.body.toByteArray().decodeToString()
            respond(sessionJson, HttpStatusCode.OK, jsonHeaders)
        }

        val result = source.login("a@b.cl", " clave con espacios ")

        assertEquals(AuthCallResult.Success(StoredSession("u-1", "acc", "ref")), result)
        assertEquals(HttpMethod.Post, request?.method)
        assertEquals("/auth/login", request?.url?.encodedPath)
        assertNull(request?.headers?.get(HttpHeaders.Authorization))
        val body = Json.parseToJsonElement(sentBody!!).jsonObject
        assertEquals("a@b.cl", body["email"]?.jsonPrimitive?.content)
        assertEquals(" clave con espacios ", body["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun login_credencialesInvalidas_devuelveInvalidCredentials() = runTest {
        val source = source(reply(HttpStatusCode.Unauthorized, error("IDENTITY.INVALID_CREDENTIALS")))

        assertEquals(AuthCallResult.Failure(AuthFailure.InvalidCredentials), source.login("a@b.cl", "x"))
    }

    @Test
    fun login_401SinCodigoConocido_devuelveUnknown() = runTest {
        val source = source(reply(HttpStatusCode.Unauthorized, "no es json"))

        assertEquals(AuthCallResult.Failure(AuthFailure.Unknown), source.login("a@b.cl", "x"))
    }

    @Test
    fun login_errorDelServidor_devuelveUnknown() = runTest {
        val source = source(reply(HttpStatusCode.InternalServerError, error("INTERNAL.UNEXPECTED_ERROR")))

        assertEquals(AuthCallResult.Failure(AuthFailure.Unknown), source.login("a@b.cl", "x"))
    }

    @Test
    fun login_sinConexion_devuelveNetwork() = runTest {
        val source = source { throw IOException("sin red") }

        assertEquals(AuthCallResult.Failure(AuthFailure.Network), source.login("a@b.cl", "x"))
    }

    @Test
    fun login_esperaExcesiva_devuelveUnknown() = runTest {
        val source = source { throw HttpRequestTimeoutException("http://localhost:3000/auth/login", 15_000) }

        assertEquals(AuthCallResult.Failure(AuthFailure.Unknown), source.login("a@b.cl", "x"))
    }

    @Test
    fun login_respuestaExitosaIncompleta_devuelveUnknown() = runTest {
        val source = source(reply(HttpStatusCode.OK, """{"userId":"u-1"}"""))

        assertEquals(AuthCallResult.Failure(AuthFailure.Unknown), source.login("a@b.cl", "x"))
    }

    @Test
    fun registro_exito_devuelveSesionYEnviaLosTresCampos() = runTest {
        var path: String? = null
        var sentBody: String? = null
        val source = source {
            path = it.url.encodedPath
            sentBody = it.body.toByteArray().decodeToString()
            respond(sessionJson.dropLast(1) + ""","defaultFamilyId":"f-1"}""", HttpStatusCode.Created, jsonHeaders)
        }

        val result = source.register("Rocío", "a@b.cl", "secreto123")

        assertEquals(AuthCallResult.Success(StoredSession("u-1", "acc", "ref")), result)
        assertEquals("/auth/register", path)
        val body = Json.parseToJsonElement(sentBody!!).jsonObject
        assertEquals("Rocío", body["displayName"]?.jsonPrimitive?.content)
        assertEquals("a@b.cl", body["email"]?.jsonPrimitive?.content)
        assertEquals("secreto123", body["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun registro_emailYaRegistrado_devuelveRechazoNeutro() = runTest {
        val source = source(reply(HttpStatusCode.Conflict, error("IDENTITY.EMAIL_ALREADY_REGISTERED")))

        assertEquals(
            AuthCallResult.Failure(AuthFailure.RegistrationRejected),
            source.register("Rocío", "a@b.cl", "secreto123"),
        )
    }

    @Test
    fun registro_validacionesDelBackend_devuelvenRechazoNeutro() = runTest {
        listOf("IDENTITY.WEAK_PASSWORD", "IDENTITY.INVALID_DISPLAY_NAME", "SHARED.INVALID_EMAIL").forEach { code ->
            val source = source(reply(HttpStatusCode.BadRequest, error(code)))

            assertEquals(code, AuthCallResult.Failure(AuthFailure.RegistrationRejected), source.register("R", "a@b.cl", "x"))
        }
    }

    @Test
    fun registro_codigoDesconocido_devuelveUnknown() = runTest {
        val source = source(reply(HttpStatusCode.BadRequest, error("HTTP.INVALID_REQUEST_BODY")))

        assertEquals(AuthCallResult.Failure(AuthFailure.Unknown), source.register("R", "a@b.cl", "secreto123"))
    }

    @Test
    fun registro_200EnLugarDe201_devuelveUnknown() = runTest {
        val source = source(reply(HttpStatusCode.OK, sessionJson))

        assertEquals(AuthCallResult.Failure(AuthFailure.Unknown), source.register("R", "a@b.cl", "secreto123"))
    }

    @Test
    fun registro_sinConexion_devuelveNetwork() = runTest {
        val source = source { throw IOException("sin red") }

        assertEquals(AuthCallResult.Failure(AuthFailure.Network), source.register("R", "a@b.cl", "secreto123"))
    }

    @Test
    fun refresh_exito_devuelveElNuevoParPorElClientePublico() = runTest {
        val calls = Calls()
        var request: HttpRequestData? = null
        var sentBody: String? = null
        val source = splitSource(calls) {
            request = it
            sentBody = it.body.toByteArray().decodeToString()
            respond("""{"accessToken":"acc-2","refreshToken":"ref-2"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val result = source.refresh("ref-1")

        assertEquals(RefreshCallResult.Success("acc-2", "ref-2"), result)
        assertEquals(1, calls.publicCalls)
        assertEquals(0, calls.apiCalls)
        assertEquals("/auth/refresh", request?.url?.encodedPath)
        assertNull(request?.headers?.get(HttpHeaders.Authorization))
        assertEquals("ref-1", Json.parseToJsonElement(sentBody!!).jsonObject["refreshToken"]?.jsonPrimitive?.content)
    }

    @Test
    fun refresh_posibleRobo_rechazaPorSeguridad() = runTest {
        val source = source(reply(HttpStatusCode.Unauthorized, error("AUTH.POSSIBLE_TOKEN_THEFT")))

        assertEquals(RefreshCallResult.Rejected(SessionEndReason.Security), source.refresh("ref"))
    }

    @Test
    fun refresh_tokenInvalidoOSinCodigo_rechazaPorExpiracion() = runTest {
        listOf(error("AUTH.INVALID_REFRESH_TOKEN"), "no es json").forEach { body ->
            val source = source(reply(HttpStatusCode.Unauthorized, body))

            assertEquals(body, RefreshCallResult.Rejected(SessionEndReason.Expired), source.refresh("ref"))
        }
    }

    @Test
    fun refresh_sinConexion_devuelveNetwork() = runTest {
        val source = source { throw IOException("sin red") }

        assertEquals(RefreshCallResult.Network, source.refresh("ref"))
    }

    @Test
    fun refresh_errorDelServidorEsperaORespuestaIncompleta_devuelveUnknown() = runTest {
        assertEquals(RefreshCallResult.Unknown, source(reply(HttpStatusCode.InternalServerError, "")).refresh("ref"))
        assertEquals(RefreshCallResult.Unknown, source(reply(HttpStatusCode.OK, """{"accessToken":"a"}""")).refresh("ref"))
        val timeout = source { throw HttpRequestTimeoutException("http://localhost:3000/auth/refresh", 15_000) }
        assertEquals(RefreshCallResult.Unknown, timeout.refresh("ref"))
    }

    @Test
    fun refresh_resultado_noExponeTokensEnToString() {
        val text = RefreshCallResult.Success("acc-secreto", "ref-secreto").toString()

        assertEquals(false, text.contains("acc-secreto") || text.contains("ref-secreto"))
    }

    @Test
    fun logout_204_esExitoYUsaElClienteAutenticado() = runTest {
        val calls = Calls()
        var request: HttpRequestData? = null
        val source = splitSource(calls) {
            request = it
            respond("", HttpStatusCode.NoContent)
        }

        assertEquals(LogoutCallResult.Success, source.logout())
        assertEquals(0, calls.publicCalls)
        assertEquals(1, calls.apiCalls)
        assertEquals(HttpMethod.Post, request?.method)
        assertEquals("/auth/logout", request?.url?.encodedPath)
    }

    @Test
    fun logout_sinConexion_devuelveNoConnection() = runTest {
        val source = source { throw IOException("sin red") }

        assertEquals(LogoutCallResult.NoConnection, source.logout())
    }

    @Test
    fun logout_401_devuelveUnauthorized() = runTest {
        val source = source(reply(HttpStatusCode.Unauthorized, error("AUTH.INVALID_TOKEN")))

        assertEquals(LogoutCallResult.Unauthorized, source.logout())
    }

    @Test
    fun logout_errorDelServidorOEspera_devuelveUnknown() = runTest {
        assertEquals(LogoutCallResult.Unknown, source(reply(HttpStatusCode.InternalServerError, "")).logout())
        val timeout = source { throw HttpRequestTimeoutException("http://localhost:3000/auth/logout", 15_000) }
        assertEquals(LogoutCallResult.Unknown, timeout.logout())
    }
}
