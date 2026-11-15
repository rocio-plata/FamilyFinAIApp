package com.rocioplata.familyfinaiapp.data.categories

import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Llamadas de categorías y tags (contrato §3.4.2). [client] es el cliente autenticado: añade el Bearer
 * y renueva la sesión por su cuenta. Nunca lanza por red o servidor; relanza la cancelación.
 */
class CategoriesRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /** Todas las categorías de la familia, archivadas incluidas, con todos sus tags. */
    suspend fun categories(familyId: String): CategoryResult<List<Category>> =
        send(HttpStatusCode.OK, request = {
            client.get {
                categoriesUrl(familyId)
                parameter("includeDeprecated", true)
                applyTimeouts()
            }
        }) { dtos: List<CategoryDto> ->
            val categories = dtos.map { it.toDomain() ?: return@send CategoryResult.Failure(CategoryFailure.InvalidData) }
            CategoryResult.Success(categories)
        }

    /** El backend no devuelve los tags al crear: la categoría nace sin ellos. */
    suspend fun createCategory(
        familyId: String,
        type: CategoryType,
        name: String,
        icon: String?,
    ): CategoryResult<Category> {
        val body = buildJsonObject {
            put("type", type.name)
            put("name", name)
            if (icon != null) put("icon", icon)
        }
        return send(HttpStatusCode.Created, request = {
            client.post { categoriesUrl(familyId); jsonBody(body) }
        }) { dto: CategoryDto ->
            val category = dto.toDomain() ?: return@send CategoryResult.Failure(CategoryFailure.InvalidData)
            CategoryResult.Success(category)
        }
    }

    /** Solo se envían los campos modificados; [IconUpdate.Remove] envía `newIcon: null` explícito. */
    suspend fun updateCategory(
        familyId: String,
        categoryId: String,
        newName: String?,
        icon: IconUpdate,
    ): CategoryResult<Unit> {
        val body = buildJsonObject {
            if (newName != null) put("newName", newName)
            when (icon) {
                IconUpdate.Unchanged -> Unit
                IconUpdate.Remove -> put("newIcon", JsonNull)
                is IconUpdate.Set -> put("newIcon", icon.key)
            }
        }
        return sendDiscardingBody(HttpStatusCode.OK) {
            client.patch { categoriesUrl(familyId, categoryId); jsonBody(body) }
        }
    }

    suspend fun deprecateCategory(familyId: String, categoryId: String): CategoryResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) {
            client.post { categoriesUrl(familyId, categoryId, "deprecate"); applyTimeouts() }
        }

    suspend fun deleteCategory(familyId: String, categoryId: String): CategoryResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) {
            client.delete { categoriesUrl(familyId, categoryId); applyTimeouts() }
        }

    /** El backend devuelve el tag creado, con su id y su posición (al final de la lista). */
    suspend fun addTag(familyId: String, categoryId: String, name: String): CategoryResult<Tag> {
        val body = buildJsonObject { put("name", name) }
        return send(HttpStatusCode.Created, request = {
            client.post { categoriesUrl(familyId, categoryId, "tags"); jsonBody(body) }
        }) { dto: TagDto ->
            val tag = dto.toDomain() ?: return@send CategoryResult.Failure(CategoryFailure.InvalidData)
            CategoryResult.Success(tag)
        }
    }

    suspend fun renameTag(
        familyId: String,
        categoryId: String,
        tagId: String,
        name: String,
    ): CategoryResult<Unit> {
        val body = buildJsonObject { put("name", name) }
        return sendDiscardingBody(HttpStatusCode.OK) {
            client.patch { categoriesUrl(familyId, categoryId, "tags", tagId); jsonBody(body) }
        }
    }

    /** El backend exige todos los tags de la categoría, archivados incluidos, sin duplicados. */
    suspend fun reorderTags(
        familyId: String,
        categoryId: String,
        orderedTagIds: List<String>,
    ): CategoryResult<Unit> {
        val body = buildJsonObject {
            put("orderedTagIds", JsonArray(orderedTagIds.map(::JsonPrimitive)))
        }
        return sendDiscardingBody(HttpStatusCode.NoContent) {
            client.put { categoriesUrl(familyId, categoryId, "tags", "order"); jsonBody(body) }
        }
    }

    suspend fun deprecateTag(familyId: String, categoryId: String, tagId: String): CategoryResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) {
            client.post { categoriesUrl(familyId, categoryId, "tags", tagId, "deprecate"); applyTimeouts() }
        }

    suspend fun deleteTag(familyId: String, categoryId: String, tagId: String): CategoryResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) {
            client.delete { categoriesUrl(familyId, categoryId, "tags", tagId); applyTimeouts() }
        }

    private fun HttpRequestBuilder.jsonBody(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body)
        applyTimeouts()
    }

    private fun HttpRequestBuilder.categoriesUrl(familyId: String, vararg more: String) {
        url {
            takeFrom(baseUrl.trimEnd('/'))
            appendPathSegments("families", familyId, "categories", *more, encodeSlash = true)
        }
    }

    private fun HttpRequestBuilder.applyTimeouts() {
        timeout {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    private suspend inline fun <reified D, T> send(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
        map: (D) -> CategoryResult<T>,
    ): CategoryResult<T> {
        val response = when (val call = call(expected, request)) {
            is CategoryResult.Failure -> return call
            is CategoryResult.Success -> call.value
        }
        return try {
            map(response.body<D>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CategoryResult.Failure(CategoryFailure.InvalidData)
        }
    }

    /** Para las respuestas sin cuerpo útil (`204`) o cuyo resultado ya conoce el repositorio. */
    private suspend inline fun sendDiscardingBody(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
    ): CategoryResult<Unit> = when (val call = call(expected, request)) {
        is CategoryResult.Failure -> call
        is CategoryResult.Success -> CategoryResult.Success(Unit)
    }

    private suspend inline fun call(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
    ): CategoryResult<HttpResponse> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return CategoryResult.Failure(CategoryFailure.Network)
        } catch (e: Exception) {
            return CategoryResult.Failure(CategoryFailure.Unknown)
        }
        return if (response.status == expected) {
            CategoryResult.Success(response)
        } else {
            CategoryResult.Failure(failureOf(response))
        }
    }

    // Solo se interpreta `error`; `message` no se lee ni se registra.
    private suspend fun failureOf(response: HttpResponse): CategoryFailure {
        val code = try {
            response.body<ErrorDto>().error
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return when {
            response.status == HttpStatusCode.NotFound -> CategoryFailure.NotFound
            code in INSUFFICIENT_ROLE_CODES -> CategoryFailure.InsufficientRole
            code in DUPLICATE_NAME_CODES -> CategoryFailure.DuplicateName
            code in HAS_ASSOCIATED_ITEMS_CODES -> CategoryFailure.HasAssociatedItems
            code in CATEGORY_NOT_ACTIVE_CODES -> CategoryFailure.CategoryNotActive
            response.status == HttpStatusCode.Unauthorized -> CategoryFailure.Unauthorized
            else -> CategoryFailure.Unknown
        }
    }

    private fun CategoryDto.toDomain(): Category? {
        val categoryType = CategoryType.entries.firstOrNull { it.name == type } ?: return null
        val categoryStatus = CategoryStatus.entries.firstOrNull { it.name == status } ?: return null
        val domainTags = tags.map { it.toDomain() ?: return null }
        return Category(id, categoryType, name, icon, categoryStatus, domainTags)
    }

    private fun TagDto.toDomain(): Tag? {
        val tagStatus = TagStatus.entries.firstOrNull { it.name == status } ?: return null
        return Tag(id, name, tagStatus, displayOrder)
    }

    @Serializable
    private data class CategoryDto(
        val id: String,
        val type: String,
        val name: String,
        val icon: String? = null,
        val status: String,
        val tags: List<TagDto> = emptyList(),
    )

    @Serializable
    private data class TagDto(
        val id: String,
        val name: String,
        val status: String,
        val displayOrder: Int,
    )

    @Serializable
    private data class ErrorDto(val error: String? = null)

    private companion object {
        // El contrato (§4 y §9) documenta los códigos como `FINANCIAL.*`, pero el backend los emite como
        // `FINANCIAL_TRACKING.*` (comprobado contra el backend local y su código, ver BG-06). Se aceptan
        // los dos prefijos para no depender de cuándo se alinee la documentación.
        private fun financial(code: String) = setOf("FINANCIAL_TRACKING.$code", "FINANCIAL.$code")

        val INSUFFICIENT_ROLE_CODES = setOf("FAMILY.INSUFFICIENT_ROLE") + financial("INSUFFICIENT_ROLE")
        val DUPLICATE_NAME_CODES = financial("DUPLICATE_CATEGORY_NAME") + financial("DUPLICATE_TAG_NAME")
        val HAS_ASSOCIATED_ITEMS_CODES =
            financial("CATEGORY_HAS_ASSOCIATED_ITEMS") + financial("TAG_HAS_ASSOCIATED_ITEMS")
        val CATEGORY_NOT_ACTIVE_CODES = financial("CATEGORY_NOT_ACTIVE")
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
