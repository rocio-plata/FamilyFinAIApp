package com.rocioplata.familyfinaiapp.data.network

import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.session.LogoutResult
import com.rocioplata.familyfinaiapp.session.RefreshOutcome
import com.rocioplata.familyfinaiapp.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAuthPluginTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    /** Responde 200 solo con el token "acc-nuevo"; registra el Authorization de cada petición. */
    private class Api {
        val authorizations = mutableListOf<String?>()
        var refreshes = mutableListOf<String>()
    }

    private fun client(
        api: Api,
        token: () -> String?,
        refresh: suspend (String) -> RefreshOutcome,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            if (auth == "Bearer acc-nuevo") respond("ok", HttpStatusCode.OK) else respond("", HttpStatusCode.Unauthorized)
        },
    ) = HttpClient(MockEngine { request -> api.authorizations += request.headers[HttpHeaders.Authorization]; handler(request) }) {
        install(SessionAuth) {
            accessToken = token
            this.refresh = { used -> api.refreshes += used; refresh(used) }
        }
    }

    @Test
    fun conSesion_anadeElBearer() = runTest {
        val api = Api()
        val client = client(api, { "acc-nuevo" }, { error("no debe renovar") })

        val response = client.get("http://localhost/me/profile")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf<String?>("Bearer acc-nuevo"), api.authorizations)
        assertTrue(api.refreshes.isEmpty())
    }

    @Test
    fun sinSesion_noAnadeBearerNiRenueva() = runTest {
        val api = Api()
        val client = client(api, { null }, { error("no debe renovar") })

        val response = client.get("http://localhost/me/profile")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(listOf<String?>(null), api.authorizations)
        assertTrue(api.refreshes.isEmpty())
    }

    @Test
    fun con401_renuevaYReintentaConElTokenNuevo() = runTest {
        val api = Api()
        val client = client(api, { "acc-viejo" }, { RefreshOutcome.Refreshed("acc-nuevo") })

        val response = client.get("http://localhost/me/profile")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("acc-viejo"), api.refreshes)
        assertEquals(listOf<String?>("Bearer acc-viejo", "Bearer acc-nuevo"), api.authorizations)
    }

    @Test
    fun reintentoCon401_noRenuevaOtraVez() = runTest {
        val api = Api()
        val client = client(api, { "acc-viejo" }, { RefreshOutcome.Refreshed("acc-tambien-invalido") })

        val response = client.get("http://localhost/me/profile")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, api.refreshes.size)
        assertEquals(2, api.authorizations.size)
    }

    @Test
    fun renovacionRechazada_devuelveEl401OriginalSinReintentar() = runTest {
        val api = Api()
        val client = client(api, { "acc-viejo" }, { RefreshOutcome.SessionEnded })

        val response = client.get("http://localhost/me/profile")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, api.authorizations.size)
    }

    @Test
    fun renovacionSinConexion_fallaComoErrorDeRed() = runTest {
        val api = Api()
        val client = client(api, { "acc-viejo" }, { RefreshOutcome.Unavailable(offline = true) })

        val error = runCatching { client.get("http://localhost/me/profile") }.exceptionOrNull()

        assertTrue(error is SessionRefreshOfflineException)
        assertTrue(error is java.io.IOException)
    }

    @Test
    fun renovacionConErrorDelServidor_fallaSinSerErrorDeRed() = runTest {
        val api = Api()
        val client = client(api, { "acc-viejo" }, { RefreshOutcome.Unavailable(offline = false) })

        val error = runCatching { client.get("http://localhost/me/profile") }.exceptionOrNull()

        assertTrue(error is SessionRefreshFailedException)
        assertTrue(error !is java.io.IOException)
    }

    // Integración con SessionManager real: el ciclo cliente ↔ SessionManager se resuelve con funciones.
    private class FakeStore(var stored: StoredSession?) : SessionStore {
        override suspend fun read() = stored
        override suspend fun save(session: StoredSession) {
            stored = session
        }

        override suspend fun clear() {
            stored = null
        }
    }

    private class Backend {
        var refreshCalls = 0
        var refreshStatus = HttpStatusCode.OK
        var refreshBody = """{"accessToken":"acc-nuevo","refreshToken":"ref-nuevo"}"""
        var logoutAuthorizations = mutableListOf<String?>()
        var logoutNeedsRenewedToken = false
    }

    private class Wired(val manager: SessionManager, val api: HttpClient, val store: FakeStore)

    private fun wired(backend: Backend): Wired {
        val engine = MockEngine { request ->
            delay(5)
            when (request.url.encodedPath) {
                "/auth/refresh" -> {
                    backend.refreshCalls++
                    respond(backend.refreshBody, backend.refreshStatus, jsonHeaders)
                }
                "/auth/logout" -> {
                    val auth = request.headers[HttpHeaders.Authorization]
                    backend.logoutAuthorizations += auth
                    if (backend.logoutNeedsRenewedToken && auth != "Bearer acc-nuevo") {
                        respond("", HttpStatusCode.Unauthorized)
                    } else {
                        respond("", HttpStatusCode.NoContent)
                    }
                }
                else ->
                    if (request.headers[HttpHeaders.Authorization] == "Bearer acc-nuevo") {
                        respond("""{"displayName":"rocío"}""", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("""{"error":"AUTH.INVALID_TOKEN"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                    }
            }
        }
        val publicClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        lateinit var manager: SessionManager
        val apiClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(SessionAuth) {
                accessToken = { manager.accessToken() }
                refresh = { manager.refreshAfter(it) }
            }
        }
        val store = FakeStore(StoredSession("u-1", "acc-1", "ref-1"))
        manager = SessionManager(store, AuthRemoteDataSource(publicClient, apiClient, "http://localhost")) {}
        return Wired(manager, apiClient, store)
    }

    @Test
    fun integracion_peticionesConcurrentesConAccesoCaducado_unaSolaRenovacion() = runTest {
        val backend = Backend()
        val env = wired(backend)
        env.manager.load()

        val statuses = (1..10).map { async { env.api.get("http://localhost/me/profile").status } }.awaitAll()

        assertTrue(statuses.all { it == HttpStatusCode.OK })
        assertEquals(1, backend.refreshCalls)
        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
        assertEquals("acc-nuevo", env.store.stored?.accessToken)
    }

    @Test
    fun integracion_renovacionRechazada_cierraLaSesionYDevuelve401() = runTest {
        val backend = Backend().apply {
            refreshStatus = HttpStatusCode.Unauthorized
            refreshBody = """{"error":"AUTH.INVALID_REFRESH_TOKEN"}"""
        }
        val env = wired(backend)
        env.manager.load()

        val status = env.api.get("http://localhost/me/profile").status

        assertEquals(HttpStatusCode.Unauthorized, status)
        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), env.manager.state.value)
        assertNull(env.store.stored)
    }

    @Test
    fun integracion_logout_enviaElBearerDeLaSesion() = runTest {
        val backend = Backend()
        val env = wired(backend)
        env.manager.load()

        env.manager.logout()

        assertEquals(listOf<String?>("Bearer acc-1"), backend.logoutAuthorizations)
        assertEquals(SessionState.SignedOut(SessionEndReason.LoggedOut), env.manager.state.value)
    }

    @Test
    fun integracion_logoutConAccesoCaducado_renuevaYCierraSinBloquearse() = runTest {
        val backend = Backend().apply { logoutNeedsRenewedToken = true }
        val env = wired(backend)
        env.manager.load()

        val result = env.manager.logout()

        assertEquals(LogoutResult.Success, result)
        assertEquals(1, backend.refreshCalls)
        assertEquals(listOf<String?>("Bearer acc-1", "Bearer acc-nuevo"), backend.logoutAuthorizations)
        assertEquals(SessionState.SignedOut(SessionEndReason.LoggedOut), env.manager.state.value)
    }
}
