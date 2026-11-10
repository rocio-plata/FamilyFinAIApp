package com.rocioplata.familyfinaiapp.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderLocalDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRemoteDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Cableado real de sesión, plugin y repositorio de cabecera. Regresión del interbloqueo visto en la
 * prueba manual de 003-auth (CA-10): un refresco de cabecera recibe 401, la renovación es rechazada
 * y el cierre de sesión debe completarse aunque ese refresco siga esperando la respuesta.
 */
class SessionEndDuringHeaderRefreshTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private class FakeStore(var stored: StoredSession?) : SessionStore {
        override suspend fun read() = stored
        override suspend fun save(session: StoredSession) {
            stored = session
        }

        override suspend fun clear() {
            stored = null
        }
    }

    @Test
    fun renovacionRechazadaDuranteElRefresco_cierraLaSesionYVaciaLaCache() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/auth/refresh" ->
                    respond("""{"error":"AUTH.INVALID_REFRESH_TOKEN"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                else -> respond("""{"error":"AUTH.INVALID_TOKEN"}""", HttpStatusCode.Unauthorized, jsonHeaders)
            }
        }
        val json = Json { ignoreUnknownKeys = true }
        val publicClient = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        lateinit var manager: SessionManager
        val apiClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
            install(SessionAuth) {
                accessToken = { manager.accessToken() }
                refresh = { manager.refreshAfter(it) }
            }
        }
        val local = HeaderLocalDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "c.preferences_pb") },
        )
        val repository by lazy {
            HeaderRepositoryImpl(HeaderRemoteDataSource(apiClient, "http://localhost"), local, manager)
        }
        val store = FakeStore(StoredSession("u-1", "acc-1", "ref-1"))
        manager = SessionManager(
            store,
            AuthRemoteDataSource(publicClient, apiClient, "http://localhost"),
        ) { repository.clear() }
        local.saveFamilies("u-1", 1L, listOf(Family("f-1", "Plata", FamilyRole.OWNER)))
        manager.load()

        // Con el interbloqueo, esta llamada no terminaba nunca.
        val result = withTimeout(5_000) { repository.refresh() }

        assertEquals(HeaderFailure.Unauthorized, result.families)
        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), manager.state.value)
        assertNull(store.stored)
        assertNull(local.readFamilies())
    }
}
