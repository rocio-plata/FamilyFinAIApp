package com.rocioplata.familyfinaiapp.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsLocalDataSource
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsRemoteDataSource
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
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
 * Cableado real de sesión, plugin y repositorio de medios de pago. Equivalente de
 * [SessionEndDuringHeaderRefreshTest]: un refresco recibe 401, la renovación es rechazada y el
 * cierre de sesión debe completarse (y vaciar la caché) aunque ese refresco siga esperando.
 */
class SessionEndDuringPaymentMethodsRefreshTest {
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
    fun renovacionRechazadaDuranteElRefresco_cierraLaSesionYVaciaLaCacheDeMediosDePago() = runTest {
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
        val local = PaymentMethodsLocalDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "pm.preferences_pb") },
        )
        val repository by lazy {
            PaymentMethodsRepositoryImpl(PaymentMethodsRemoteDataSource(apiClient, "http://localhost"), local, manager, Online)
        }
        val store = FakeStore(StoredSession("u-1", "acc-1", "ref-1"))
        manager = SessionManager(
            store,
            AuthRemoteDataSource(publicClient, apiClient, "http://localhost"),
        ) { repository.clear() }
        // Caché vigente (reloj real): así, que desaparezca prueba el borrado y no la caducidad de 7 días.
        val method = PaymentMethod("m-1", "Efectivo", PaymentMethodStatus.ACTIVE)
        local.save("f-1", "u-1", System.currentTimeMillis(), listOf(method))
        manager.load()
        assertEquals(listOf(method), repository.paymentMethods("f-1").first().paymentMethods)

        // Con el interbloqueo, esta llamada no terminaba nunca. El límite usa tiempo real (ver el test de
        // cabecera): el MockEngine responde en otro hilo.
        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { repository.refresh("f-1") }
        }

        assertEquals(PaymentMethodResult.Failure(PaymentMethodFailure.Unauthorized), result)
        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), manager.state.value)
        assertNull(store.stored)
        assertNull(local.read("f-1"))
        assertEquals(PaymentMethodsData(), repository.paymentMethods("f-1").first())
    }
}
