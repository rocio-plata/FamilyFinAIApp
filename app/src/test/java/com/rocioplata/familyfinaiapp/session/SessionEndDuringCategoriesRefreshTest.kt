package com.rocioplata.familyfinaiapp.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.categories.CategoriesLocalDataSource
import com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSource
import com.rocioplata.familyfinaiapp.data.categories.CategoriesRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
 * Cableado real de sesión, plugin y repositorio de categorías. Equivalente de
 * [SessionEndDuringHeaderRefreshTest]: un refresco recibe 401, la renovación es rechazada y el
 * cierre de sesión debe completarse (y vaciar la caché) aunque ese refresco siga esperando.
 */
class SessionEndDuringCategoriesRefreshTest {
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

    private object Online : ConnectivityObserver {
        override val isOnline: Flow<Boolean> = flowOf(true)
    }

    @Test
    fun renovacionRechazadaDuranteElRefresco_cierraLaSesionYVaciaLaCacheDeCategorias() = runTest {
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
        val local = CategoriesLocalDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "c.preferences_pb") },
        )
        val repository by lazy {
            CategoriesRepositoryImpl(CategoriesRemoteDataSource(apiClient, "http://localhost"), local, manager, Online)
        }
        val store = FakeStore(StoredSession("u-1", "acc-1", "ref-1"))
        manager = SessionManager(
            store,
            AuthRemoteDataSource(publicClient, apiClient, "http://localhost"),
        ) { repository.clear() }
        // Caché vigente (reloj real): así, que desaparezca prueba el borrado y no la caducidad de 7 días.
        val category = Category("c-1", CategoryType.EXPENSE, "Comida", null, CategoryStatus.ACTIVE, emptyList())
        local.save("f-1", "u-1", System.currentTimeMillis(), listOf(category))
        manager.load()
        assertEquals(listOf(category), repository.categories("f-1").first().categories)

        // Con el interbloqueo, esta llamada no terminaba nunca. El límite usa tiempo real (ver el test de
        // cabecera): el MockEngine responde en otro hilo.
        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { repository.refresh("f-1") }
        }

        assertEquals(CategoryResult.Failure(CategoryFailure.Unauthorized), result)
        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), manager.state.value)
        assertNull(store.stored)
        assertNull(local.read("f-1"))
        assertEquals(CategoriesData(), repository.categories("f-1").first())
    }
}
