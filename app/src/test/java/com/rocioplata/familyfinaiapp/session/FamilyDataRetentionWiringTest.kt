package com.rocioplata.familyfinaiapp.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.categories.CategoriesLocalDataSource
import com.rocioplata.familyfinaiapp.data.categories.CategoriesRemoteDataSource
import com.rocioplata.familyfinaiapp.data.categories.CategoriesRepositoryImpl
import com.rocioplata.familyfinaiapp.data.header.HeaderLocalDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRemoteDataSource
import com.rocioplata.familyfinaiapp.data.header.HeaderRepositoryImpl
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersLocalDataSource
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersRemoteDataSource
import com.rocioplata.familyfinaiapp.data.members.FamilyMembersRepositoryImpl
import com.rocioplata.familyfinaiapp.data.movements.MovementsLocalDataSource
import com.rocioplata.familyfinaiapp.data.movements.MovementsRemoteDataSource
import com.rocioplata.familyfinaiapp.data.movements.MovementsRepositoryImpl
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsLocalDataSource
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsRemoteDataSource
import com.rocioplata.familyfinaiapp.data.paymentmethods.PaymentMethodsRepositoryImpl
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Cableado real de la cabecera con los cuatro repositorios que guardan datos por familia (RF-23 de la 009, CA-27): cuando
 * el backend deja de listar una familia, desaparece del dispositivo todo lo guardado de ella y lo de las demás se
 * conserva, y terminar la sesión después sigue vaciando todo sin bloquearse.
 */
class FamilyDataRetentionWiringTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val november = YearMonth.of(2026, 11)

    private class FakeStore(var stored: StoredSession?) : SessionStore {
        override suspend fun read() = stored
        override suspend fun save(session: StoredSession) {
            stored = session
        }

        override suspend fun clear() {
            stored = null
        }
    }

    private class Wiring(
        val manager: SessionManager,
        val header: HeaderRepositoryImpl,
        val categories: CategoriesLocalDataSource,
        val paymentMethods: PaymentMethodsLocalDataSource,
        val movements: MovementsLocalDataSource,
        val members: FamilyMembersLocalDataSource,
        val headerLocal: HeaderLocalDataSource,
    )

    private fun TestScope.wiring(familiesBody: String): Wiring {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/me/families" -> respond(familiesBody, HttpStatusCode.OK, jsonHeaders)
                "/me/invitations" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> respond("""{"displayName":"rocío"}""", HttpStatusCode.OK, jsonHeaders)
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
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "$name.preferences_pb")
        }
        val online = object : ConnectivityObserver {
            override val isOnline: Flow<Boolean> = flowOf(true)
        }
        val dates = MovementDates(Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC))
        val categoriesLocal = CategoriesLocalDataSource(store("categories"))
        val paymentMethodsLocal = PaymentMethodsLocalDataSource(store("payment_methods"))
        val movementsLocal = MovementsLocalDataSource(store("movements"))
        val membersLocal = FamilyMembersLocalDataSource(store("members"))
        val headerLocal = HeaderLocalDataSource(store("header"))
        // Los repositorios resuelven la sesión al usarse, igual que `AppContainer`.
        val categories by lazy {
            CategoriesRepositoryImpl(CategoriesRemoteDataSource(apiClient, "http://x"), categoriesLocal, manager, online)
        }
        val paymentMethods by lazy {
            PaymentMethodsRepositoryImpl(PaymentMethodsRemoteDataSource(apiClient, "http://x"), paymentMethodsLocal, manager, online)
        }
        val movements by lazy {
            MovementsRepositoryImpl(MovementsRemoteDataSource(apiClient, "http://x", dates), movementsLocal, manager, online, dates)
        }
        val members by lazy {
            FamilyMembersRepositoryImpl(FamilyMembersRemoteDataSource(apiClient, "http://x"), membersLocal, manager)
        }
        val header by lazy {
            HeaderRepositoryImpl(
                HeaderRemoteDataSource(apiClient, "http://x"), headerLocal, manager,
                onFamiliesKnown = { ids ->
                    categories.retainFamilies(ids)
                    paymentMethods.retainFamilies(ids)
                    movements.retainFamilies(ids)
                    members.retainFamilies(ids)
                },
            )
        }
        manager = SessionManager(
            FakeStore(StoredSession("u-1", "acc-1", "ref-1")),
            AuthRemoteDataSource(publicClient, apiClient, "http://x"),
        ) {
            header.clear()
            categories.clear()
            paymentMethods.clear()
            movements.clear()
            members.clear()
        }
        return Wiring(manager, header, categoriesLocal, paymentMethodsLocal, movementsLocal, membersLocal, headerLocal)
    }

    private suspend fun Wiring.seed(familyId: String) {
        val now = System.currentTimeMillis()
        categories.save(familyId, "u-1", now, emptyList())
        paymentMethods.save(familyId, "u-1", now, emptyList())
        movements.save(familyId, november, "u-1", now, emptyList())
        movements.saveLastUsedPaymentMethodId("u-1", familyId, "m-1")
        members.save(familyId, "u-1", now, emptyList())
    }

    @Test
    fun laListaSinUnaFamilia_borraLoSuyoDeLosCuatroRepositorios_yConservaLasDemas() = runTest {
        val wiring = wiring("""[{"familyId":"f-2","name":"Plata","role":"OWNER"}]""")
        wiring.manager.load()
        wiring.seed("f-1")
        wiring.seed("f-2")

        val result = wiring.header.refresh()

        assertEquals(null, result.families)
        assertNull(wiring.categories.read("f-1"))
        assertNull(wiring.paymentMethods.read("f-1"))
        assertNull(wiring.movements.read("f-1", november))
        assertNull(wiring.movements.lastUsedPaymentMethodId("u-1", "f-1"))
        assertNull(wiring.members.read("f-1"))
        assertNotNull(wiring.categories.read("f-2"))
        assertNotNull(wiring.paymentMethods.read("f-2"))
        assertNotNull(wiring.movements.read("f-2", november))
        assertEquals("m-1", wiring.movements.lastUsedPaymentMethodId("u-1", "f-2"))
        assertNotNull(wiring.members.read("f-2"))
    }

    @Test
    fun conTodasLasFamiliasVigentes_noSeBorraNada() = runTest {
        val wiring = wiring("""[{"familyId":"f-1","name":"A","role":"OWNER"},{"familyId":"f-2","name":"B","role":"MEMBER"}]""")
        wiring.manager.load()
        wiring.seed("f-1")
        wiring.seed("f-2")

        wiring.header.refresh()

        assertNotNull(wiring.categories.read("f-1"))
        assertNotNull(wiring.movements.read("f-2", november))
        assertNotNull(wiring.members.read("f-1"))
    }

    @Test
    fun terminarLaSesionDespuesDeOlvidarFamilias_vaciaTodoSinBloquearse() = runTest {
        val wiring = wiring("""[{"familyId":"f-2","name":"Plata","role":"OWNER"}]""")
        wiring.manager.load()
        wiring.seed("f-1")
        wiring.seed("f-2")
        wiring.header.refresh()

        // El límite usa tiempo real, como en SessionEndDuringHeaderRefreshTest: el MockEngine responde en otro hilo.
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { wiring.manager.endSession(SessionEndReason.LoggedOut) }
        }

        assertEquals(SessionState.SignedOut(SessionEndReason.LoggedOut), wiring.manager.state.value)
        assertNull(wiring.categories.read("f-2"))
        assertNull(wiring.paymentMethods.read("f-2"))
        assertNull(wiring.movements.read("f-2", november))
        assertNull(wiring.members.read("f-2"))
        assertNull(wiring.headerLocal.readFamilies())
    }
}
