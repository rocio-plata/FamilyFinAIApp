package com.rocioplata.familyfinaiapp.data.header

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class HeaderRepositoryImplTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_000_000_000_000L
    private val plata = Family("f-1", "Plata", FamilyRole.OWNER)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    /** Rutas que fallan por red; el resto responde con datos válidos. */
    private class Backend(var offline: Set<String> = emptySet(), var gate: CompletableDeferred<Unit>? = null)

    private class FixedSession(var userId: String = "u-1", var failure: SessionFailure? = null) : SessionProvider {
        override suspend fun current(): Session {
            failure?.let { throw SessionUnavailableException(it) }
            return Session(userId, "tok")
        }
    }

    private class Env(
        val known: MutableList<Set<String>>,
        var knownGate: CompletableDeferred<Unit>?,
        val repository: HeaderRepositoryImpl,
        val local: HeaderLocalDataSource,
        val backend: Backend,
        val session: FixedSession,
        var clock: Long,
    )

    private fun TestScope.env(): Env {
        val backend = Backend()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            backend.gate?.await()
            if (path in backend.offline) throw IOException("sin red")
            val body = when (path) {
                "/me/families" -> """[{"familyId":"f-1","name":"Plata","role":"OWNER"}]"""
                "/me/invitations" -> """[{"id":"a"},{"id":"b"}]"""
                else -> """{"displayName":"rocío"}"""
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "c.preferences_pb") }
        val local = HeaderLocalDataSource(store)
        val session = FixedSession()
        lateinit var env: Env
        val known = mutableListOf<Set<String>>()
        val repository = HeaderRepositoryImpl(
            HeaderRemoteDataSource(client, "http://x"), local, session,
            onFamiliesKnown = {
                known += it
                env.knownGate?.await()
            },
        ) { env.clock }
        env = Env(known, null, repository, local, backend, session, now)
        return env
    }

    private suspend fun Env.seed(ageMillis: Long, userId: String = "u-1") {
        val savedAt = clock - ageMillis
        local.saveFamilies(userId, savedAt, listOf(plata))
        local.savePendingInvitations(userId, savedAt, 7)
        local.saveDisplayName(userId, savedAt, "guardado")
    }

    private val allPaths = setOf("/me/families", "/me/invitations", "/me/profile")

    @Test
    fun conRed_sinCache_cargaLosTresRecursosYLosGuarda() = runTest {
        val env = env()

        val result = env.repository.refresh()

        val data = env.repository.header.first()
        assertTrue(result.isSuccessful)
        assertEquals(listOf(plata), data.families?.value)
        assertEquals(2, data.pendingInvitations?.value)
        assertEquals("rocío", data.displayName?.value)
        assertFalse(data.isStale)
        assertEquals(listOf(plata), env.local.readFamilies()?.value)
    }

    @Test
    fun cacheDe1Hora_sinRed_semuestraSinAlerta() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        env.backend.offline = allPaths

        val result = env.repository.refresh()

        val data = env.repository.header.first()
        assertEquals(HeaderFailure.Network, result.families)
        assertEquals("guardado", data.displayName?.value)
        assertEquals(7, data.pendingInvitations?.value)
        assertFalse(data.isStale)
    }

    @Test
    fun cacheDe30Horas_sinRed_semuestraConAlerta() = runTest {
        val env = env()
        env.seed(30.hours.inWholeMilliseconds)
        env.backend.offline = allPaths

        env.repository.refresh()

        val data = env.repository.header.first()
        assertEquals(listOf(plata), data.families?.value)
        assertTrue(data.isStale)
    }

    @Test
    fun cacheDe30Horas_unRefrescoCorrectoQuitaLaAlerta() = runTest {
        val env = env()
        env.seed(30.hours.inWholeMilliseconds)

        env.repository.refresh()

        assertFalse(env.repository.header.first().isStale)
    }

    @Test
    fun cacheDe8Dias_sinRed_seEliminaYNoSeMuestra() = runTest {
        val env = env()
        env.seed(8.days.inWholeMilliseconds)
        env.backend.offline = allPaths

        val result = env.repository.refresh()

        val data = env.repository.header.first()
        assertNull(data.families)
        assertNull(data.displayName)
        assertEquals(HeaderFailure.Network, result.families)
        assertNull(env.local.readFamilies())
        assertNull(env.local.readDisplayName())
    }

    @Test
    fun cacheDeOtroUsuario_seEliminaYNuncaSeMuestra() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds, userId = "otro")
        env.backend.offline = allPaths

        env.repository.refresh()

        assertNull(env.repository.header.first().families)
        assertNull(env.local.readDisplayName())
    }

    @Test
    fun cacheDeOtroUsuario_conRed_quedaSoloLoDelUsuarioActual() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds, userId = "otro")

        env.repository.refresh()

        assertEquals("rocío", env.repository.header.first().displayName?.value)
        assertEquals("u-1", env.local.readDisplayName()?.userId)
    }

    @Test
    fun sinRedYSinCache_devuelveErrorYNoHayDatos() = runTest {
        val env = env()
        env.backend.offline = allPaths

        val result = env.repository.refresh()

        assertFalse(result.isSuccessful)
        assertNull(env.repository.header.first().families)
    }

    @Test
    fun fallaSoloInvitaciones_lasFamiliasSeCargan_yElContadorNoSeMuestra() = runTest {
        val env = env()
        env.backend.offline = setOf("/me/invitations")

        val result = env.repository.refresh()

        val data = env.repository.header.first()
        assertNull(result.families)
        assertEquals(HeaderFailure.Network, result.pendingInvitations)
        assertEquals(listOf(plata), data.families?.value)
        assertNull(data.pendingInvitations)
    }

    @Test
    fun fallaSoloInvitacionesConCache_conservaElContadorGuardado() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        env.backend.offline = setOf("/me/invitations")

        env.repository.refresh()

        assertEquals(7, env.repository.header.first().pendingInvitations?.value)
    }

    @Test
    fun sesionNoDisponible_informaElFalloYNoTocaLaCache() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        val result = env.repository.refresh()

        assertEquals(HeaderFailure.Unauthorized, result.families)
        assertEquals(listOf(plata), env.local.readFamilies()?.value)
    }

    private val depto = Family("f-2", "Depto", FamilyRole.MEMBER, "CLP", memberCount = 2)

    @Test
    fun addFamily_laAnadeAlFinal_enMemoriaYCache_conservandoElSavedAt() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        env.backend.offline = allPaths
        env.repository.refresh()

        env.repository.addFamily(depto)

        val families = env.repository.header.first().families
        val savedAt = env.clock - 1.hours.inWholeMilliseconds
        assertEquals(listOf(plata, depto), families?.value)
        assertEquals(savedAt, families?.savedAtMillis)
        assertEquals(Cached("u-1", savedAt, listOf(plata, depto)), env.local.readFamilies())
    }

    @Test
    fun addFamily_conLosDatosAntiguos_siguenMarcadosComoDesactualizados() = runTest {
        val env = env()
        env.seed(30.hours.inWholeMilliseconds)
        env.backend.offline = allPaths
        env.repository.refresh()

        env.repository.addFamily(depto)

        assertTrue(env.repository.header.first().isStale)
    }

    @Test
    fun addFamily_deUnaQueYaEsta_laSustituyeSinDuplicarla() = runTest {
        val env = env()
        env.repository.refresh()
        val renamed = plata.copy(currency = "USD", memberCount = 5)

        env.repository.addFamily(renamed)

        assertEquals(listOf(renamed), env.repository.header.first().families?.value)
        assertEquals(listOf(renamed), env.local.readFamilies()?.value)
    }

    @Test
    fun removeFamily_laQuitaDeMemoriaYCache_yLasDemasSeConservan() = runTest {
        val env = env()
        env.repository.refresh()
        env.repository.addFamily(depto)

        env.repository.removeFamily("f-1")

        assertEquals(listOf(depto), env.repository.header.first().families?.value)
        assertEquals(listOf(depto), env.local.readFamilies()?.value)
    }

    @Test
    fun setFamilyOrder_ordena_ignoraDesconocidasYDejaAlFinalLasNoMencionadas() = runTest {
        val env = env()
        env.repository.refresh()
        val tercera = Family("f-3", "Tercera", FamilyRole.OWNER)
        env.repository.addFamily(depto)
        env.repository.addFamily(tercera)

        env.repository.setFamilyOrder(listOf("f-3", "no-existe", "f-1", "f-3"))

        val expected = listOf(tercera, plata, depto)
        assertEquals(expected, env.repository.header.first().families?.value)
        assertEquals(expected, env.local.readFamilies()?.value)
    }

    @Test
    fun updateFamily_cambiaRolMonedaYMiembros_soloDeEsaFamilia() = runTest {
        val env = env()
        env.repository.refresh()
        env.repository.addFamily(depto)

        env.repository.updateFamily("f-2") { it.copy(role = FamilyRole.OWNER, currency = "USD", memberCount = 3) }
        env.repository.updateFamily("no-existe") { it.copy(currency = "EUR") }

        val expected = listOf(plata, Family("f-2", "Depto", FamilyRole.OWNER, "USD", 3))
        assertEquals(expected, env.repository.header.first().families?.value)
        assertEquals(expected, env.local.readFamilies()?.value)
    }

    @Test
    fun cambiosDeFamilias_sinListaCargada_noHacenNada() = runTest {
        val env = env()

        env.repository.addFamily(depto)
        env.repository.removeFamily("f-1")
        env.repository.setFamilyOrder(listOf("f-1"))
        env.repository.updateFamily("f-1") { it.copy(currency = "USD") }

        assertNull(env.repository.header.first().families)
        assertNull(env.local.readFamilies())
    }

    @Test
    fun setPendingInvitations_loFijaEnMemoriaYCache_tambienSinListaDeFamilias() = runTest {
        val env = env()

        env.repository.setPendingInvitations(4)

        assertEquals(4, env.repository.header.first().pendingInvitations?.value)
        assertEquals(4, env.local.readPendingInvitations()?.value)
        assertEquals("u-1", env.local.readPendingInvitations()?.userId)
        assertNull(env.repository.header.first().families)
    }

    @Test
    fun cambiosLocales_sinSesion_noHacenNada() = runTest {
        val env = env()
        env.repository.refresh()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        env.repository.addFamily(depto)
        env.repository.setPendingInvitations(9)

        assertEquals(listOf(plata), env.repository.header.first().families?.value)
        assertEquals(2, env.repository.header.first().pendingInvitations?.value)
        assertEquals(listOf(plata), env.local.readFamilies()?.value)
    }

    @Test
    fun cambiosLocales_despuesDeClear_noHacenNada() = runTest {
        val env = env()
        env.repository.refresh()
        env.repository.clear()

        env.repository.addFamily(depto)

        assertNull(env.repository.header.first().families)
        assertNull(env.local.readFamilies())
    }

    @Test
    fun unRefrescoEnCurso_noDeshaceUnaFamiliaAnadida_peroSiActualizaLosDemasRecursos() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        val gate = CompletableDeferred<Unit>()
        env.backend.gate = gate
        val refresh = async { env.repository.refresh() }
        runCurrent()

        env.repository.addFamily(depto)
        gate.complete(Unit)
        val result = refresh.await()

        val data = env.repository.header.first()
        assertTrue(result.isSuccessful)
        assertEquals(listOf(plata, depto), data.families?.value)
        assertEquals(listOf(plata, depto), env.local.readFamilies()?.value)
        assertEquals(2, data.pendingInvitations?.value)
        assertEquals("rocío", data.displayName?.value)
    }

    @Test
    fun unRefrescoEnCurso_noDeshaceUnaBaja() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        val gate = CompletableDeferred<Unit>()
        env.backend.gate = gate
        val refresh = async { env.repository.refresh() }
        runCurrent()

        env.repository.removeFamily("f-1")
        gate.complete(Unit)
        refresh.await()

        assertEquals(emptyList<Family>(), env.repository.header.first().families?.value)
        assertEquals(emptyList<Family>(), env.local.readFamilies()?.value)
    }

    @Test
    fun unRefrescoEnCurso_noDeshaceElContador_peroSiActualizaLasFamilias() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        val gate = CompletableDeferred<Unit>()
        env.backend.gate = gate
        val refresh = async { env.repository.refresh() }
        runCurrent()

        env.repository.setPendingInvitations(9)
        gate.complete(Unit)
        refresh.await()

        val data = env.repository.header.first()
        assertEquals(9, data.pendingInvitations?.value)
        assertEquals(9, env.local.readPendingInvitations()?.value)
        assertEquals(listOf(plata), data.families?.value)
        assertEquals(env.clock, data.families?.savedAtMillis)
    }

    @Test
    fun unRefrescoQueEmpiezaDespuesDeUnCambio_siTraeLosDatosDelBackend() = runTest {
        val env = env()
        env.repository.refresh()
        env.repository.addFamily(depto)

        env.repository.refresh()

        // El backend de la prueba solo conoce f-1: un refresco posterior al cambio manda.
        assertEquals(listOf(plata), env.repository.header.first().families?.value)
    }

    @Test
    fun alAvisar_unRefrescoCorrectoEntregaLasFamiliasDelBackend() = runTest {
        val env = env()

        env.repository.refresh()

        assertEquals(listOf(setOf("f-1")), env.known)
    }

    @Test
    fun alAvisar_cadaCambioLocalDeFamiliasEntregaLaListaResultante() = runTest {
        val env = env()
        env.repository.refresh()
        env.known.clear()

        env.repository.addFamily(depto)
        env.repository.updateFamily("f-2") { it.copy(currency = "USD") }
        env.repository.setFamilyOrder(listOf("f-2", "f-1"))
        env.repository.removeFamily("f-1")

        assertEquals(listOf(setOf("f-1", "f-2"), setOf("f-1", "f-2"), setOf("f-1", "f-2"), setOf("f-2")), env.known)
    }

    @Test
    fun alAvisar_nadaCuandoNoHayListaCargadaNiSesion() = runTest {
        val env = env()

        env.repository.addFamily(depto)
        env.repository.refresh()
        env.known.clear()
        env.session.failure = SessionFailure.NOT_SIGNED_IN
        env.repository.addFamily(depto)

        assertEquals(emptyList<Set<String>>(), env.known)
    }

    @Test
    fun alAvisar_nadaConUnFalloDeRedNiConLaCacheNiConElContador() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        env.backend.offline = allPaths

        env.repository.refresh()
        env.repository.setPendingInvitations(3)

        assertEquals(emptyList<Set<String>>(), env.known)
    }

    @Test
    fun alAvisar_nadaSiSoloFallanLasFamilias_peroSiCuandoFallaOtroRecurso() = runTest {
        val env = env()
        env.backend.offline = setOf("/me/families")
        env.repository.refresh()
        assertEquals(emptyList<Set<String>>(), env.known)

        env.backend.offline = setOf("/me/invitations")
        env.repository.refresh()
        assertEquals(listOf(setOf("f-1")), env.known)
    }

    @Test
    fun alAvisar_nadaSiLaListaSeDescartaPorUnCambioLocalDuranteElRefresco() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        val gate = CompletableDeferred<Unit>()
        env.backend.gate = gate
        val refresh = async { env.repository.refresh() }
        runCurrent()
        env.repository.addFamily(depto)
        env.known.clear()

        gate.complete(Unit)
        refresh.await()

        assertEquals(emptyList<Set<String>>(), env.known)
    }

    @Test
    fun alAvisar_nadaSiSeBorraDuranteElRefrescoNiAlHacerClear() = runTest {
        val env = env()
        val gate = CompletableDeferred<Unit>()
        env.backend.gate = gate
        val refresh = async { env.repository.refresh() }
        runCurrent()
        env.repository.clear()

        gate.complete(Unit)
        refresh.await()

        assertEquals(emptyList<Set<String>>(), env.known)
    }

    @Test
    fun alAvisar_elCallbackSePuedeSuspenderSinBloquearLosCambiosDeLaCabecera() = runTest {
        val env = env()
        env.repository.refresh()
        val release = CompletableDeferred<Unit>()
        env.knownGate = release
        val add = async { env.repository.addFamily(depto) }
        runCurrent()

        // El aviso sigue esperando, pero el candado de la cabecera ya se soltó: otro cambio no se bloquea.
        env.repository.setPendingInvitations(1)
        assertFalse(add.isCompleted)
        assertEquals(1, env.repository.header.first().pendingInvitations?.value)
        release.complete(Unit)
        add.await()
    }

    @Test
    fun clear_vaciaLaCacheYLosDatosEnMemoria() = runTest {
        val env = env()
        env.repository.refresh()

        env.repository.clear()

        val data = env.repository.header.first()
        assertNull(data.families)
        assertNull(data.pendingInvitations)
        assertNull(data.displayName)
        assertNull(env.local.readFamilies())
        assertNull(env.local.readPendingInvitations())
        assertNull(env.local.readDisplayName())
    }

    @Test
    fun clear_duranteUnRefresco_noQuedanDatosGuardadosAlTerminar() = runTest {
        val env = env()
        val gate = CompletableDeferred<Unit>()
        env.backend.gate = gate
        val refresh = async { env.repository.refresh() }
        runCurrent()

        val clear = async { env.repository.clear() }
        runCurrent()
        gate.complete(Unit)
        refresh.await()
        clear.await()

        assertNull(env.repository.header.first().families)
        assertNull(env.local.readFamilies())
        assertNull(env.local.readDisplayName())
    }

    @Test
    fun clear_duranteUnRefresco_noEsperaALaRed() = runTest {
        val env = env()
        val gate = CompletableDeferred<Unit>()
        env.backend.gate = gate
        val refresh = async { env.repository.refresh() }
        runCurrent()

        val clear = async { env.repository.clear() }
        runCurrent()

        // El borrado termina con el refresco todavía esperando la respuesta del backend.
        assertTrue(clear.isCompleted)
        assertFalse(refresh.isCompleted)
        gate.complete(Unit)
        refresh.await()
    }
}
