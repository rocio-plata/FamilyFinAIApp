package com.rocioplata.familyfinaiapp.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersLocalDataSource
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersRemoteDataSource
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Cableado real de sesión, plugin y repositorio de miembros. Equivalente de
 * [SessionEndDuringPaymentMethodsRefreshTest]: un refresco recibe 401, la renovación es rechazada y el cierre
 * de sesión debe completarse (y vaciar la caché) aunque ese refresco siga esperando.
 */
class SessionEndDuringMembersRefreshTest {
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
    fun renovacionRechazadaDuranteElRefresco_cierraLaSesionYVaciaLaCacheDeMiembros() = runTest {
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
        val local = FamilyMembersLocalDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "members.preferences_pb") },
        )
        val repository by lazy {
            FamilyMembersRepositoryImpl(FamilyMembersRemoteDataSource(apiClient, "http://localhost"), local, manager)
        }
        val store = FakeStore(StoredSession("u-1", "acc-1", "ref-1"))
        manager = SessionManager(
            store,
            AuthRemoteDataSource(publicClient, apiClient, "http://localhost"),
        ) { repository.clear() }
        // Caché vigente (reloj real): así, que desaparezca prueba el borrado y no la caducidad de 7 días.
        val member = FamilyMember("u-1", "Valentina", FamilyRole.OWNER)
        local.save("f-1", "u-1", System.currentTimeMillis(), listOf(member))
        manager.load()
        assertEquals(listOf(member), repository.members("f-1").first().members)

        // Con el interbloqueo, esta llamada no terminaba nunca. El límite usa tiempo real (ver el test de
        // cabecera): el MockEngine responde en otro hilo.
        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { repository.refresh("f-1") }
        }

        assertEquals(FamilyMembersResult.Failure(FamilyMembersFailure.Unauthorized), result)
        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), manager.state.value)
        assertNull(store.stored)
        assertNull(local.read("f-1"))
        assertEquals(FamilyMembersData(), repository.members("f-1").first())
    }
}
