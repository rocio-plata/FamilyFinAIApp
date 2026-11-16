package com.rocioplata.familyfinaiapp.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.movements.MovementsLocalDataSource
import com.rocioplata.familyfinaiapp.data.movements.MovementsRemoteDataSource
import com.rocioplata.familyfinaiapp.data.movements.MovementsRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementsData
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Cableado real de sesión, plugin y repositorio de movimientos. Equivalente de
 * [SessionEndDuringPaymentMethodsRefreshTest]: un refresco recibe 401, la renovación es rechazada y el cierre
 * de sesión debe completarse (y vaciar la caché) aunque ese refresco siga esperando.
 */
class SessionEndDuringMovementsRefreshTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val month = YearMonth.of(2026, 11)
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
    fun renovacionRechazadaDuranteElRefresco_cierraLaSesionYVaciaLaCacheDeMovimientos() = runTest {
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
        val local = MovementsLocalDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "movements.preferences_pb") },
        )
        val dates = MovementDates()
        val connectivity = object : ConnectivityObserver {
            override val isOnline: Flow<Boolean> = flowOf(true)
        }
        val repository by lazy {
            MovementsRepositoryImpl(
                MovementsRemoteDataSource(apiClient, "http://localhost", dates), local, manager, connectivity, dates,
            )
        }
        val store = FakeStore(StoredSession("u-1", "acc-1", "ref-1"))
        manager = SessionManager(
            store,
            AuthRemoteDataSource(publicClient, apiClient, "http://localhost"),
        ) { repository.clear() }
        // Caché vigente (reloj real): así, que desaparezca prueba el borrado y no la caducidad de 7 días.
        val movement = Movement(
            id = "i-1",
            type = MovementType.EXPENSE,
            amount = Money(1000, "CLP"),
            categoryId = "c-1",
            tagId = null,
            paymentMethodId = "m-1",
            title = "Súper",
            note = null,
            date = LocalDate.of(2026, 11, 10),
            recordedBy = "u-1",
            createdAt = Instant.parse("2026-11-10T15:00:00Z"),
        )
        local.save("f-1", month, "u-1", System.currentTimeMillis(), listOf(movement))
        manager.load()
        assertEquals(listOf(movement), repository.movements("f-1", month).first().movements)

        // Con el interbloqueo, esta llamada no terminaba nunca. El límite usa tiempo real (ver el test de
        // cabecera): el MockEngine responde en otro hilo.
        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { repository.refresh("f-1", month) }
        }

        assertEquals(MovementResult.Failure(MovementFailure.Unauthorized), result)
        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), manager.state.value)
        assertNull(store.stored)
        assertNull(local.read("f-1", month))
        assertEquals(MovementsData(), repository.movements("f-1", month).first())
    }
}
