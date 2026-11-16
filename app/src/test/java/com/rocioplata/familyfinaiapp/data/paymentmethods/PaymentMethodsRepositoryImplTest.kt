package com.rocioplata.familyfinaiapp.data.paymentmethods

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.session.Session
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class PaymentMethodsRepositoryImplTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_000_000_000_000L
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val remoteMethods = listOf(
        PaymentMethod("m-2", "Transferencia", PaymentMethodStatus.ACTIVE),
        PaymentMethod("m-1", "Efectivo", PaymentMethodStatus.ACTIVE),
        PaymentMethod("m-3", "Tarjeta vieja", PaymentMethodStatus.DEPRECATED),
    )
    private val remoteBody =
        """[{"id":"m-2","familyId":"f-1","name":"Transferencia","status":"ACTIVE"},""" +
            """{"id":"m-1","familyId":"f-1","name":"Efectivo","status":"ACTIVE"},""" +
            """{"id":"m-3","familyId":"f-1","name":"Tarjeta vieja","status":"DEPRECATED"}]"""

    private class Backend {
        val calls = AtomicInteger()
        val paths = mutableListOf<String>()
        var offline = false
        var status = HttpStatusCode.OK
        var body: String? = null
        var gate: CompletableDeferred<Unit>? = null

        /** Respuesta por petición para los cambios; si es nula se usan `status` y `body`. */
        var responder: ((HttpRequestData) -> Pair<HttpStatusCode, String>)? = null
        val requests = mutableListOf<Triple<HttpMethod, String, String?>>()
    }

    private class FakeConnectivity(var online: Boolean = true) : ConnectivityObserver {
        override val isOnline: Flow<Boolean> get() = flowOf(online)
    }

    private class FixedSession(var userId: String = "u-1", var failure: SessionFailure? = null) : SessionProvider {
        override suspend fun current(): Session {
            failure?.let { throw SessionUnavailableException(it) }
            return Session(userId, "tok")
        }
    }

    private class Env(
        val repository: PaymentMethodsRepositoryImpl,
        val local: PaymentMethodsLocalDataSource,
        val backend: Backend,
        val session: FixedSession,
        val connectivity: FakeConnectivity,
        var clock: Long,
    )

    private fun TestScope.env(): Env {
        val backend = Backend()
        val engine = MockEngine { request ->
            backend.calls.incrementAndGet()
            backend.paths += request.url.encodedPath
            backend.requests += Triple(request.method, request.url.encodedPath, (request.body as? TextContent)?.text)
            backend.gate?.await()
            if (backend.offline) throw IOException("sin red")
            val custom = backend.responder?.invoke(request)
            respond(custom?.second ?: backend.body ?: remoteBody, custom?.first ?: backend.status, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "pm.preferences_pb") }
        val local = PaymentMethodsLocalDataSource(store)
        val session = FixedSession()
        val connectivity = FakeConnectivity()
        lateinit var env: Env
        val repository = PaymentMethodsRepositoryImpl(
            PaymentMethodsRemoteDataSource(client, "http://x"), local, session, connectivity,
        ) { env.clock }
        env = Env(repository, local, backend, session, connectivity, now)
        return env
    }

    @Test
    fun sinCacheNiRefresco_noHayDatos() = runTest {
        val env = env()

        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())
    }

    @Test
    fun cacheVigenteDelUsuario_seMuestraSinLlamarAlBackendYEnSuOrden() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, remoteMethods)

        val data = env.repository.paymentMethods("f-1").first()

        assertEquals(PaymentMethodsData(remoteMethods, isStale = false), data)
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun cacheDeMasDe24Horas_seMuestraMarcadaComoDesactualizada() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 25.hours.inWholeMilliseconds, remoteMethods)

        assertEquals(
            PaymentMethodsData(remoteMethods, isStale = true),
            env.repository.paymentMethods("f-1").first(),
        )
    }

    @Test
    fun cacheDeOtroUsuario_seDescartaYSeBorra() = runTest {
        val env = env()
        env.local.save("f-1", "u-2", now, remoteMethods)
        env.local.save("f-2", "u-2", now, remoteMethods)

        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())

        assertNull(env.local.read("f-1"))
        assertNull(env.local.read("f-2"))
    }

    @Test
    fun cacheDe7DiasOMas_seDescartaSoloEnEsaFamilia() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 7.days.inWholeMilliseconds, remoteMethods)
        env.local.save("f-2", "u-1", now - 1.hours.inWholeMilliseconds, remoteMethods)

        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())

        assertNull(env.local.read("f-1"))
        assertEquals(PaymentMethodsData(remoteMethods, false), env.repository.paymentMethods("f-2").first())
    }

    @Test
    fun cacheJustoAntesDeLos7Dias_seConserva() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 7.days.inWholeMilliseconds + 1, remoteMethods)

        val data = env.repository.paymentMethods("f-1").first()

        assertEquals(remoteMethods, data.paymentMethods)
        assertTrue(data.isStale)
    }

    @Test
    fun refrescoCorrecto_pideLaFamiliaConArchivados_guardaYActualiza() = runTest {
        val env = env()

        val result = env.repository.refresh("f-1")

        assertEquals(PaymentMethodResult.Success(Unit), result)
        assertEquals(listOf("/families/f-1/payment-methods"), env.backend.paths)
        assertEquals(
            PaymentMethodsData(remoteMethods, isStale = false),
            env.repository.paymentMethods("f-1").first(),
        )
        val cached = env.local.read("f-1")
        assertEquals("u-1", cached?.userId)
        assertEquals(now, cached?.savedAtMillis)
        assertEquals(remoteMethods, cached?.value)
    }

    @Test
    fun refrescoCorrecto_quitaLaMarcaDeDesactualizada() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 30.hours.inWholeMilliseconds, emptyList())
        assertTrue(env.repository.paymentMethods("f-1").first().isStale)

        env.repository.refresh("f-1")

        assertFalse(env.repository.paymentMethods("f-1").first().isStale)
    }

    @Test
    fun refrescoFallido_conservaLaCacheYDevuelveElFallo() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 2.hours.inWholeMilliseconds, remoteMethods)
        env.backend.offline = true

        val result = env.repository.refresh("f-1")

        assertEquals(PaymentMethodResult.Failure(PaymentMethodFailure.Network), result)
        assertEquals(PaymentMethodsData(remoteMethods, false), env.repository.paymentMethods("f-1").first())
        assertEquals(now - 2.hours.inWholeMilliseconds, env.local.read("f-1")?.savedAtMillis)
    }

    @Test
    fun refrescoFallidoSinCache_sigueSinDatos() = runTest {
        val env = env()
        env.backend.status = HttpStatusCode.InternalServerError
        env.backend.body = """{"error":"X"}"""

        val result = env.repository.refresh("f-1")

        assertEquals(PaymentMethodResult.Failure(PaymentMethodFailure.Unknown), result)
        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    @Test
    fun refrescoCorrecto_reemplazaLosDatosAnteriores() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, emptyList())

        env.repository.refresh("f-1")

        assertEquals(remoteMethods, env.repository.paymentMethods("f-1").first().paymentMethods)
    }

    @Test
    fun datosPorFamilia_sinMezclarse() = runTest {
        val env = env()

        env.repository.refresh("f-1")

        assertEquals(remoteMethods, env.repository.paymentMethods("f-1").first().paymentMethods)
        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-2").first())
        assertNull(env.local.read("f-2"))
    }

    @Test
    fun sinSesion_elRefrescoFallaSinLlamarAlBackend() = runTest {
        val env = env()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(PaymentMethodResult.Failure(PaymentMethodFailure.Unauthorized), env.repository.refresh("f-1"))
        env.session.failure = SessionFailure.UNKNOWN
        assertEquals(PaymentMethodResult.Failure(PaymentMethodFailure.Unknown), env.repository.refresh("f-1"))
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun sinSesion_laLecturaNoMuestraNiLeeLaCache() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now, remoteMethods)
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())
    }

    @Test
    fun dosRefrescosSimultaneos_unaSolaLlamadaYMismoResultado() = runTest {
        val env = env()
        env.backend.gate = CompletableDeferred()

        val first = async { env.repository.refresh("f-1") }
        runCurrent()
        val second = async { env.repository.refresh("f-1") }
        runCurrent()
        env.backend.gate!!.complete(Unit)

        assertEquals(PaymentMethodResult.Success(Unit), first.await())
        assertEquals(PaymentMethodResult.Success(Unit), second.await())
        assertEquals(1, env.backend.calls.get())
    }

    @Test
    fun refrescosDeFamiliasDistintas_noSeBloqueanEntreSi() = runTest {
        val env = env()

        env.repository.refresh("f-1")
        env.repository.refresh("f-2")

        assertEquals(
            listOf("/families/f-1/payment-methods", "/families/f-2/payment-methods"),
            env.backend.paths,
        )
    }

    @Test
    fun refrescoSecuencial_siHaceNuevaLlamada() = runTest {
        val env = env()

        env.repository.refresh("f-1")
        env.repository.refresh("f-1")

        assertEquals(2, env.backend.calls.get())
    }

    @Test
    fun borrar_vaciaMemoriaYCache() = runTest {
        val env = env()
        env.repository.refresh("f-1")

        env.repository.clear()

        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    @Test
    fun borrarDuranteUnRefresco_noEsperaALaRedYDescartaLoObtenido() = runTest {
        val env = env()
        env.backend.gate = CompletableDeferred()
        val refresh = async { env.repository.refresh("f-1") }
        runCurrent()

        env.repository.clear()
        env.backend.gate!!.complete(Unit)
        refresh.await()

        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    // --- Operaciones de cambio (T-06) ---

    private val noContent = HttpStatusCode.NoContent to ""

    /** Entorno con los medios cargados del backend, sin llamadas ni peticiones registradas, una hora después. */
    private suspend fun TestScope.loaded(): Env {
        val env = env()
        env.repository.refresh("f-1")
        env.backend.calls.set(0)
        env.backend.requests.clear()
        env.clock = now + 1.hours.inWholeMilliseconds
        return env
    }

    private suspend fun Env.current() = repository.paymentMethods("f-1").first().paymentMethods
    private suspend fun Env.cached() = local.read("f-1")

    @Test
    fun crear_seAnadeAlFinalYSeGuarda_conservandoElSavedAtDeLaDescarga() = runTest {
        val env = loaded()
        env.backend.responder = {
            HttpStatusCode.Created to """{"id":"m-9","familyId":"f-1","name":"Tarjeta Santander","status":"ACTIVE"}"""
        }

        val result = env.repository.createPaymentMethod("f-1", "Tarjeta Santander")

        val created = PaymentMethod("m-9", "Tarjeta Santander", PaymentMethodStatus.ACTIVE)
        assertEquals(PaymentMethodResult.Success(created), result)
        assertEquals(remoteMethods + created, env.current())
        assertEquals(remoteMethods + created, env.cached()?.value)
        assertEquals(now, env.cached()?.savedAtMillis)
        assertEquals("u-1", env.cached()?.userId)
        assertEquals(listOf(HttpMethod.Post to "/families/f-1/payment-methods"), env.backend.requests.map { it.first to it.second })
    }

    @Test
    fun crear_duplicado_noCambiaNada() = runTest {
        val env = loaded()
        env.backend.responder = {
            HttpStatusCode.Conflict to """{"error":"FINANCIAL_TRACKING.DUPLICATE_PAYMENT_METHOD_NAME"}"""
        }

        val result = env.repository.createPaymentMethod("f-1", "Efectivo")

        assertEquals(PaymentMethodResult.Failure(PaymentMethodFailure.DuplicateName), result)
        assertEquals(remoteMethods, env.current())
        assertEquals(remoteMethods, env.cached()?.value)
    }

    @Test
    fun renombrar_cambiaElNombreSinAlterarElOrdenNiElEstado() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.OK to """{"id":"m-1","familyId":"f-1","name":"Dinero","status":"ACTIVE"}""" }

        val result = env.repository.renamePaymentMethod("f-1", "m-1", "Dinero")

        assertEquals(PaymentMethodResult.Success(Unit), result)
        val expected = listOf(
            remoteMethods[0],
            PaymentMethod("m-1", "Dinero", PaymentMethodStatus.ACTIVE),
            remoteMethods[2],
        )
        assertEquals(expected, env.current())
        assertEquals(expected, env.cached()?.value)
        assertEquals(now, env.cached()?.savedAtMillis)
        assertEquals(HttpMethod.Patch to "/families/f-1/payment-methods/m-1", env.backend.requests.single().let { it.first to it.second })
        assertEquals("""{"name":"Dinero"}""", env.backend.requests.single().third)
    }

    @Test
    fun archivar_pasaAArchivado_enMemoriaYCache() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }

        val result = env.repository.deprecatePaymentMethod("f-1", "m-2")

        assertEquals(PaymentMethodResult.Success(Unit), result)
        val expected = listOf(
            PaymentMethod("m-2", "Transferencia", PaymentMethodStatus.DEPRECATED),
            remoteMethods[1],
            remoteMethods[2],
        )
        assertEquals(expected, env.current())
        assertEquals(expected, env.cached()?.value)
        assertEquals(HttpMethod.Post to "/families/f-1/payment-methods/m-2/deprecate", env.backend.requests.single().let { it.first to it.second })
    }

    @Test
    fun eliminar_desapareceDeMemoriaYCache() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }

        val result = env.repository.deletePaymentMethod("f-1", "m-3")

        assertEquals(PaymentMethodResult.Success(Unit), result)
        assertEquals(remoteMethods.take(2), env.current())
        assertEquals(remoteMethods.take(2), env.cached()?.value)
        assertEquals(HttpMethod.Delete to "/families/f-1/payment-methods/m-3", env.backend.requests.single().let { it.first to it.second })
    }

    @Test
    fun fallosDelBackend_noAlteranNiMemoriaNiCache() = runTest {
        val cases = listOf(
            HttpStatusCode.Conflict to """{"error":"FINANCIAL_TRACKING.PAYMENT_METHOD_IS_SOMEONES_DEFAULT"}""" to PaymentMethodFailure.IsSomeonesDefault,
            HttpStatusCode.Conflict to """{"error":"FINANCIAL_TRACKING.PAYMENT_METHOD_HAS_ASSOCIATED_ITEMS"}""" to PaymentMethodFailure.HasAssociatedItems,
            HttpStatusCode.NotFound to """{"error":"X"}""" to PaymentMethodFailure.NotFound,
            HttpStatusCode.Forbidden to """{"error":"FAMILY.INSUFFICIENT_ROLE"}""" to PaymentMethodFailure.InsufficientRole,
            HttpStatusCode.InternalServerError to """{"error":"X"}""" to PaymentMethodFailure.Unknown,
        )
        // Un solo entorno: dos DataStore sobre el mismo archivo no pueden coexistir. Como ningún fallo
        // cambia nada, el estado es el mismo al empezar cada caso.
        val env = loaded()
        for ((response, failure) in cases) {
            env.backend.responder = { response }

            assertEquals(PaymentMethodResult.Failure(failure), env.repository.deletePaymentMethod("f-1", "m-1"))
            assertEquals(PaymentMethodResult.Failure(failure), env.repository.deprecatePaymentMethod("f-1", "m-1"))
            assertEquals(remoteMethods, env.current())
            assertEquals(remoteMethods, env.cached()?.value)
        }
    }

    @Test
    fun errorDeRedEnUnCambio_noAlteraNada() = runTest {
        val env = loaded()
        env.backend.offline = true

        val result = env.repository.renamePaymentMethod("f-1", "m-1", "Otro")

        assertEquals(PaymentMethodResult.Failure(PaymentMethodFailure.Network), result)
        assertEquals(remoteMethods, env.current())
        assertEquals(remoteMethods, env.cached()?.value)
    }

    @Test
    fun sinConexion_cadaOperacionDevuelveOfflineSinLlamarAlBackend() = runTest {
        val env = loaded()
        env.connectivity.online = false
        val offline = PaymentMethodResult.Failure(PaymentMethodFailure.Offline)

        assertEquals(offline, env.repository.createPaymentMethod("f-1", "X"))
        assertEquals(offline, env.repository.renamePaymentMethod("f-1", "m-1", "X"))
        assertEquals(offline, env.repository.deprecatePaymentMethod("f-1", "m-1"))
        assertEquals(offline, env.repository.deletePaymentMethod("f-1", "m-1"))

        assertEquals(0, env.backend.calls.get())
        assertEquals(remoteMethods, env.current())
    }

    @Test
    fun sinSesion_elCambioFallaSinLlamarAlBackend() = runTest {
        val env = loaded()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(
            PaymentMethodResult.Failure(PaymentMethodFailure.Unauthorized),
            env.repository.deletePaymentMethod("f-1", "m-1"),
        )
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun cambioSinDatosCargadosDeLaFamilia_devuelveElResultadoSinTocarLaCache() = runTest {
        val env = env()
        env.backend.responder = { noContent }

        val result = env.repository.deletePaymentMethod("f-1", "m-1")

        assertEquals(PaymentMethodResult.Success(Unit), result)
        assertNull(env.local.read("f-1"))
        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())
    }

    @Test
    fun cambiosEnUnaFamilia_noAfectanAOtra() = runTest {
        val env = loaded()
        env.repository.refresh("f-2")
        env.backend.responder = { noContent }

        env.repository.deletePaymentMethod("f-1", "m-1")

        assertEquals(remoteMethods.filterNot { it.id == "m-1" }, env.current())
        assertEquals(remoteMethods, env.repository.paymentMethods("f-2").first().paymentMethods)
        assertEquals(remoteMethods, env.local.read("f-2")?.value)
    }

    @Test
    fun borrarDuranteUnCambio_noGuardaNada() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }
        env.backend.gate = CompletableDeferred()
        val change = async { env.repository.deletePaymentMethod("f-1", "m-1") }
        runCurrent()

        env.repository.clear()
        env.backend.gate!!.complete(Unit)

        assertEquals(PaymentMethodResult.Success(Unit), change.await())
        assertEquals(PaymentMethodsData(), env.repository.paymentMethods("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    @Test
    fun unRefrescoPosterior_reemplazaLosCambiosAplicadosLocalmente() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }
        env.repository.deletePaymentMethod("f-1", "m-1")
        env.backend.responder = null

        env.repository.refresh("f-1")

        assertEquals(remoteMethods, env.current())
        assertEquals(env.clock, env.cached()?.savedAtMillis)
    }
}
