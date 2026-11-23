package com.rocioplata.familyfinaiapp.data.movements

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.CategoryChange
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementChanges
import com.rocioplata.familyfinaiapp.domain.movements.MovementEditResult
import com.rocioplata.familyfinaiapp.domain.movements.NewMovement
import com.rocioplata.familyfinaiapp.domain.movements.NoteUpdate
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementsData
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
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.HttpStatusCode
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class MovementsRepositoryImplTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private val november = YearMonth.of(2026, 11)
    private val october = YearMonth.of(2026, 10)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun movement(id: String, day: String, createdAt: String, month: String = "2026-11") =
        Movement(
            id = id,
            type = MovementType.EXPENSE,
            amount = Money(1000, "CLP"),
            categoryId = "c-1",
            tagId = null,
            paymentMethodId = "m-1",
            title = "t-$id",
            note = null,
            date = LocalDate.parse("$month-$day"),
            recordedBy = "u-1",
            createdAt = Instant.parse(createdAt),
        )

    private fun json(m: Movement) =
        """{"id":"${m.id}","recordedBy":"u-1","type":"EXPENSE","amount":{"amount":1000,"currency":"CLP"},
        |"categoryId":"c-1","paymentMethodId":"m-1","title":"${m.title}",
        |"occurredOn":"${m.date}T15:00:00.000Z","createdAt":"${m.createdAt}"}""".trimMargin()

    // Desordenados a propósito: el backend no ordena (BG-01).
    private val a = movement("a", "05", "2026-11-05T10:00:00Z")
    private val b = movement("b", "20", "2026-11-20T09:00:00Z")
    private val c = movement("c", "20", "2026-11-20T18:00:00Z")
    private val unordered = listOf(a, b, c)
    private val ordered = listOf(c, b, a)
    private val remoteBody = unordered.joinToString(prefix = "[", postfix = "]") { json(it) }

    private class Backend {
        val calls = AtomicInteger()
        val urls = mutableListOf<io.ktor.http.Url>()
        var offline = false
        var status = HttpStatusCode.OK
        var body: String? = null
        var gate: CompletableDeferred<Unit>? = null

        /** Método, ruta y cuerpo de cada llamada, para comprobar qué se envió. */
        val requests = mutableListOf<Triple<HttpMethod, String, String>>()

        /** Si responde algo, sustituye a la respuesta por defecto de esa llamada. */
        var override: ((HttpMethod, String) -> Pair<HttpStatusCode, String>?)? = null
    }

    private class FixedSession(var userId: String = "u-1", var failure: SessionFailure? = null) : SessionProvider {
        override suspend fun current(): Session {
            failure?.let { throw SessionUnavailableException(it) }
            return Session(userId, "tok")
        }
    }

    private class FakeConnectivity(var online: Boolean = true) : ConnectivityObserver {
        override val isOnline: Flow<Boolean> get() = flowOf(online)
    }

    private class Env(
        val repository: MovementsRepositoryImpl,
        val local: MovementsLocalDataSource,
        val backend: Backend,
        val session: FixedSession,
        val connectivity: FakeConnectivity,
        var clock: Long,
    )

    private fun TestScope.env(): Env {
        val backend = Backend()
        val engine = MockEngine { request ->
            backend.calls.incrementAndGet()
            backend.urls += request.url
            backend.requests += Triple(request.method, request.url.encodedPath, (request.body as? TextContent)?.text.orEmpty())
            backend.gate?.await()
            if (backend.offline) throw IOException("sin red")
            val custom = backend.override?.invoke(request.method, request.url.encodedPath)
            if (custom != null) respond(custom.second, custom.first, jsonHeaders)
            else respond(backend.body ?: remoteBody, backend.status, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "movements.preferences_pb")
        }
        val local = MovementsLocalDataSource(store)
        val session = FixedSession()
        val connectivity = FakeConnectivity()
        val dates = MovementDates(Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
        lateinit var env: Env
        val repository = MovementsRepositoryImpl(
            MovementsRemoteDataSource(client, "http://x", dates), local, session, connectivity, dates,
        ) { env.clock }
        env = Env(repository, local, backend, session, connectivity, now)
        return env
    }

    @Test
    fun sinCacheNiRefresco_noHayDatos() = runTest {
        assertEquals(MovementsData(), env().repository.movements("f-1", november).first())
    }

    @Test
    fun refresco_pideElMesCompletoConFromYToJuntos() = runTest {
        val env = env()

        env.repository.refresh("f-1", november)

        val url = env.backend.urls.single()
        assertEquals("/families/f-1/items", url.encodedPath)
        assertEquals("2026-11-01T00:00:00Z", url.parameters["from"])
        assertEquals("2026-11-30T23:59:59.999Z", url.parameters["to"])
    }

    @Test
    fun refresco_ordenaPorDiaYCreatedAtDescendentes() = runTest {
        val env = env()

        env.repository.refresh("f-1", november)

        assertEquals(ordered, env.repository.movements("f-1", november).first().movements)
        assertEquals(ordered, env.local.read("f-1", november)?.value)
    }

    @Test
    fun cacheVigente_seMuestraOrdenadaSinLlamarAlBackend() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 1.hours.inWholeMilliseconds, unordered)

        assertEquals(MovementsData(ordered, isStale = false), env.repository.movements("f-1", november).first())
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun cacheDeMasDe24Horas_seMuestraMarcadaComoDesactualizada() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 25.hours.inWholeMilliseconds, ordered)

        assertEquals(MovementsData(ordered, isStale = true), env.repository.movements("f-1", november).first())
    }

    @Test
    fun cacheDeOtroUsuario_seDescartaYSeBorraTodo() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-2", now, ordered)
        env.local.save("f-1", october, "u-2", now, ordered)

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())

        assertNull(env.local.read("f-1", november))
        assertNull(env.local.read("f-1", october))
    }

    @Test
    fun cacheDe7DiasOMas_seDescartaSoloEseMes() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 7.days.inWholeMilliseconds, ordered)
        env.local.save("f-1", october, "u-1", now, ordered)

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())

        assertNull(env.local.read("f-1", november))
        assertEquals(ordered, env.repository.movements("f-1", october).first().movements)
    }

    @Test
    fun cacheJustoAntesDeLos7Dias_seConserva() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 7.days.inWholeMilliseconds + 1, ordered)

        assertEquals(ordered, env.repository.movements("f-1", november).first().movements)
    }

    @Test
    fun refrescoCorrecto_quitaLaMarcaDeDesactualizada() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 25.hours.inWholeMilliseconds, ordered)

        env.repository.refresh("f-1", november)

        assertEquals(MovementsData(ordered, isStale = false), env.repository.movements("f-1", november).first())
    }

    @Test
    fun refrescoFallido_conservaLaCacheYDevuelveElFallo() = runTest {
        val env = env()
        val old = now - 25.hours.inWholeMilliseconds
        env.local.save("f-1", november, "u-1", old, listOf(a))
        env.backend.offline = true

        val result = env.repository.refresh("f-1", november)

        assertEquals(MovementResult.Failure(MovementFailure.Network), result)
        assertEquals(MovementsData(listOf(a), isStale = true), env.repository.movements("f-1", november).first())
        assertEquals(old, env.local.read("f-1", november)?.savedAtMillis)
    }

    @Test
    fun refrescoFallidoSinCache_sigueSinDatos() = runTest {
        val env = env()
        env.backend.status = HttpStatusCode.InternalServerError

        val result = env.repository.refresh("f-1", november)

        assertEquals(MovementResult.Failure(MovementFailure.Unknown), result)
        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
    }

    @Test
    fun respuestaInvalida_noGuardaNada() = runTest {
        val env = env()
        env.backend.body = """{"no":"es una lista"}"""

        assertEquals(MovementResult.Failure(MovementFailure.InvalidData), env.repository.refresh("f-1", november))
        assertNull(env.local.read("f-1", november))
    }

    @Test
    fun dosMeses_sonIndependientes() = runTest {
        val env = env()
        env.local.save("f-1", october, "u-1", now, listOf(a))

        env.repository.refresh("f-1", november)

        assertEquals(ordered, env.repository.movements("f-1", november).first().movements)
        assertEquals(listOf(a), env.repository.movements("f-1", october).first().movements)
    }

    @Test
    fun dosFamilias_noSeMezclan() = runTest {
        val env = env()

        env.repository.refresh("f-1", november)

        assertEquals(MovementsData(), env.repository.movements("f-2", november).first())
    }

    @Test
    fun movimientoPorId_presenteYAusente() = runTest {
        val env = env()
        env.repository.refresh("f-1", november)

        assertEquals(b, env.repository.movement("f-1", "b").first())
        assertNull(env.repository.movement("f-1", "nope").first())
        assertNull(env.repository.movement("f-2", "b").first())
    }

    @Test
    fun movimientoPorId_desapareceSiUnRefrescoYaNoLoTrae() = runTest {
        val env = env()
        env.repository.refresh("f-1", november)
        env.backend.body = "[${json(a)}]"

        env.repository.refresh("f-1", november)

        assertNull(env.repository.movement("f-1", "b").first())
        assertEquals(a, env.repository.movement("f-1", "a").first())
    }

    @Test
    fun movimientoPorId_loEncuentraEnCualquierMesCargado() = runTest {
        val env = env()
        val old = movement("o", "10", "2026-10-10T10:00:00Z", month = "2026-10")
        env.local.save("f-1", october, "u-1", now, listOf(old))
        env.repository.movements("f-1", october).first()

        assertEquals(old, env.repository.movement("f-1", "o").first())
    }

    @Test
    fun sinSesion_elRefrescoFallaSinLlamarAlBackend() = runTest {
        val env = env()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(MovementResult.Failure(MovementFailure.Unauthorized), env.repository.refresh("f-1", november))
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun sesionConFalloDesconocido_esUnknown() = runTest {
        val env = env()
        env.session.failure = SessionFailure.UNKNOWN

        assertEquals(MovementResult.Failure(MovementFailure.Unknown), env.repository.refresh("f-1", november))
    }

    @Test
    fun sinSesion_laLecturaNoMuestraNiLeeLaCache() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now, ordered)
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
    }

    @Test
    fun dosRefrescosSimultaneos_unaSolaLlamadaYMismoResultado() = runTest {
        val env = env()
        env.backend.gate = CompletableDeferred()

        val first = async { env.repository.refresh("f-1", november) }
        runCurrent()
        val second = async { env.repository.refresh("f-1", november) }
        runCurrent()
        env.backend.gate!!.complete(Unit)

        assertEquals(MovementResult.Success(Unit), first.await())
        assertEquals(MovementResult.Success(Unit), second.await())
        assertEquals(1, env.backend.calls.get())
    }

    @Test
    fun refrescosDeMesesDistintos_noSeBloqueanEntreSi() = runTest {
        val env = env()

        env.repository.refresh("f-1", november)
        env.repository.refresh("f-1", october)

        assertEquals(2, env.backend.calls.get())
    }

    @Test
    fun refrescoSecuencial_siHaceNuevaLlamada() = runTest {
        val env = env()

        env.repository.refresh("f-1", november)
        env.repository.refresh("f-1", november)

        assertEquals(2, env.backend.calls.get())
    }

    @Test
    fun borrar_vaciaMemoriaYCache() = runTest {
        val env = env()
        env.repository.refresh("f-1", november)

        env.repository.clear()

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
        assertNull(env.local.read("f-1", november))
        assertNull(env.repository.movement("f-1", "b").first())
    }

    @Test
    fun borrarDuranteUnRefresco_noEsperaALaRedYDescartaLoObtenido() = runTest {
        val env = env()
        env.backend.gate = CompletableDeferred()
        val refresh = async { env.repository.refresh("f-1", november) }
        runCurrent()

        env.repository.clear()
        env.backend.gate!!.complete(Unit)
        refresh.await()

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
        assertNull(env.local.read("f-1", november))
    }

    // --- Cambios (T-09) ---

    private val newMovement = NewMovement(
        amount = Money(5000, "CLP"),
        categoryId = "c-9",
        tagId = null,
        paymentMethodId = "m-7",
        title = "Nuevo",
        note = null,
        date = LocalDate.of(2026, 11, 12),
    )

    private fun created(id: String = "n-1") = HttpStatusCode.Created to """{"id":"$id","type":"EXPENSE"}"""

    private fun error(status: HttpStatusCode, code: String) = status to """{"error":"$code"}"""

    /** Deja noviembre cargado en memoria y en caché, con `savedAt` conocido. */
    private suspend fun TestScope.loaded(): Env {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 2.hours.inWholeMilliseconds, ordered)
        env.repository.movements("f-1", november).first()
        return env
    }

    private suspend fun Env.novemberList() = repository.movements("f-1", november).first().movements

    @Test
    fun sinConexion_ningunCambioLlamaAlBackend() = runTest {
        val env = loaded()
        env.connectivity.online = false

        assertEquals(MovementResult.Failure(MovementFailure.Offline), env.repository.createMovement("f-1", newMovement))
        assertEquals(
            MovementEditResult.Failure(MovementFailure.Offline),
            env.repository.editMovement("f-1", "a", MovementChanges(title = "x")),
        )
        assertEquals(MovementResult.Failure(MovementFailure.Offline), env.repository.deleteMovement("f-1", "a"))
        assertEquals(0, env.backend.calls.get())
        assertEquals(ordered, env.novemberList())
    }

    @Test
    fun altaSinSesion_fallaSinLlamarAlBackend() = runTest {
        val env = loaded()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(MovementResult.Failure(MovementFailure.Unauthorized), env.repository.createMovement("f-1", newMovement))
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun alta_completaRecordedByYCreatedAtYApareceEnSuMesOrdenada() = runTest {
        val env = loaded()
        env.backend.override = { m, _ -> if (m == HttpMethod.Post) created() else null }

        val result = env.repository.createMovement("f-1", newMovement) as MovementResult.Success

        assertEquals("u-1", result.value.recordedBy)
        assertEquals(Instant.ofEpochMilli(now), result.value.createdAt)
        assertEquals("n-1", result.value.id)
        // 12 de noviembre queda entre el día 20 y el día 5.
        assertEquals(listOf("c", "b", "n-1", "a"), env.novemberList()?.map { it.id })
        assertEquals(listOf("c", "b", "n-1", "a"), env.local.read("f-1", november)?.value?.map { it.id })
    }

    @Test
    fun alta_noApareceEnOtroMesCargado() = runTest {
        val env = loaded()
        env.local.save("f-1", october, "u-1", now, listOf(a))
        env.repository.movements("f-1", october).first()
        env.backend.override = { m, _ -> if (m == HttpMethod.Post) created() else null }

        env.repository.createMovement("f-1", newMovement)

        assertEquals(listOf(a), env.repository.movements("f-1", october).first().movements)
    }

    @Test
    fun alta_deUnMesNoCargado_noCreaEseMesPeroSiRecuerdaElMedio() = runTest {
        val env = loaded()
        env.backend.override = { m, _ -> if (m == HttpMethod.Post) created() else null }

        env.repository.createMovement("f-1", newMovement.copy(date = LocalDate.of(2026, 9, 3)))

        assertEquals(MovementsData(), env.repository.movements("f-1", YearMonth.of(2026, 9)).first())
        assertNull(env.local.read("f-1", YearMonth.of(2026, 9)))
        assertEquals(ordered, env.novemberList())
        assertEquals("m-7", env.repository.lastUsedPaymentMethodId("f-1"))
    }

    @Test
    fun alta_conservaElSavedAtDelMes() = runTest {
        val env = loaded()
        env.backend.override = { m, _ -> if (m == HttpMethod.Post) created() else null }

        env.repository.createMovement("f-1", newMovement)

        assertEquals(now - 2.hours.inWholeMilliseconds, env.local.read("f-1", november)?.savedAtMillis)
    }

    @Test
    fun altaFallida_noCambiaNadaNiRecuerdaElMedio() = runTest {
        val env = loaded()
        env.backend.override = { _, _ -> error(HttpStatusCode.BadRequest, "FINANCIAL_TRACKING.PAYMENT_METHOD_NOT_ACTIVE") }

        val result = env.repository.createMovement("f-1", newMovement)

        assertEquals(MovementResult.Failure(MovementFailure.PaymentMethodUnavailable), result)
        assertEquals(ordered, env.novemberList())
        assertNull(env.repository.lastUsedPaymentMethodId("f-1"))
    }

    @Test
    fun ultimoMedio_porUsuarioYFamilia() = runTest {
        val env = loaded()
        env.backend.override = { m, _ -> if (m == HttpMethod.Post) created() else null }

        env.repository.createMovement("f-1", newMovement)

        assertEquals("m-7", env.repository.lastUsedPaymentMethodId("f-1"))
        assertNull(env.repository.lastUsedPaymentMethodId("f-2"))
        env.session.userId = "u-2"
        assertNull(env.repository.lastUsedPaymentMethodId("f-1"))
    }

    @Test
    fun ultimoMedio_sinSesionEsNulo() = runTest {
        val env = loaded()
        env.local.saveLastUsedPaymentMethodId("u-1", "f-1", "m-1")
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertNull(env.repository.lastUsedPaymentMethodId("f-1"))
    }

    @Test
    fun edicionSinCambios_noLlamaAlBackend() = runTest {
        val env = loaded()

        assertEquals(MovementEditResult.Success, env.repository.editMovement("f-1", "a", MovementChanges()))
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun edicionSoloDeDatos_aplicaLoEnviadoYNoLlamaALaCategoria() = runTest {
        val env = loaded()
        env.backend.override = { m, _ -> if (m == HttpMethod.Patch) HttpStatusCode.OK to "{}" else null }

        val result = env.repository.editMovement(
            "f-1", "b",
            MovementChanges(amount = Money(777, "CLP"), title = "Nuevo título", note = NoteUpdate.Set("n"), paymentMethodId = "m-2"),
        )

        assertEquals(MovementEditResult.Success, result)
        assertEquals(listOf("/families/f-1/items/b"), env.backend.requests.map { it.second })
        val edited = env.novemberList()!!.first { it.id == "b" }
        assertEquals(Money(777, "CLP"), edited.amount)
        assertEquals("Nuevo título", edited.title)
        assertEquals("n", edited.note)
        assertEquals("m-2", edited.paymentMethodId)
        assertEquals(edited, env.local.read("f-1", november)?.value?.first { it.id == "b" })
    }

    @Test
    fun edicionQuitarObservaciones_dejaNulo() = runTest {
        val env = loaded()
        env.backend.override = { _, _ -> HttpStatusCode.OK to "{}" }
        env.local.save("f-1", november, "u-1", now, listOf(a.copy(note = "x")))
        env.repository.movements("f-1", november).first()

        env.repository.editMovement("f-1", "a", MovementChanges(note = NoteUpdate.Remove))

        assertNull(env.novemberList()!!.single().note)
    }

    @Test
    fun edicionSoloDeCategoria_llamaSoloALaReclasificacion() = runTest {
        val env = loaded()
        env.backend.override = { m, _ -> if (m == HttpMethod.Patch) HttpStatusCode.OK to "{}" else null }

        val result = env.repository.editMovement(
            "f-1", "a", MovementChanges(category = CategoryChange("c-2", "t-2")),
        )

        assertEquals(MovementEditResult.Success, result)
        assertEquals(listOf("/families/f-1/items/a/category"), env.backend.requests.map { it.second })
        val edited = env.novemberList()!!.first { it.id == "a" }
        assertEquals("c-2", edited.categoryId)
        assertEquals("t-2", edited.tagId)
    }

    @Test
    fun edicionDeDatosYCategoria_primeroLosDatos() = runTest {
        val env = loaded()
        env.backend.override = { m, _ -> if (m == HttpMethod.Patch) HttpStatusCode.OK to "{}" else null }

        val result = env.repository.editMovement(
            "f-1", "a", MovementChanges(title = "T", category = CategoryChange("c-2", null)),
        )

        assertEquals(MovementEditResult.Success, result)
        assertEquals(
            listOf("/families/f-1/items/a", "/families/f-1/items/a/category"),
            env.backend.requests.map { it.second },
        )
        val edited = env.novemberList()!!.first { it.id == "a" }
        assertEquals("T", edited.title)
        assertEquals("c-2", edited.categoryId)
        assertNull(edited.tagId)
    }

    @Test
    fun edicionParcial_fallaLaCategoriaPeroLosDatosQuedanAplicados() = runTest {
        val env = loaded()
        env.backend.override = { m, path ->
            if (m != HttpMethod.Patch) null
            else if (path.endsWith("/category")) error(HttpStatusCode.BadRequest, "FINANCIAL_TRACKING.CATEGORY_NOT_ACTIVE")
            else HttpStatusCode.OK to "{}"
        }

        val result = env.repository.editMovement(
            "f-1", "a", MovementChanges(title = "T", category = CategoryChange("c-2", null)),
        )

        assertEquals(MovementEditResult.Partial(MovementFailure.CategoryUnavailable), result)
        val edited = env.novemberList()!!.first { it.id == "a" }
        assertEquals("T", edited.title)
        assertEquals("c-1", edited.categoryId)
    }

    @Test
    fun edicionDeCategoriaQueFalla_sinDatosEsFalloNoParcial() = runTest {
        val env = loaded()
        env.backend.override = { _, _ -> error(HttpStatusCode.BadRequest, "FINANCIAL_TRACKING.CANNOT_RECLASSIFY_ACROSS_TYPES") }

        val result = env.repository.editMovement("f-1", "a", MovementChanges(category = CategoryChange("c-2", null)))

        assertEquals(MovementEditResult.Failure(MovementFailure.TypeMismatch), result)
        assertEquals(ordered, env.novemberList())
    }

    @Test
    fun edicionDondeFallanLosDatos_noIntentaLaCategoria() = runTest {
        val env = loaded()
        env.backend.override = { _, _ -> error(HttpStatusCode.NotFound, "FINANCIAL_TRACKING.FINANCIAL_ITEM_NOT_FOUND") }

        val result = env.repository.editMovement(
            "f-1", "a", MovementChanges(title = "T", category = CategoryChange("c-2", null)),
        )

        assertEquals(MovementEditResult.Failure(MovementFailure.NotFound), result)
        assertEquals(1, env.backend.calls.get())
        assertEquals(ordered, env.novemberList())
    }

    @Test
    fun edicionDeFechaAOtroMesCargado_loMueveDeUnMesAOtro() = runTest {
        val env = loaded()
        val savedOctober = now - 3.hours.inWholeMilliseconds
        env.local.save("f-1", october, "u-1", savedOctober, emptyList())
        env.repository.movements("f-1", october).first()
        env.backend.override = { _, _ -> HttpStatusCode.OK to "{}" }

        env.repository.editMovement("f-1", "a", MovementChanges(date = LocalDate.of(2026, 10, 30)))

        assertEquals(listOf("c", "b"), env.novemberList()?.map { it.id })
        val inOctober = env.repository.movements("f-1", october).first().movements!!
        assertEquals(listOf("a"), inOctober.map { it.id })
        assertEquals(LocalDate.of(2026, 10, 30), inOctober.single().date)
        assertEquals(savedOctober, env.local.read("f-1", october)?.savedAtMillis)
        assertEquals(now - 2.hours.inWholeMilliseconds, env.local.read("f-1", november)?.savedAtMillis)
    }

    @Test
    fun edicionDeFechaAUnMesNoCargado_saleDelMesActual() = runTest {
        val env = loaded()
        env.backend.override = { _, _ -> HttpStatusCode.OK to "{}" }

        env.repository.editMovement("f-1", "a", MovementChanges(date = LocalDate.of(2026, 8, 1)))

        assertEquals(listOf("c", "b"), env.novemberList()?.map { it.id })
        assertNull(env.local.read("f-1", YearMonth.of(2026, 8)))
        assertNull(env.repository.movement("f-1", "a").first())
    }

    @Test
    fun edicionDeFechaDentroDelMismoMes_reordena() = runTest {
        val env = loaded()
        env.backend.override = { _, _ -> HttpStatusCode.OK to "{}" }

        env.repository.editMovement("f-1", "a", MovementChanges(date = LocalDate.of(2026, 11, 25)))

        assertEquals(listOf("a", "c", "b"), env.novemberList()?.map { it.id })
    }

    @Test
    fun edicionDeUnMovimientoNoCargado_tieneExitoSinTocarNada() = runTest {
        val env = loaded()
        env.backend.override = { _, _ -> HttpStatusCode.OK to "{}" }

        val result = env.repository.editMovement("f-1", "otro", MovementChanges(title = "T"))

        assertEquals(MovementEditResult.Success, result)
        assertEquals(ordered, env.novemberList())
    }

    @Test
    fun eliminacion_quitaElMovimientoDeMemoriaYCache() = runTest {
        val env = loaded()
        env.backend.override = { m, _ -> if (m == HttpMethod.Delete) HttpStatusCode.NoContent to "" else null }

        val result = env.repository.deleteMovement("f-1", "b")

        assertTrue(result is MovementResult.Success)
        assertEquals(listOf("c", "a"), env.novemberList()?.map { it.id })
        assertEquals(listOf("c", "a"), env.local.read("f-1", november)?.value?.map { it.id })
        assertNull(env.repository.movement("f-1", "b").first())
        assertEquals(now - 2.hours.inWholeMilliseconds, env.local.read("f-1", november)?.savedAtMillis)
    }

    @Test
    fun eliminacionFallida_noCambiaNada() = runTest {
        val env = loaded()
        env.backend.override = { _, _ -> HttpStatusCode.InternalServerError to "" }

        val result = env.repository.deleteMovement("f-1", "b")

        assertEquals(MovementResult.Failure(MovementFailure.Unknown), result)
        assertEquals(ordered, env.novemberList())
        assertEquals(ordered, env.local.read("f-1", november)?.value)
    }

    @Test
    fun cambioDuranteUnClear_noResucitaDatosBorrados() = runTest {
        val env = loaded()
        env.backend.gate = CompletableDeferred()
        env.backend.override = { m, _ -> if (m == HttpMethod.Delete) HttpStatusCode.NoContent to "" else null }
        val delete = async { env.repository.deleteMovement("f-1", "b") }
        runCurrent()

        env.repository.clear()
        env.backend.gate!!.complete(Unit)
        delete.await()

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
        assertNull(env.local.read("f-1", november))
    }

    @Test
    fun conservarFamilias_borraLaPerdidaDeMemoriaYDisco_yConservaLasDemas() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 1.hours.inWholeMilliseconds, ordered)
        env.local.save("f-2", november, "u-1", now - 1.hours.inWholeMilliseconds, ordered)
        env.repository.movements("f-1", november).first()
        val kept = env.repository.movements("f-2", november).first()

        env.repository.retainFamilies(setOf("f-2"))

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
        assertEquals(kept, env.repository.movements("f-2", november).first())
        assertNull(env.local.read("f-1", november))
        assertNotNull(env.local.read("f-2", november))
    }

    @Test
    fun conservarFamilias_conjuntoVacio_borraTodasLasFamilias() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 1.hours.inWholeMilliseconds, ordered)
        env.repository.movements("f-1", november).first()

        env.repository.retainFamilies(emptySet())

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
        assertNull(env.local.read("f-1", november))
    }

    @Test
    fun conservarFamilias_conTodasVigentes_noCambiaNada() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now - 1.hours.inWholeMilliseconds, ordered)
        val before = env.repository.movements("f-1", november).first()

        env.repository.retainFamilies(setOf("f-1", "f-9"))

        assertEquals(before, env.repository.movements("f-1", november).first())
        assertNotNull(env.local.read("f-1", november))
    }

    @Test
    fun conservarFamilias_unaDescargaEnCursoDeLaPerdida_noSeGuarda_yNoEsperaALaRed() = runTest {
        val env = env()
        env.backend.gate = CompletableDeferred()
        val refresh = async { env.repository.refresh("f-1", november) }
        runCurrent()

        env.repository.retainFamilies(setOf("f-2"))
        assertFalse(refresh.isCompleted)
        env.backend.gate!!.complete(Unit)
        refresh.await()

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
        assertNull(env.local.read("f-1", november))
    }

    @Test
    fun conservarFamilias_unaDescargaEnCursoDeUnaVigente_sigueGuardandose() = runTest {
        val env = env()
        env.backend.gate = CompletableDeferred()
        val refresh = async { env.repository.refresh("f-2", november) }
        runCurrent()

        env.repository.retainFamilies(setOf("f-2"))
        env.backend.gate!!.complete(Unit)
        refresh.await()

        assertNotNull(env.local.read("f-2", november))
    }

    @Test
    fun conservarFamilias_unaFamiliaRecuperada_vuelveAGuardarse() = runTest {
        val env = env()
        env.repository.retainFamilies(emptySet())
        env.repository.refresh("f-1", november)
        assertNull(env.local.read("f-1", november))

        env.repository.retainFamilies(setOf("f-1"))
        env.repository.refresh("f-1", november)

        assertNotNull(env.local.read("f-1", november))
    }

    @Test
    fun conservarFamilias_clear_olvidaElConjuntoYVuelveAGuardarTodo() = runTest {
        val env = env()
        env.repository.retainFamilies(setOf("f-2"))
        env.repository.clear()

        env.repository.refresh("f-1", november)

        assertNotNull(env.local.read("f-1", november))
    }

    @Test
    fun conservarFamilias_borraTodosLosMesesYElUltimoMedioDeLaPerdida() = runTest {
        val env = env()
        env.local.save("f-1", november, "u-1", now, ordered)
        env.local.save("f-1", october, "u-1", now, ordered)
        env.local.save("f-2", november, "u-1", now, ordered)
        env.local.saveLastUsedPaymentMethodId("u-1", "f-1", "m-1")
        env.local.saveLastUsedPaymentMethodId("u-1", "f-2", "m-2")
        env.repository.movements("f-1", november).first()
        env.repository.movements("f-1", october).first()

        env.repository.retainFamilies(setOf("f-2"))

        assertEquals(MovementsData(), env.repository.movements("f-1", november).first())
        assertEquals(MovementsData(), env.repository.movements("f-1", october).first())
        assertNull(env.local.read("f-1", october))
        assertNull(env.repository.movement("f-1", "b").first())
        assertNull(env.repository.lastUsedPaymentMethodId("f-1"))
        assertEquals("m-2", env.repository.lastUsedPaymentMethodId("f-2"))
        assertNotNull(env.local.read("f-2", november))
    }
}
