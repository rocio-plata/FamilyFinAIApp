package com.rocioplata.familyfinaiapp.session

import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import java.io.IOException

class SessionManagerTest {
    private val rocio = StoredSession("u-1", "acc-1", "ref-1")
    private val ana = StoredSession("u-2", "acc-2", "ref-2")

    private class FakeStore(var stored: StoredSession? = null, var failRead: Boolean = false) : SessionStore {
        var clears = 0
        override suspend fun read(): StoredSession? {
            if (failRead) throw IOException("ilegible")
            return stored
        }

        override suspend fun save(session: StoredSession) {
            stored = session
        }

        override suspend fun clear() {
            clears++
            stored = null
        }
    }

    /** Backend simulado de `/auth/refresh`: responde [status] con [body] o falla sin red. */
    private class Backend {
        var status = HttpStatusCode.OK
        var body = """{"accessToken":"acc-nuevo","refreshToken":"ref-nuevo"}"""
        var offline = false
        val sentRefreshTokens = mutableListOf<String>()
        var logoutStatus = HttpStatusCode.NoContent
        val paths = mutableListOf<String>()
    }

    private class Env(val store: FakeStore, val backend: Backend) {
        var userDataCleared = 0
        lateinit var manager: SessionManager
    }

    private fun env(stored: StoredSession? = null, failRead: Boolean = false): Env {
        val env = Env(FakeStore(stored, failRead), Backend())
        val client = HttpClient(
            MockEngine { request ->
                delay(10)
                env.backend.paths += request.url.encodedPath
                if (env.backend.offline) throw IOException("sin red")
                if (request.url.encodedPath == "/auth/logout") return@MockEngine respond("", env.backend.logoutStatus)
                val sent = Json.parseToJsonElement(request.body.toByteArray().decodeToString())
                env.backend.sentRefreshTokens += sent.toString()
                respond(env.backend.body, env.backend.status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val remote = AuthRemoteDataSource(client, client, "http://localhost:3000")
        env.manager = SessionManager(env.store, remote) { env.userDataCleared++ }
        return env
    }

    private fun error(code: String) = """{"error":"$code"}"""

    @Test
    fun estadoInicial_esLoading() {
        assertEquals(SessionState.Loading, env().manager.state.value)
    }

    @Test
    fun load_conSesionGuardada_entraSinBorrarNada() = runTest {
        val env = env(stored = rocio)

        env.manager.load()

        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
        assertEquals(Session("u-1", "acc-1"), env.manager.current())
        assertEquals(0, env.userDataCleared)
    }

    @Test
    fun load_sinSesion_borraDatosDelUsuarioYMuestraAccesoSinMotivo() = runTest {
        val env = env()

        env.manager.load()

        assertEquals(SessionState.SignedOut(null), env.manager.state.value)
        assertEquals(1, env.userDataCleared)
    }

    @Test
    fun load_archivoIlegible_equivaleASinSesion() = runTest {
        val env = env(failRead = true)

        env.manager.load()

        assertEquals(SessionState.SignedOut(null), env.manager.state.value)
        assertEquals(1, env.store.clears)
        assertEquals(1, env.userDataCleared)
    }

    @Test
    fun load_segundaVez_noVuelveALeer() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.store.stored = ana

        env.manager.load()

        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
    }

    @Test
    fun start_guardaLaSesionYCambiaLaGeneracion() = runTest {
        val env = env()
        env.manager.load()

        env.manager.start(rocio)

        assertEquals(rocio, env.store.stored)
        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
        assertEquals(Session("u-1", "acc-1"), env.manager.current())
    }

    @Test
    fun cadaInicioDeSesion_tieneUnaGeneracionNueva() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.manager.endSession(SessionEndReason.LoggedOut)

        env.manager.start(rocio)

        assertEquals(SessionState.SignedIn("u-1", 2), env.manager.state.value)
    }

    @Test
    fun endSession_borraSesionYDatosYGuardaElMotivo() = runTest {
        val env = env(stored = rocio)
        env.manager.load()

        env.manager.endSession(SessionEndReason.Security)

        assertEquals(SessionState.SignedOut(SessionEndReason.Security), env.manager.state.value)
        assertNull(env.store.stored)
        assertEquals(1, env.userDataCleared)
        assertTrue(runCatching { env.manager.current() }.exceptionOrNull() is SessionUnavailableException)
    }

    @Test
    fun endSession_repetido_conservaElPrimerMotivoYNoVuelveABorrar() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.manager.endSession(SessionEndReason.Expired)

        env.manager.endSession(SessionEndReason.Security)

        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), env.manager.state.value)
        assertEquals(1, env.userDataCleared)
    }

    @Test
    fun current_sinSesion_lanzaNotSignedIn() = runTest {
        val env = env()
        env.manager.load()

        val error = runCatching { env.manager.current() }.exceptionOrNull()

        assertEquals(SessionFailure.NOT_SIGNED_IN, (error as SessionUnavailableException).failure)
    }

    @Test
    fun otroUsuario_trasCerrarSesion_entraConSusDatos() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.manager.endSession(SessionEndReason.LoggedOut)

        env.manager.start(ana)

        assertEquals(SessionState.SignedIn("u-2", 2), env.manager.state.value)
        assertEquals(Session("u-2", "acc-2"), env.manager.current())
    }

    @Test
    fun refreshAfter_exito_guardaElParNuevoYDevuelveElToken() = runTest {
        val env = env(stored = rocio)
        env.manager.load()

        val outcome = env.manager.refreshAfter("acc-1")

        assertEquals(RefreshOutcome.Refreshed("acc-nuevo"), outcome)
        assertEquals(StoredSession("u-1", "acc-nuevo", "ref-nuevo"), env.store.stored)
        assertEquals(Session("u-1", "acc-nuevo"), env.manager.current())
        assertEquals(listOf("""{"refreshToken":"ref-1"}"""), env.backend.sentRefreshTokens)
        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
    }

    @Test
    fun refreshAfter_peticionesConcurrentes_renuevanUnaSolaVez() = runTest {
        val env = env(stored = rocio)
        env.manager.load()

        val outcomes = (1..10).map { async { env.manager.refreshAfter("acc-1") } }.awaitAll()

        assertEquals(1, env.backend.sentRefreshTokens.size)
        assertTrue(outcomes.all { it == RefreshOutcome.Refreshed("acc-nuevo") })
        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
    }

    @Test
    fun refreshAfter_conTokenYaRenovado_noLlamaAlBackend() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.manager.refreshAfter("acc-1")

        val outcome = env.manager.refreshAfter("acc-1")

        assertEquals(RefreshOutcome.Refreshed("acc-nuevo"), outcome)
        assertEquals(1, env.backend.sentRefreshTokens.size)
    }

    @Test
    fun refreshAfter_tokenInvalido_cierraLaSesionPorExpiracion() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.backend.status = HttpStatusCode.Unauthorized
        env.backend.body = error("AUTH.INVALID_REFRESH_TOKEN")

        val outcome = env.manager.refreshAfter("acc-1")

        assertEquals(RefreshOutcome.SessionEnded, outcome)
        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), env.manager.state.value)
        assertNull(env.store.stored)
        assertEquals(1, env.userDataCleared)
    }

    @Test
    fun refreshAfter_posibleRobo_cierraLaSesionPorSeguridad() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.backend.status = HttpStatusCode.Unauthorized
        env.backend.body = error("AUTH.POSSIBLE_TOKEN_THEFT")

        val outcome = env.manager.refreshAfter("acc-1")

        assertEquals(RefreshOutcome.SessionEnded, outcome)
        assertEquals(SessionState.SignedOut(SessionEndReason.Security), env.manager.state.value)
        assertEquals(1, env.userDataCleared)
    }

    @Test
    fun refreshAfter_sinConexion_conservaLaSesion() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.backend.offline = true

        val outcome = env.manager.refreshAfter("acc-1")

        assertEquals(RefreshOutcome.Unavailable(offline = true), outcome)
        assertEquals(rocio, env.store.stored)
        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
        assertEquals(0, env.userDataCleared)
    }

    @Test
    fun refreshAfter_errorDelServidor_conservaLaSesion() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.backend.status = HttpStatusCode.InternalServerError
        env.backend.body = error("INTERNAL.UNEXPECTED_ERROR")

        val outcome = env.manager.refreshAfter("acc-1")

        assertEquals(RefreshOutcome.Unavailable(offline = false), outcome)
        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
    }

    @Test
    fun refreshAfter_sinSesion_noLlamaAlBackend() = runTest {
        val env = env()
        env.manager.load()

        assertEquals(RefreshOutcome.SessionEnded, env.manager.refreshAfter("acc-1"))
        assertTrue(env.backend.sentRefreshTokens.isEmpty())
    }

    @Test
    fun logout_204_cierraLaSesionYBorraLosDatos() = runTest {
        val env = env(stored = rocio)
        env.manager.load()

        val result = env.manager.logout()

        assertEquals(LogoutResult.Success, result)
        assertEquals(listOf("/auth/logout"), env.backend.paths)
        assertEquals(SessionState.SignedOut(SessionEndReason.LoggedOut), env.manager.state.value)
        assertNull(env.store.stored)
        assertEquals(1, env.userDataCleared)
    }

    @Test
    fun logout_sinConexion_conservaSesionYDatos() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.backend.offline = true

        val result = env.manager.logout()

        assertEquals(LogoutResult.NoConnection, result)
        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
        assertEquals(rocio, env.store.stored)
        assertEquals(0, env.userDataCleared)
    }

    @Test
    fun logout_errorDelServidor_conservaLaSesion() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.backend.logoutStatus = HttpStatusCode.InternalServerError

        assertEquals(LogoutResult.Unknown, env.manager.logout())
        assertEquals(SessionState.SignedIn("u-1", 1), env.manager.state.value)
        assertEquals(rocio, env.store.stored)
    }

    @Test
    fun logout_sesionYaInvalidaEnElBackend_cierraIgualEnElTelefono() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.backend.logoutStatus = HttpStatusCode.Unauthorized

        assertEquals(LogoutResult.Success, env.manager.logout())
        assertEquals(SessionState.SignedOut(SessionEndReason.LoggedOut), env.manager.state.value)
        assertNull(env.store.stored)
    }

    @Test
    fun logout_sesionYaCerrada_noLlamaAlBackendYConservaElMotivo() = runTest {
        val env = env(stored = rocio)
        env.manager.load()
        env.manager.endSession(SessionEndReason.Security)

        assertEquals(LogoutResult.Success, env.manager.logout())
        assertEquals(SessionState.SignedOut(SessionEndReason.Security), env.manager.state.value)
        assertTrue(env.backend.paths.isEmpty())
    }
}
