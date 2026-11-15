package com.rocioplata.familyfinaiapp.data.categories

import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CategoriesRemoteDataSourceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): CategoriesRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
            install(SessionAuth) { accessToken = { "tok" } }
        }
        return CategoriesRemoteDataSource(client, "http://localhost:3000")
    }

    private fun reply(status: HttpStatusCode, body: String): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(body, status, jsonHeaders) }

    private fun errorBody(code: String) = """{"error":"$code","message":"texto que no se lee"}"""

    private suspend fun failureOf(status: HttpStatusCode, body: String): CategoryFailure {
        val result = source(reply(status, body)).categories("f-1")
        return (result as CategoryResult.Failure).failure
    }

    @Test
    fun listado_pideTodasLasCategoriasConArchivadasYEnviaBearer() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var include: String? = null
        var auth: String? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            include = it.url.parameters["includeDeprecated"]
            auth = it.headers[HttpHeaders.Authorization]
            respond("[]", HttpStatusCode.OK, jsonHeaders)
        }

        source.categories("f-1")

        assertEquals(HttpMethod.Get, method)
        assertEquals("/families/f-1/categories", path)
        assertEquals("true", include)
        assertEquals("Bearer tok", auth)
    }

    @Test
    fun listado_mapeaCategoriasConTagsYArchivadas() = runTest {
        val body = """[
          {"id":"c-1","type":"EXPENSE","name":"Alimentación","icon":"shopping_cart","status":"ACTIVE","tags":[
            {"id":"t-1","name":"Super","status":"ACTIVE","displayOrder":0},
            {"id":"t-2","name":"Viejo","status":"DEPRECATED","displayOrder":1}]},
          {"id":"c-2","type":"INCOME","name":"Sueldo","icon":null,"status":"DEPRECATED","tags":[]}
        ]"""

        val result = source(reply(HttpStatusCode.OK, body)).categories("f-1")

        assertEquals(
            CategoryResult.Success(
                listOf(
                    Category(
                        "c-1", CategoryType.EXPENSE, "Alimentación", "shopping_cart", CategoryStatus.ACTIVE,
                        listOf(Tag("t-1", "Super", TagStatus.ACTIVE, 0), Tag("t-2", "Viejo", TagStatus.DEPRECATED, 1)),
                    ),
                    Category("c-2", CategoryType.INCOME, "Sueldo", null, CategoryStatus.DEPRECATED, emptyList()),
                ),
            ),
            result,
        )
    }

    @Test
    fun listado_iconoAusenteOCategoriaSinTags_seAceptan() = runTest {
        val body = """[{"id":"c-1","type":"EXPENSE","name":"Casa","status":"ACTIVE"}]"""

        val result = source(reply(HttpStatusCode.OK, body)).categories("f-1")

        assertEquals(
            CategoryResult.Success(listOf(Category("c-1", CategoryType.EXPENSE, "Casa", null, CategoryStatus.ACTIVE, emptyList()))),
            result,
        )
    }

    @Test
    fun listado_listaVacia_esExito() = runTest {
        assertEquals(CategoryResult.Success(emptyList<Category>()), source(reply(HttpStatusCode.OK, "[]")).categories("f-1"))
    }

    @Test
    fun listado_valoresDesconocidosOCuerpoIlegible_sonDatosInvalidos() = runTest {
        val unknownType = """[{"id":"c","type":"TRANSFER","name":"x","status":"ACTIVE","tags":[]}]"""
        val unknownStatus = """[{"id":"c","type":"EXPENSE","name":"x","status":"ARCHIVED","tags":[]}]"""
        val unknownTagStatus =
            """[{"id":"c","type":"EXPENSE","name":"x","status":"ACTIVE","tags":[{"id":"t","name":"y","status":"???","displayOrder":0}]}]"""
        listOf(unknownType, unknownStatus, unknownTagStatus, "no es json", """{"id":"c"}""").forEach {
            assertEquals(it, CategoryResult.Failure(CategoryFailure.InvalidData), source(reply(HttpStatusCode.OK, it)).categories("f-1"))
        }
    }

    @Test
    fun errores_segunElCodigoDelCuerpo() = runTest {
        assertEquals(
            CategoryFailure.InsufficientRole,
            failureOf(HttpStatusCode.Forbidden, errorBody("FINANCIAL.INSUFFICIENT_ROLE")),
        )
        assertEquals(
            CategoryFailure.InsufficientRole,
            failureOf(HttpStatusCode.Forbidden, errorBody("FAMILY.INSUFFICIENT_ROLE")),
        )
        assertEquals(
            CategoryFailure.DuplicateName,
            failureOf(HttpStatusCode.Conflict, errorBody("FINANCIAL.DUPLICATE_CATEGORY_NAME")),
        )
        assertEquals(
            CategoryFailure.DuplicateName,
            failureOf(HttpStatusCode.Conflict, errorBody("FINANCIAL.DUPLICATE_TAG_NAME")),
        )
        assertEquals(
            CategoryFailure.HasAssociatedItems,
            failureOf(HttpStatusCode.Conflict, errorBody("FINANCIAL.CATEGORY_HAS_ASSOCIATED_ITEMS")),
        )
        assertEquals(
            CategoryFailure.HasAssociatedItems,
            failureOf(HttpStatusCode.Conflict, errorBody("FINANCIAL.TAG_HAS_ASSOCIATED_ITEMS")),
        )
        assertEquals(
            CategoryFailure.CategoryNotActive,
            failureOf(HttpStatusCode.BadRequest, errorBody("FINANCIAL.CATEGORY_NOT_ACTIVE")),
        )
    }

    // El backend emite `FINANCIAL_TRACKING.*` aunque el contrato documente `FINANCIAL.*` (BG-06): ambos valen.
    @Test
    fun errores_conElPrefijoRealDelBackend() = runTest {
        assertEquals(
            CategoryFailure.DuplicateName,
            failureOf(HttpStatusCode.Conflict, errorBody("FINANCIAL_TRACKING.DUPLICATE_CATEGORY_NAME")),
        )
        assertEquals(
            CategoryFailure.DuplicateName,
            failureOf(HttpStatusCode.Conflict, errorBody("FINANCIAL_TRACKING.DUPLICATE_TAG_NAME")),
        )
        assertEquals(
            CategoryFailure.HasAssociatedItems,
            failureOf(HttpStatusCode.Conflict, errorBody("FINANCIAL_TRACKING.CATEGORY_HAS_ASSOCIATED_ITEMS")),
        )
        assertEquals(
            CategoryFailure.HasAssociatedItems,
            failureOf(HttpStatusCode.Conflict, errorBody("FINANCIAL_TRACKING.TAG_HAS_ASSOCIATED_ITEMS")),
        )
        assertEquals(
            CategoryFailure.CategoryNotActive,
            failureOf(HttpStatusCode.BadRequest, errorBody("FINANCIAL_TRACKING.CATEGORY_NOT_ACTIVE")),
        )
        assertEquals(
            CategoryFailure.InsufficientRole,
            failureOf(HttpStatusCode.Forbidden, errorBody("FAMILY.INSUFFICIENT_ROLE")),
        )
        assertEquals(
            CategoryFailure.NotFound,
            failureOf(HttpStatusCode.NotFound, errorBody("FINANCIAL_TRACKING.CATEGORY_NOT_FOUND")),
        )
    }

    @Test
    fun errores_unPrefijoDesconocidoNoSeInterpreta() = runTest {
        assertEquals(
            CategoryFailure.Unknown,
            failureOf(HttpStatusCode.Conflict, errorBody("OTRO.DUPLICATE_CATEGORY_NAME")),
        )
    }

    @Test
    fun errores_404DeCualquierCodigoEsNoEncontrado() = runTest {
        assertEquals(CategoryFailure.NotFound, failureOf(HttpStatusCode.NotFound, errorBody("FINANCIAL.CATEGORY_NOT_FOUND")))
        assertEquals(CategoryFailure.NotFound, failureOf(HttpStatusCode.NotFound, errorBody("FAMILY.NOT_FOUND")))
        assertEquals(CategoryFailure.NotFound, failureOf(HttpStatusCode.NotFound, "sin cuerpo json"))
    }

    @Test
    fun errores_401EsNoAutorizadoYElRestoDesconocido() = runTest {
        assertEquals(CategoryFailure.Unauthorized, failureOf(HttpStatusCode.Unauthorized, errorBody("AUTH.UNAUTHORIZED")))
        assertEquals(CategoryFailure.Unauthorized, failureOf(HttpStatusCode.Unauthorized, "sin cuerpo json"))
        assertEquals(CategoryFailure.Unknown, failureOf(HttpStatusCode.InternalServerError, errorBody("INTERNAL")))
        assertEquals(CategoryFailure.Unknown, failureOf(HttpStatusCode.BadRequest, errorBody("FINANCIAL.INVALID_TAG_ORDER")))
        assertEquals(CategoryFailure.Unknown, failureOf(HttpStatusCode.Forbidden, errorBody("FAMILY.NOT_A_MEMBER")))
        assertEquals(CategoryFailure.Unknown, failureOf(HttpStatusCode.BadGateway, "<html>"))
    }

    @Test
    fun red_ioExceptionEsFalloDeRed() = runTest {
        val result = source { throw IOException("sin red") }.categories("f-1")

        assertEquals(CategoryResult.Failure(CategoryFailure.Network), result)
    }

    @Test
    fun red_esperaExcesivaEsFalloDeRed() = runTest {
        // En Ktor el timeout es un IOException; como en la cabecera, se trata como fallo de red.
        val result = source { throw HttpRequestTimeoutException("http://localhost:3000/x", 15_000L) }.categories("f-1")

        assertEquals(CategoryResult.Failure(CategoryFailure.Network), result)
    }

    @Test
    fun cancelacion_seRelanza() = runTest {
        val source = source { throw CancellationException("cancelada") }

        assertThrows(CancellationException::class.java) { runBlocking { source.categories("f-1") } }
    }

    // --- Operaciones de categoría (T-05) ---

    private class Captured {
        var method: HttpMethod? = null
        var path: String? = null
        var body: JsonElement? = null
        var contentType: String? = null
        var requests = 0
    }

    private fun capturing(status: HttpStatusCode, responseBody: String = "", captured: Captured) = source {
        captured.requests++
        captured.method = it.method
        captured.path = it.url.encodedPath
        captured.contentType = it.body.contentType?.withoutParameters()?.toString()
        captured.body = (it.body as? TextContent)?.text?.let(Json::parseToJsonElement)
        respond(responseBody, status, jsonHeaders)
    }

    @Test
    fun crear_enviaTipoNombreEIconoYDevuelveLaCategoriaSinTags() = runTest {
        val captured = Captured()
        val source = capturing(
            HttpStatusCode.Created,
            """{"id":"c-9","type":"INCOME","name":"Sueldo","icon":"home","status":"ACTIVE"}""",
            captured,
        )

        val result = source.createCategory("f-1", CategoryType.INCOME, "Sueldo", "home")

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/families/f-1/categories", captured.path)
        assertEquals("application/json", captured.contentType)
        assertEquals(buildJsonObject { put("type", "INCOME"); put("name", "Sueldo"); put("icon", "home") }, captured.body)
        assertEquals(
            CategoryResult.Success(Category("c-9", CategoryType.INCOME, "Sueldo", "home", CategoryStatus.ACTIVE, emptyList())),
            result,
        )
    }

    @Test
    fun crear_sinIconoNoEnviaElCampo() = runTest {
        val captured = Captured()
        val source = capturing(
            HttpStatusCode.Created,
            """{"id":"c-9","type":"EXPENSE","name":"Casa","icon":null,"status":"ACTIVE"}""",
            captured,
        )

        val result = source.createCategory("f-1", CategoryType.EXPENSE, "Casa", null)

        assertEquals(buildJsonObject { put("type", "EXPENSE"); put("name", "Casa") }, captured.body)
        assertEquals(
            CategoryResult.Success(Category("c-9", CategoryType.EXPENSE, "Casa", null, CategoryStatus.ACTIVE, emptyList())),
            result,
        )
    }

    @Test
    fun crear_nombreDuplicadoYRolInsuficiente() = runTest {
        val duplicate = source(reply(HttpStatusCode.Conflict, errorBody("FINANCIAL.DUPLICATE_CATEGORY_NAME")))
        val forbidden = source(reply(HttpStatusCode.Forbidden, errorBody("FINANCIAL.INSUFFICIENT_ROLE")))

        assertEquals(
            CategoryResult.Failure(CategoryFailure.DuplicateName),
            duplicate.createCategory("f-1", CategoryType.EXPENSE, "Casa", null),
        )
        assertEquals(
            CategoryResult.Failure(CategoryFailure.InsufficientRole),
            forbidden.createCategory("f-1", CategoryType.EXPENSE, "Casa", null),
        )
    }

    @Test
    fun crear_respuestaConValoresDesconocidos_sonDatosInvalidos() = runTest {
        val source = source(reply(HttpStatusCode.Created, """{"id":"c","type":"OTRO","name":"x","status":"ACTIVE"}"""))

        assertEquals(
            CategoryResult.Failure(CategoryFailure.InvalidData),
            source.createCategory("f-1", CategoryType.EXPENSE, "x", null),
        )
    }

    @Test
    fun crear_unOkEnLugarDeCreated_noEsExito() = runTest {
        val source = source(reply(HttpStatusCode.OK, """{"id":"c","type":"EXPENSE","name":"x","status":"ACTIVE"}"""))

        assertEquals(
            CategoryResult.Failure(CategoryFailure.Unknown),
            source.createCategory("f-1", CategoryType.EXPENSE, "x", null),
        )
    }

    @Test
    fun editar_soloNombre_noEnviaElIcono() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.OK, """{"id":"c-1","name":"Nuevo","icon":"home","status":"ACTIVE"}""", captured)

        val result = source.updateCategory("f-1", "c-1", "Nuevo", IconUpdate.Unchanged)

        assertEquals(HttpMethod.Patch, captured.method)
        assertEquals("/families/f-1/categories/c-1", captured.path)
        assertEquals(buildJsonObject { put("newName", "Nuevo") }, captured.body)
        assertEquals(CategoryResult.Success(Unit), result)
    }

    @Test
    fun editar_soloIcono_noEnviaElNombre() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.OK, """{"id":"c-1","name":"x","icon":"pets","status":"ACTIVE"}""", captured)

        source.updateCategory("f-1", "c-1", null, IconUpdate.Set("pets"))

        assertEquals(buildJsonObject { put("newIcon", "pets") }, captured.body)
    }

    @Test
    fun editar_quitarIcono_enviaNewIconNuloExplicito() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.OK, """{"id":"c-1","name":"x","icon":null,"status":"ACTIVE"}""", captured)

        source.updateCategory("f-1", "c-1", null, IconUpdate.Remove)

        val body = captured.body as JsonObject
        assertEquals(setOf("newIcon"), body.keys)
        assertEquals(JsonNull, body["newIcon"])
    }

    @Test
    fun editar_nombreEIconoJuntos() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.OK, """{"id":"c-1","name":"A","icon":"home","status":"ACTIVE"}""", captured)

        source.updateCategory("f-1", "c-1", "A", IconUpdate.Set("home"))

        assertEquals(buildJsonObject { put("newName", "A"); put("newIcon", JsonPrimitive("home")) }, captured.body)
    }

    @Test
    fun editar_errores() = runTest {
        assertEquals(
            CategoryResult.Failure(CategoryFailure.DuplicateName),
            source(reply(HttpStatusCode.Conflict, errorBody("FINANCIAL.DUPLICATE_CATEGORY_NAME")))
                .updateCategory("f-1", "c-1", "A", IconUpdate.Unchanged),
        )
        assertEquals(
            CategoryResult.Failure(CategoryFailure.NotFound),
            source(reply(HttpStatusCode.NotFound, errorBody("FINANCIAL.CATEGORY_NOT_FOUND")))
                .updateCategory("f-1", "c-1", "A", IconUpdate.Unchanged),
        )
    }

    @Test
    fun archivar_hacePostADeprecateYAceptaNoContent() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.NoContent, captured = captured)

        val result = source.deprecateCategory("f-1", "c-1")

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/families/f-1/categories/c-1/deprecate", captured.path)
        assertEquals(CategoryResult.Success(Unit), result)
    }

    @Test
    fun eliminar_haceDeleteYAceptaNoContent() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.NoContent, captured = captured)

        val result = source.deleteCategory("f-1", "c-1")

        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/families/f-1/categories/c-1", captured.path)
        assertEquals(CategoryResult.Success(Unit), result)
    }

    @Test
    fun eliminar_conMovimientosAsociados() = runTest {
        val source = source(reply(HttpStatusCode.Conflict, errorBody("FINANCIAL.CATEGORY_HAS_ASSOCIATED_ITEMS")))

        assertEquals(CategoryResult.Failure(CategoryFailure.HasAssociatedItems), source.deleteCategory("f-1", "c-1"))
    }

    @Test
    fun archivarYEliminar_erroresDeRedYRol() = runTest {
        assertEquals(
            CategoryResult.Failure(CategoryFailure.Network),
            source { throw IOException("sin red") }.deprecateCategory("f-1", "c-1"),
        )
        assertEquals(
            CategoryResult.Failure(CategoryFailure.InsufficientRole),
            source(reply(HttpStatusCode.Forbidden, errorBody("FINANCIAL.INSUFFICIENT_ROLE"))).deleteCategory("f-1", "c-1"),
        )
    }

    @Test
    fun identificadoresConCaracteresEspeciales_seCodificanEnLaRuta() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.NoContent, captured = captured)

        source.deleteCategory("f 1/x", "c?1")

        assertEquals("/families/f%201%2Fx/categories/c%3F1", captured.path)
    }

    // --- Operaciones de tags (T-06) ---

    @Test
    fun anadirTag_enviaElNombreYDevuelveElTagCreado() = runTest {
        val captured = Captured()
        val source = capturing(
            HttpStatusCode.Created,
            """{"id":"t-3","name":"Feria","status":"ACTIVE","displayOrder":2}""",
            captured,
        )

        val result = source.addTag("f-1", "c-1", "Feria")

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/families/f-1/categories/c-1/tags", captured.path)
        assertEquals("application/json", captured.contentType)
        assertEquals(buildJsonObject { put("name", "Feria") }, captured.body)
        assertEquals(CategoryResult.Success(Tag("t-3", "Feria", TagStatus.ACTIVE, 2)), result)
    }

    @Test
    fun anadirTag_errores() = runTest {
        assertEquals(
            CategoryResult.Failure(CategoryFailure.DuplicateName),
            source(reply(HttpStatusCode.Conflict, errorBody("FINANCIAL.DUPLICATE_TAG_NAME"))).addTag("f-1", "c-1", "x"),
        )
        assertEquals(
            CategoryResult.Failure(CategoryFailure.CategoryNotActive),
            source(reply(HttpStatusCode.BadRequest, errorBody("FINANCIAL.CATEGORY_NOT_ACTIVE"))).addTag("f-1", "c-1", "x"),
        )
        assertEquals(
            CategoryResult.Failure(CategoryFailure.InsufficientRole),
            source(reply(HttpStatusCode.Forbidden, errorBody("FINANCIAL.INSUFFICIENT_ROLE"))).addTag("f-1", "c-1", "x"),
        )
    }

    @Test
    fun anadirTag_estadoDesconocidoEnLaRespuesta_sonDatosInvalidos() = runTest {
        val source = source(reply(HttpStatusCode.Created, """{"id":"t","name":"x","status":"???","displayOrder":0}"""))

        assertEquals(CategoryResult.Failure(CategoryFailure.InvalidData), source.addTag("f-1", "c-1", "x"))
    }

    @Test
    fun renombrarTag_hacePatchConElNombre() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.OK, """{"id":"t-1","name":"Nuevo","status":"ACTIVE","displayOrder":0}""", captured)

        val result = source.renameTag("f-1", "c-1", "t-1", "Nuevo")

        assertEquals(HttpMethod.Patch, captured.method)
        assertEquals("/families/f-1/categories/c-1/tags/t-1", captured.path)
        assertEquals(buildJsonObject { put("name", "Nuevo") }, captured.body)
        assertEquals(CategoryResult.Success(Unit), result)
    }

    @Test
    fun renombrarTag_errores() = runTest {
        assertEquals(
            CategoryResult.Failure(CategoryFailure.DuplicateName),
            source(reply(HttpStatusCode.Conflict, errorBody("FINANCIAL.DUPLICATE_TAG_NAME")))
                .renameTag("f-1", "c-1", "t-1", "x"),
        )
        assertEquals(
            CategoryResult.Failure(CategoryFailure.NotFound),
            source(reply(HttpStatusCode.NotFound, errorBody("FINANCIAL.TAG_NOT_FOUND"))).renameTag("f-1", "c-1", "t-1", "x"),
        )
    }

    @Test
    fun reordenarTags_hacePutConLaListaCompletaEnOrden() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.NoContent, captured = captured)

        val result = source.reorderTags("f-1", "c-1", listOf("t-2", "t-1", "t-9"))

        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/families/f-1/categories/c-1/tags/order", captured.path)
        assertEquals("application/json", captured.contentType)
        assertEquals(
            buildJsonObject { put("orderedTagIds", JsonArray(listOf("t-2", "t-1", "t-9").map(::JsonPrimitive))) },
            captured.body,
        )
        assertEquals(CategoryResult.Success(Unit), result)
    }

    @Test
    fun reordenarTags_rechazoDeOrden_esErrorGenerico() = runTest {
        // El contrato no define el código de este rechazo: se trata como desconocido.
        val source = source(reply(HttpStatusCode.BadRequest, errorBody("FINANCIAL.INVALID_TAG_ORDER")))

        assertEquals(CategoryResult.Failure(CategoryFailure.Unknown), source.reorderTags("f-1", "c-1", listOf("t-1")))
    }

    @Test
    fun archivarTag_hacePostADeprecate() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.NoContent, captured = captured)

        val result = source.deprecateTag("f-1", "c-1", "t-1")

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/families/f-1/categories/c-1/tags/t-1/deprecate", captured.path)
        assertEquals(CategoryResult.Success(Unit), result)
    }

    @Test
    fun eliminarTag_haceDelete() = runTest {
        val captured = Captured()
        val source = capturing(HttpStatusCode.NoContent, captured = captured)

        val result = source.deleteTag("f-1", "c-1", "t-1")

        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/families/f-1/categories/c-1/tags/t-1", captured.path)
        assertEquals(CategoryResult.Success(Unit), result)
    }

    @Test
    fun eliminarTag_conMovimientosAsociados() = runTest {
        val source = source(reply(HttpStatusCode.Conflict, errorBody("FINANCIAL.TAG_HAS_ASSOCIATED_ITEMS")))

        assertEquals(CategoryResult.Failure(CategoryFailure.HasAssociatedItems), source.deleteTag("f-1", "c-1", "t-1"))
    }

    @Test
    fun tags_erroresDeRedYCancelacion() = runTest {
        assertEquals(
            CategoryResult.Failure(CategoryFailure.Network),
            source { throw IOException("sin red") }.reorderTags("f-1", "c-1", listOf("t-1")),
        )
        val cancelled = source { throw CancellationException("cancelada") }
        assertThrows(CancellationException::class.java) { runBlocking { cancelled.deleteTag("f-1", "c-1", "t-1") } }
    }
}
