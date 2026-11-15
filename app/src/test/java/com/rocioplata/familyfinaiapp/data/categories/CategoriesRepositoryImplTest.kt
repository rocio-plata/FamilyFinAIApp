package com.rocioplata.familyfinaiapp.data.categories

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
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

class CategoriesRepositoryImplTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_000_000_000_000L
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val remoteCategories = listOf(
        Category(
            "c-1", CategoryType.EXPENSE, "Alimentación", "home", CategoryStatus.ACTIVE,
            listOf(Tag("t-1", "Súper", TagStatus.ACTIVE, 0)),
        ),
    )
    private val remoteBody =
        """[{"id":"c-1","type":"EXPENSE","name":"Alimentación","icon":"home","status":"ACTIVE","tags":[{"id":"t-1","name":"Súper","status":"ACTIVE","displayOrder":0}]}]"""

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
        val repository: CategoriesRepositoryImpl,
        val local: CategoriesLocalDataSource,
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
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "c.preferences_pb") }
        val local = CategoriesLocalDataSource(store)
        val session = FixedSession()
        val connectivity = FakeConnectivity()
        lateinit var env: Env
        val repository = CategoriesRepositoryImpl(
            CategoriesRemoteDataSource(client, "http://x"), local, session, connectivity,
        ) { env.clock }
        env = Env(repository, local, backend, session, connectivity, now)
        return env
    }

    @Test
    fun sinCacheNiRefresco_noHayDatos() = runTest {
        val env = env()

        assertEquals(CategoriesData(), env.repository.categories("f-1").first())
    }

    @Test
    fun cacheVigenteDelUsuario_seMuestraSinLlamarAlBackend() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, remoteCategories)

        val data = env.repository.categories("f-1").first()

        assertEquals(CategoriesData(remoteCategories, isStale = false), data)
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun cacheDeMasDe24Horas_seMuestraMarcadaComoDesactualizada() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 25.hours.inWholeMilliseconds, remoteCategories)

        assertEquals(CategoriesData(remoteCategories, isStale = true), env.repository.categories("f-1").first())
    }

    @Test
    fun cacheDeOtroUsuario_seDescartaYSeBorra() = runTest {
        val env = env()
        env.local.save("f-1", "u-2", now, remoteCategories)
        env.local.save("f-2", "u-2", now, remoteCategories)

        assertEquals(CategoriesData(), env.repository.categories("f-1").first())

        assertNull(env.local.read("f-1"))
        assertNull(env.local.read("f-2"))
    }

    @Test
    fun cacheDe7DiasOMas_seDescartaSoloEnEsaFamilia() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 7.days.inWholeMilliseconds, remoteCategories)
        env.local.save("f-2", "u-1", now - 1.hours.inWholeMilliseconds, remoteCategories)

        assertEquals(CategoriesData(), env.repository.categories("f-1").first())

        assertNull(env.local.read("f-1"))
        assertEquals(CategoriesData(remoteCategories, false), env.repository.categories("f-2").first())
    }

    @Test
    fun cacheJustoAntesDeLos7Dias_seConserva() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 7.days.inWholeMilliseconds + 1, remoteCategories)

        val data = env.repository.categories("f-1").first()

        assertEquals(remoteCategories, data.categories)
        assertTrue(data.isStale)
    }

    @Test
    fun refrescoCorrecto_pideLaFamiliaConArchivadas_guardaYActualiza() = runTest {
        val env = env()

        val result = env.repository.refresh("f-1")

        assertEquals(CategoryResult.Success(Unit), result)
        assertEquals(listOf("/families/f-1/categories"), env.backend.paths)
        assertEquals(CategoriesData(remoteCategories, isStale = false), env.repository.categories("f-1").first())
        val cached = env.local.read("f-1")
        assertEquals("u-1", cached?.userId)
        assertEquals(now, cached?.savedAtMillis)
        assertEquals(remoteCategories, cached?.value)
    }

    @Test
    fun refrescoCorrecto_quitaLaMarcaDeDesactualizada() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 30.hours.inWholeMilliseconds, emptyList())
        assertTrue(env.repository.categories("f-1").first().isStale)

        env.repository.refresh("f-1")

        assertFalse(env.repository.categories("f-1").first().isStale)
    }

    @Test
    fun refrescoFallido_conservaLaCacheYDevuelveElFallo() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 2.hours.inWholeMilliseconds, remoteCategories)
        env.backend.offline = true

        val result = env.repository.refresh("f-1")

        assertEquals(CategoryResult.Failure(CategoryFailure.Network), result)
        assertEquals(CategoriesData(remoteCategories, false), env.repository.categories("f-1").first())
        assertEquals(now - 2.hours.inWholeMilliseconds, env.local.read("f-1")?.savedAtMillis)
    }

    @Test
    fun refrescoFallidoSinCache_sigueSinDatos() = runTest {
        val env = env()
        env.backend.status = HttpStatusCode.InternalServerError
        env.backend.body = """{"error":"X"}"""

        val result = env.repository.refresh("f-1")

        assertEquals(CategoryResult.Failure(CategoryFailure.Unknown), result)
        assertEquals(CategoriesData(), env.repository.categories("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    @Test
    fun refrescoCorrecto_reemplazaLosDatosAnteriores() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now - 1.hours.inWholeMilliseconds, emptyList())

        env.repository.refresh("f-1")

        assertEquals(remoteCategories, env.repository.categories("f-1").first().categories)
    }

    @Test
    fun datosPorFamilia_sinMezclarse() = runTest {
        val env = env()

        env.repository.refresh("f-1")

        assertEquals(remoteCategories, env.repository.categories("f-1").first().categories)
        assertEquals(CategoriesData(), env.repository.categories("f-2").first())
        assertNull(env.local.read("f-2"))
    }

    @Test
    fun sinSesion_elRefrescoFallaSinLlamarAlBackend() = runTest {
        val env = env()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(CategoryResult.Failure(CategoryFailure.Unauthorized), env.repository.refresh("f-1"))
        env.session.failure = SessionFailure.UNKNOWN
        assertEquals(CategoryResult.Failure(CategoryFailure.Unknown), env.repository.refresh("f-1"))
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun sinSesion_laLecturaNoMuestraNiLeeLaCache() = runTest {
        val env = env()
        env.local.save("f-1", "u-1", now, remoteCategories)
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(CategoriesData(), env.repository.categories("f-1").first())
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

        assertEquals(CategoryResult.Success(Unit), first.await())
        assertEquals(CategoryResult.Success(Unit), second.await())
        assertEquals(1, env.backend.calls.get())
    }

    @Test
    fun refrescosDeFamiliasDistintas_noSeBloqueanEntreSi() = runTest {
        val env = env()

        env.repository.refresh("f-1")
        env.repository.refresh("f-2")

        assertEquals(listOf("/families/f-1/categories", "/families/f-2/categories"), env.backend.paths)
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

        assertEquals(CategoriesData(), env.repository.categories("f-1").first())
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

        assertEquals(CategoriesData(), env.repository.categories("f-1").first())
        assertNull(env.local.read("f-1"))
    }

    // --- Operaciones de cambio (T-09) ---

    private val richCategories = listOf(
        Category(
            "c-1", CategoryType.EXPENSE, "Alimentación", "home", CategoryStatus.ACTIVE,
            listOf(
                Tag("t-1", "Súper", TagStatus.ACTIVE, 0),
                Tag("t-2", "Feria", TagStatus.ACTIVE, 1),
                Tag("t-3", "Viejo", TagStatus.DEPRECATED, 2),
            ),
        ),
        Category("c-2", CategoryType.INCOME, "Sueldo", null, CategoryStatus.ACTIVE, emptyList()),
    )
    private val richBody = """[
      {"id":"c-1","type":"EXPENSE","name":"Alimentación","icon":"home","status":"ACTIVE","tags":[
        {"id":"t-1","name":"Súper","status":"ACTIVE","displayOrder":0},
        {"id":"t-2","name":"Feria","status":"ACTIVE","displayOrder":1},
        {"id":"t-3","name":"Viejo","status":"DEPRECATED","displayOrder":2}]},
      {"id":"c-2","type":"INCOME","name":"Sueldo","icon":null,"status":"ACTIVE","tags":[]}]"""

    private val noContent = HttpStatusCode.NoContent to ""

    /** Entorno con las categorías ricas cargadas del backend, sin llamadas ni peticiones registradas, una hora después. */
    private suspend fun TestScope.loaded(): Env {
        val env = env()
        env.backend.body = richBody
        env.repository.refresh("f-1")
        env.backend.body = null
        env.backend.calls.set(0)
        env.backend.requests.clear()
        env.clock = now + 1.hours.inWholeMilliseconds
        return env
    }

    private suspend fun Env.current() = repository.categories("f-1").first().categories
    private suspend fun Env.cached() = local.read("f-1")

    @Test
    fun crearCategoria_seAnadeAlFinalYSeGuarda_conservandoElSavedAtDeLaDescarga() = runTest {
        val env = loaded()
        val created = Category("c-9", CategoryType.EXPENSE, "Casa", "pets", CategoryStatus.ACTIVE, emptyList())
        env.backend.responder = {
            HttpStatusCode.Created to """{"id":"c-9","type":"EXPENSE","name":"Casa","icon":"pets","status":"ACTIVE"}"""
        }

        val result = env.repository.createCategory("f-1", CategoryType.EXPENSE, "Casa", "pets")

        assertEquals(CategoryResult.Success(created), result)
        assertEquals(richCategories + created, env.current())
        assertEquals(richCategories + created, env.cached()?.value)
        assertEquals(now, env.cached()?.savedAtMillis)
        assertEquals("u-1", env.cached()?.userId)
    }

    @Test
    fun crearCategoria_duplicada_noCambiaNada() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.Conflict to """{"error":"FINANCIAL.DUPLICATE_CATEGORY_NAME"}""" }

        val result = env.repository.createCategory("f-1", CategoryType.EXPENSE, "Casa", null)

        assertEquals(CategoryResult.Failure(CategoryFailure.DuplicateName), result)
        assertEquals(richCategories, env.current())
        assertEquals(richCategories, env.cached()?.value)
    }

    @Test
    fun editarCategoria_nombreEIcono() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.OK to """{"id":"c-1","name":"Comida","icon":"pets","status":"ACTIVE"}""" }

        val result = env.repository.updateCategory("f-1", "c-1", "Comida", IconUpdate.Set("pets"))

        assertEquals(CategoryResult.Success(Unit), result)
        val edited = richCategories[0].copy(name = "Comida", icon = "pets")
        assertEquals(listOf(edited, richCategories[1]), env.current())
        assertEquals(listOf(edited, richCategories[1]), env.cached()?.value)
        assertEquals(now, env.cached()?.savedAtMillis)
    }

    @Test
    fun editarCategoria_quitarIconoYSoloNombre() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.OK to """{"id":"c-1","name":"x","icon":null,"status":"ACTIVE"}""" }

        env.repository.updateCategory("f-1", "c-1", null, IconUpdate.Remove)
        assertEquals(null, env.current()?.first { it.id == "c-1" }?.icon)
        assertEquals("Alimentación", env.current()?.first { it.id == "c-1" }?.name)

        env.repository.updateCategory("f-1", "c-1", "Otro", IconUpdate.Unchanged)
        assertEquals("Otro", env.current()?.first { it.id == "c-1" }?.name)
        assertEquals(null, env.current()?.first { it.id == "c-1" }?.icon)
    }

    @Test
    fun archivarCategoria_pasaAArchivada() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }

        val result = env.repository.deprecateCategory("f-1", "c-2")

        assertEquals(CategoryResult.Success(Unit), result)
        assertEquals(CategoryStatus.DEPRECATED, env.current()?.first { it.id == "c-2" }?.status)
        assertEquals(CategoryStatus.ACTIVE, env.current()?.first { it.id == "c-1" }?.status)
        assertEquals(CategoryStatus.DEPRECATED, env.cached()?.value?.first { it.id == "c-2" }?.status)
    }

    @Test
    fun eliminarCategoria_desapareceDeMemoriaYCache() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }

        val result = env.repository.deleteCategory("f-1", "c-1")

        assertEquals(CategoryResult.Success(Unit), result)
        assertEquals(listOf(richCategories[1]), env.current())
        assertEquals(listOf(richCategories[1]), env.cached()?.value)
    }

    @Test
    fun eliminarCategoria_conMovimientos_noCambiaNada() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.Conflict to """{"error":"FINANCIAL.CATEGORY_HAS_ASSOCIATED_ITEMS"}""" }

        val result = env.repository.deleteCategory("f-1", "c-1")

        assertEquals(CategoryResult.Failure(CategoryFailure.HasAssociatedItems), result)
        assertEquals(richCategories, env.current())
    }

    @Test
    fun anadirTag_seAnadeAlFinalConLosDatosDelBackend() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.Created to """{"id":"t-9","name":"Nuevo","status":"ACTIVE","displayOrder":3}""" }

        val result = env.repository.addTag("f-1", "c-1", "Nuevo")

        assertEquals(CategoryResult.Success(Unit), result)
        val tags = env.current()?.first { it.id == "c-1" }?.tags
        assertEquals(richCategories[0].tags + Tag("t-9", "Nuevo", TagStatus.ACTIVE, 3), tags)
        assertEquals(tags, env.cached()?.value?.first { it.id == "c-1" }?.tags)
    }

    @Test
    fun renombrarTag() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.OK to """{"id":"t-2","name":"Mercado","status":"ACTIVE","displayOrder":1}""" }

        env.repository.renameTag("f-1", "c-1", "t-2", "Mercado")

        val tags = env.current()?.first { it.id == "c-1" }?.tags
        assertEquals(listOf("Súper", "Mercado", "Viejo"), tags?.map { it.name })
        assertEquals(tags, env.cached()?.value?.first { it.id == "c-1" }?.tags)
    }

    @Test
    fun reordenarTags_enviaActivosEnElNuevoOrdenYDespuesLosArchivados() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }

        val result = env.repository.reorderTags("f-1", "c-1", listOf("t-2", "t-1"))

        assertEquals(CategoryResult.Success(Unit), result)
        val (method, path, body) = env.backend.requests.single()
        assertEquals(HttpMethod.Put, method)
        assertEquals("/families/f-1/categories/c-1/tags/order", path)
        assertEquals("""{"orderedTagIds":["t-2","t-1","t-3"]}""", body)
        val tags = env.current()?.first { it.id == "c-1" }?.tags
        assertEquals(
            listOf(
                Tag("t-2", "Feria", TagStatus.ACTIVE, 0),
                Tag("t-1", "Súper", TagStatus.ACTIVE, 1),
                Tag("t-3", "Viejo", TagStatus.DEPRECATED, 2),
            ),
            tags,
        )
        assertEquals(tags, env.cached()?.value?.first { it.id == "c-1" }?.tags)
    }

    @Test
    fun reordenarTags_conVariosArchivados_losMantieneEnSuOrden() = runTest {
        val env = env()
        env.backend.body = """[{"id":"c-1","type":"EXPENSE","name":"A","status":"ACTIVE","tags":[
            {"id":"a","name":"a","status":"DEPRECATED","displayOrder":2},
            {"id":"t-1","name":"1","status":"ACTIVE","displayOrder":0},
            {"id":"b","name":"b","status":"DEPRECATED","displayOrder":1}]}]"""
        env.repository.refresh("f-1")
        env.backend.requests.clear()
        env.backend.responder = { noContent }

        env.repository.reorderTags("f-1", "c-1", listOf("t-1"))

        assertEquals("""{"orderedTagIds":["t-1","b","a"]}""", env.backend.requests.single().third)
    }

    @Test
    fun reordenarTags_fallido_noCambiaElOrden() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.BadRequest to """{"error":"FINANCIAL.INVALID_TAG_ORDER"}""" }

        val result = env.repository.reorderTags("f-1", "c-1", listOf("t-2", "t-1"))

        assertEquals(CategoryResult.Failure(CategoryFailure.Unknown), result)
        assertEquals(richCategories, env.current())
    }

    @Test
    fun reordenarTags_deUnaCategoriaDesconocida_noLlamaAlBackend() = runTest {
        val env = loaded()

        val result = env.repository.reorderTags("f-1", "c-404", listOf("t-1"))

        assertEquals(CategoryResult.Failure(CategoryFailure.NotFound), result)
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun archivarTag_pasaAArchivado() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }

        env.repository.deprecateTag("f-1", "c-1", "t-1")

        val tags = env.current()?.first { it.id == "c-1" }?.tags
        assertEquals(TagStatus.DEPRECATED, tags?.first { it.id == "t-1" }?.status)
        assertEquals(TagStatus.ACTIVE, tags?.first { it.id == "t-2" }?.status)
        assertEquals(tags, env.cached()?.value?.first { it.id == "c-1" }?.tags)
    }

    @Test
    fun eliminarTag_desapareceDeMemoriaYCache() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }

        env.repository.deleteTag("f-1", "c-1", "t-3")

        val tags = env.current()?.first { it.id == "c-1" }?.tags
        assertEquals(listOf("t-1", "t-2"), tags?.map { it.id })
        assertEquals(tags, env.cached()?.value?.first { it.id == "c-1" }?.tags)
    }

    @Test
    fun eliminarTag_conMovimientos_noCambiaNada() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.Conflict to """{"error":"FINANCIAL.TAG_HAS_ASSOCIATED_ITEMS"}""" }

        val result = env.repository.deleteTag("f-1", "c-1", "t-1")

        assertEquals(CategoryResult.Failure(CategoryFailure.HasAssociatedItems), result)
        assertEquals(richCategories, env.current())
    }

    @Test
    fun fallosDelBackend_noAlteranNiMemoriaNiCache() = runTest {
        val env = loaded()
        env.backend.responder = { HttpStatusCode.Forbidden to """{"error":"FINANCIAL.INSUFFICIENT_ROLE"}""" }

        val result = env.repository.deprecateCategory("f-1", "c-1")

        assertEquals(CategoryResult.Failure(CategoryFailure.InsufficientRole), result)
        assertEquals(richCategories, env.current())
        assertEquals(richCategories, env.cached()?.value)
        assertEquals(now, env.cached()?.savedAtMillis)
    }

    @Test
    fun sinConexion_cadaOperacionDevuelveOfflineSinLlamarAlBackend() = runTest {
        val env = loaded()
        env.connectivity.online = false
        val offline = CategoryResult.Failure(CategoryFailure.Offline)
        val repo = env.repository

        assertEquals(offline, repo.createCategory("f-1", CategoryType.EXPENSE, "x", null))
        assertEquals(offline, repo.updateCategory("f-1", "c-1", "x", IconUpdate.Unchanged))
        assertEquals(offline, repo.deprecateCategory("f-1", "c-1"))
        assertEquals(offline, repo.deleteCategory("f-1", "c-1"))
        assertEquals(offline, repo.addTag("f-1", "c-1", "x"))
        assertEquals(offline, repo.renameTag("f-1", "c-1", "t-1", "x"))
        assertEquals(offline, repo.reorderTags("f-1", "c-1", listOf("t-1")))
        assertEquals(offline, repo.deprecateTag("f-1", "c-1", "t-1"))
        assertEquals(offline, repo.deleteTag("f-1", "c-1", "t-1"))

        assertEquals(0, env.backend.calls.get())
        assertEquals(richCategories, env.current())
    }

    @Test
    fun sinSesion_elCambioFallaSinLlamarAlBackend() = runTest {
        val env = loaded()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        val result = env.repository.deleteCategory("f-1", "c-1")

        assertEquals(CategoryResult.Failure(CategoryFailure.Unauthorized), result)
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun cambioSinDatosCargadosDeLaFamilia_devuelveElResultadoSinTocarLaCache() = runTest {
        val env = env()
        env.backend.responder = { noContent }

        val result = env.repository.deleteCategory("f-9", "c-1")

        assertEquals(CategoryResult.Success(Unit), result)
        assertNull(env.local.read("f-9"))
        assertEquals(CategoriesData(), env.repository.categories("f-9").first())
    }

    @Test
    fun cambiosEnUnaFamilia_noAfectanAOtra() = runTest {
        val env = loaded()
        env.backend.body = richBody
        env.repository.refresh("f-2")
        env.backend.responder = { noContent }

        env.repository.deleteCategory("f-1", "c-1")

        assertEquals(listOf(richCategories[1]), env.current())
        assertEquals(richCategories, env.repository.categories("f-2").first().categories)
    }

    @Test
    fun borrarDuranteUnCambio_noGuardaNada() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }
        env.backend.gate = CompletableDeferred()
        val change = async { env.repository.deleteCategory("f-1", "c-1") }
        runCurrent()

        env.repository.clear()
        env.backend.gate!!.complete(Unit)
        change.await()

        assertEquals(CategoriesData(), env.repository.categories("f-1").first())
        assertNull(env.cached())
    }

    @Test
    fun unRefrescoPosterior_reemplazaLosCambiosAplicadosLocalmente() = runTest {
        val env = loaded()
        env.backend.responder = { noContent }
        env.repository.deleteCategory("f-1", "c-1")

        env.backend.responder = null
        env.backend.body = richBody
        env.repository.refresh("f-1")

        assertEquals(richCategories, env.current())
        assertEquals(env.clock, env.cached()?.savedAtMillis)
    }
}
