package com.rocioplata.familyfinaiapp.data.members

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import com.rocioplata.familyfinaiapp.session.Session
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

class FamilyMembersRepositoryImplTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_000_000_000_000L
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val remoteMembers = listOf(
        FamilyMember("u-1", "Valentina Plata", FamilyRole.OWNER),
        FamilyMember("u-2", null, FamilyRole.MEMBER),
    )
    private val remoteBody =
        """[{"userId":"u-1","role":"OWNER","displayName":"Valentina Plata","email":"v@example.com"},""" +
            """{"userId":"u-2","role":"MEMBER"}]"""

    private class Backend {
        val calls = AtomicInteger()
        val paths = mutableListOf<String>()
        var offline = false
        var status = HttpStatusCode.OK
        var body: String? = null
        var gate: CompletableDeferred<Unit>? = null
    }

    private class FixedSession(var userId: String = "u-1", var failure: SessionFailure? = null) : SessionProvider {
        override suspend fun current(): Session {
            failure?.let { throw SessionUnavailableException(it) }
            return Session(userId, "tok")
        }
    }

    private class Env(
        val repository: FamilyMembersRepositoryImpl,
        val local: FamilyMembersLocalDataSource,
        val backend: Backend,
        val session: FixedSession,
        var clock: Long,
    )

    private fun TestScope.env(): Env {
        val backend = Backend()
        val engine = MockEngine { request ->
            backend.calls.incrementAndGet()
            backend.paths += request.url.encodedPath
            backend.gate?.await()
            if (backend.offline) throw IOException("sin red")
            respond(backend.body ?: remoteBody, backend.status, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "members.preferences_pb") }
        val local = FamilyMembersLocalDataSource(store)
        val session = FixedSession()
        lateinit var env: Env
        val repository = FamilyMembersRepositoryImpl(
            FamilyMembersRemoteDataSource(client, "http://x"), local, session,
        ) { env.clock }
        env = Env(repository, local, backend, session, now)
        return env
    }

    @Test
    fun sinCacheNiRefresco_noHayDatos() = runTest {
        assertEquals(FamilyMembersData(), env().repository.members("f-1").first())
    }

    @Test
    fun cacheVigenteDelUsuario_seMuestraSinLlamarAlBackend() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, remoteMembers)

        assertEquals(FamilyMembersData(remoteMembers, isStale = false), env.repository.members("f-1").first())
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun cacheDeMasDe24Horas_seMuestraMarcadaComoDesactualizada() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 25.hours.inWholeMilliseconds, remoteMembers)

        assertEquals(FamilyMembersData(remoteMembers, isStale = true), env.repository.members("f-1").first())
    }

    @Test
    fun cacheDeOtroUsuario_seDescartaYSeBorra() = runTest {
        val env = env()
        env.local.save("f-1", "u-2", now, remoteMembers)
        env.local.save("f-2", "u-2", now, remoteMembers)

        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())

        assertNull(env.local.read("f-1"))
        assertNull(env.local.read("f-2"))
    }

    @Test
    fun cacheDe7DiasOMas_seDescartaSoloEnEsaFamilia() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 7.days.inWholeMilliseconds, remoteMembers)
        env.local.save("f-2", "u-1", now - 1.hours.inWholeMilliseconds, remoteMembers)

        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())

        assertNull(env.local.read("f-1"))
        assertEquals(FamilyMembersData(remoteMembers, false), env.repository.members("f-2").first())
    }

    @Test
    fun cacheJustoAntesDeLos7Dias_seConserva() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 7.days.inWholeMilliseconds + 1, remoteMembers)

        val data = env.repository.members("f-1").first()

        assertEquals(remoteMembers, data.members)
        assertTrue(data.isStale)
    }

    @Test
    fun refrescoCorrecto_pideLaFamiliaGuardaYActualiza() = runTest {
        val env = env()

        val result = env.repository.refresh("f-1")

        assertEquals(FamilyMembersResult.Success(Unit), result)
        assertEquals(listOf("/families/f-1/members"), env.backend.paths)
        assertEquals(FamilyMembersData(remoteMembers, isStale = false), env.repository.members("f-1").first())
        val cached = env.local.read("f-1")
        assertEquals("u-1", cached?.userId)
        assertEquals(now, cached?.savedAtMillis)
        assertEquals(remoteMembers, cached?.value)
    }

    @Test
    fun refrescoCorrecto_quitaLaMarcaDeDesactualizada() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 30.hours.inWholeMilliseconds, emptyList())
        assertTrue(env.repository.members("f-1").first().isStale)

        env.repository.refresh("f-1")

        assertFalse(env.repository.members("f-1").first().isStale)
    }

    @Test
    fun refrescoCorrecto_actualizaElFlujoQueYaEstabaObservado() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, emptyList())
        val seen = mutableListOf<List<FamilyMember>?>()
        val job = backgroundScope.launch {
            env.repository.members("f-1").collect { seen += it.members }
        }
        runCurrent()

        env.repository.refresh("f-1")
        runCurrent()

        assertEquals(listOf<List<FamilyMember>?>(emptyList(), remoteMembers), seen)
        job.cancel()
    }

    @Test
    fun refrescoFallido_conservaLaCacheYDevuelveElFallo() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 2.hours.inWholeMilliseconds, remoteMembers)
        env.backend.offline = true

        val result = env.repository.refresh("f-1")

        assertEquals(FamilyMembersResult.Failure(FamilyMembersFailure.Network), result)
        assertEquals(FamilyMembersData(remoteMembers, false), env.repository.members("f-1").first())
        assertEquals(now - 2.hours.inWholeMilliseconds, env.local.read("f-1")?.savedAtMillis)
    }

    @Test
    fun refrescoFallidoSinCache_sigueSinDatos() = runTest {
        val env = env()
        env.backend.status = HttpStatusCode.Forbidden
        env.backend.body = """{"error":"FAMILY.NOT_A_MEMBER"}"""

        val result = env.repository.refresh("f-1")

        assertEquals(FamilyMembersResult.Failure(FamilyMembersFailure.NotAllowed), result)
        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    @Test
    fun refrescoCorrecto_reemplazaLosDatosAnteriores() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, listOf(FamilyMember("u-9", "Antiguo", FamilyRole.MEMBER)))

        env.repository.refresh("f-1")

        assertEquals(remoteMembers, env.repository.members("f-1").first().members)
    }

    @Test
    fun datosPorFamilia_sinMezclarse() = runTest {
        val env = env()

        env.repository.refresh("f-1")

        assertEquals(remoteMembers, env.repository.members("f-1").first().members)
        assertEquals(FamilyMembersData(), env.repository.members("f-2").first())
        assertNull(env.local.read("f-2"))
    }

    @Test
    fun sinSesion_elRefrescoFallaSinLlamarAlBackend() = runTest {
        val env = env()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(FamilyMembersResult.Failure(FamilyMembersFailure.Unauthorized), env.repository.refresh("f-1"))
        env.session.failure = SessionFailure.UNKNOWN
        assertEquals(FamilyMembersResult.Failure(FamilyMembersFailure.Unknown), env.repository.refresh("f-1"))
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun sinSesion_laLecturaNoMuestraNiLeeLaCache() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now, remoteMembers)
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())
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

        assertEquals(FamilyMembersResult.Success(Unit), first.await())
        assertEquals(FamilyMembersResult.Success(Unit), second.await())
        assertEquals(1, env.backend.calls.get())
    }

    @Test
    fun refrescosDeFamiliasDistintas_noSeBloqueanEntreSi() = runTest {
        val env = env()

        env.repository.refresh("f-1")
        env.repository.refresh("f-2")

        assertEquals(listOf("/families/f-1/members", "/families/f-2/members"), env.backend.paths)
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

        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())
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

        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    @Test
    fun elCorreoNuncaLlegaALaCache() = runTest {
        val env = env()

        env.repository.refresh("f-1")

        val cached = env.local.read("f-1")?.value.orEmpty()
        assertEquals(false, cached.any { it.toString().contains("@") })
    }

    @Test
    fun conservarFamilias_borraLaPerdidaDeMemoriaYDisco_yConservaLasDemas() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, remoteMembers)
        env.local.save("f-2", "u-1", now - 1.hours.inWholeMilliseconds, remoteMembers)
        env.repository.members("f-1").first()
        val kept = env.repository.members("f-2").first()

        env.repository.retainFamilies(setOf("f-2"))

        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())
        assertEquals(kept, env.repository.members("f-2").first())
        assertNull(env.local.read("f-1"))
        assertNotNull(env.local.read("f-2"))
    }

    @Test
    fun conservarFamilias_conjuntoVacio_borraTodasLasFamilias() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, remoteMembers)
        env.repository.members("f-1").first()

        env.repository.retainFamilies(emptySet())

        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    @Test
    fun conservarFamilias_conTodasVigentes_noCambiaNada() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, remoteMembers)
        val before = env.repository.members("f-1").first()

        env.repository.retainFamilies(setOf("f-1", "f-9"))

        assertEquals(before, env.repository.members("f-1").first())
        assertNotNull(env.local.read("f-1"))
    }

    @Test
    fun conservarFamilias_unaDescargaEnCursoDeLaPerdida_noSeGuarda_yNoEsperaALaRed() = runTest {
        val env = env()
        env.backend.gate = CompletableDeferred()
        val refresh = async { env.repository.refresh("f-1") }
        runCurrent()

        env.repository.retainFamilies(setOf("f-2"))
        assertFalse(refresh.isCompleted)
        env.backend.gate!!.complete(Unit)
        refresh.await()

        assertEquals(FamilyMembersData(), env.repository.members("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    @Test
    fun conservarFamilias_unaDescargaEnCursoDeUnaVigente_sigueGuardandose() = runTest {
        val env = env()
        env.backend.gate = CompletableDeferred()
        val refresh = async { env.repository.refresh("f-2") }
        runCurrent()

        env.repository.retainFamilies(setOf("f-2"))
        env.backend.gate!!.complete(Unit)
        refresh.await()

        assertNotNull(env.local.read("f-2"))
    }

    @Test
    fun conservarFamilias_unaFamiliaRecuperada_vuelveAGuardarse() = runTest {
        val env = env()
        env.repository.retainFamilies(emptySet())
        env.repository.refresh("f-1")
        assertNull(env.local.read("f-1"))

        env.repository.retainFamilies(setOf("f-1"))
        env.repository.refresh("f-1")

        assertNotNull(env.local.read("f-1"))
    }

    @Test
    fun conservarFamilias_clear_olvidaElConjuntoYVuelveAGuardarTodo() = runTest {
        val env = env()
        env.repository.retainFamilies(setOf("f-2"))
        env.repository.clear()

        env.repository.refresh("f-1")

        assertNotNull(env.local.read("f-1"))
    }
}
