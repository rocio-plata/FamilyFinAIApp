package com.rocioplata.familyfinaiapp.data.categories

import com.rocioplata.familyfinaiapp.data.cache.CachePolicy
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reglas de caché (RF-20): se muestra solo la caché del usuario actual y de menos de 7 días; otra
 * cosa se elimina. Con más de 24 h se marca como desactualizada hasta un refresco correcto.
 * El estado en memoria es por familia y el backend manda: la caché solo sirve para leer. Los cambios
 * requieren conexión (RF-21): sin ella no se llama al backend; con éxito se aplican a memoria y caché.
 */
class CategoriesRepositoryImpl(
    private val remote: CategoriesRemoteDataSource,
    private val local: CategoriesLocalDataSource,
    private val sessionProvider: SessionProvider,
    private val connectivity: ConnectivityObserver,
    private val clock: () -> Long = System::currentTimeMillis,
) : CategoriesRepository {
    /** `savedAtMillis` es el de la última descarga completa, no el de un cambio posterior. */
    private data class Entry(val categories: List<Category>, val savedAtMillis: Long)

    private val state = MutableStateFlow<Map<String, Entry>>(emptyMap())

    // Protege solo las escrituras de caché y estado (nunca la red). `clear()` lo usa sin esperar a un
    // refresco en curso, que puede estar esperando la renovación de la sesión, que a su vez termina la
    // sesión y llama a `clear()` (interbloqueo si compartieran candado, ver HeaderRepositoryImpl).
    private val storageLock = Mutex()
    private var clearEpoch = 0

    private val refreshLocks = mutableMapOf<String, Mutex>()
    private val lastRefreshResults = mutableMapOf<String, CategoryResult<Unit>>()

    override fun categories(familyId: String): Flow<CategoriesData> =
        state
            .map { entries ->
                val entry = entries[familyId] ?: return@map CategoriesData()
                CategoriesData(entry.categories, CachePolicy.isStale(entry.savedAtMillis, clock()))
            }
            .onStart {
                val current = session() as? CategoryResult.Success ?: return@onStart
                storageLock.withLock { loadCache(familyId, current.value.userId) }
            }

    /** Un refresco a la vez por familia: una llamada simultánea espera y comparte su resultado. */
    override suspend fun refresh(familyId: String): CategoryResult<Unit> {
        val lock = synchronized(refreshLocks) { refreshLocks.getOrPut(familyId) { Mutex() } }
        if (!lock.tryLock()) {
            lock.withLock { }
            val shared = synchronized(lastRefreshResults) { lastRefreshResults[familyId] }
            // Sin resultado compartido (el primero se canceló): se hace el refresco propio.
            return shared ?: refresh(familyId)
        }
        try {
            synchronized(lastRefreshResults) { lastRefreshResults.remove(familyId) }
            val result = fetch(familyId)
            synchronized(lastRefreshResults) { lastRefreshResults[familyId] = result }
            return result
        } finally {
            lock.unlock()
        }
    }

    // No espera a un refresco en curso: subir `clearEpoch` hace que ese refresco descarte lo que
    // obtenga en vez de guardarlo tras el borrado.
    override suspend fun clear() = storageLock.withLock {
        clearEpoch++
        local.clear()
        state.value = emptyMap()
    }

    private suspend fun fetch(familyId: String): CategoryResult<Unit> {
        val session = when (val current = session()) {
            is CategoryResult.Failure -> return current
            is CategoryResult.Success -> current.value
        }
        val epoch = storageLock.withLock {
            loadCache(familyId, session.userId)
            clearEpoch
        }
        val remoteResult = remote.categories(familyId)
        if (remoteResult is CategoryResult.Failure) return remoteResult
        val categories = (remoteResult as CategoryResult.Success).value
        val now = clock()
        storageLock.withLock {
            // Si se borró la caché mientras se esperaba la red, no se guarda nada.
            if (epoch != clearEpoch) return@withLock
            local.save(familyId, session.userId, now, categories)
            state.update { it + (familyId to Entry(categories, now)) }
        }
        return CategoryResult.Success(Unit)
    }

    private suspend fun session(): CategoryResult<Session> = try {
        CategoryResult.Success(sessionProvider.current())
    } catch (e: SessionUnavailableException) {
        CategoryResult.Failure(
            when (e.failure) {
                SessionFailure.NOT_SIGNED_IN -> CategoryFailure.Unauthorized
                SessionFailure.UNKNOWN -> CategoryFailure.Unknown
            },
        )
    }

    /** Carga la caché de la familia a memoria; descarta la de otro usuario o la de 7 días o más. Requiere `storageLock`. */
    private suspend fun loadCache(familyId: String, userId: String) {
        val cached = local.read(familyId)
        when {
            cached == null -> state.update { it - familyId }
            cached.userId != userId -> {
                local.clear()
                state.value = emptyMap()
            }
            CachePolicy.isExpired(cached.savedAtMillis, clock()) -> {
                local.remove(familyId)
                state.update { it - familyId }
            }
            else -> state.update { it + (familyId to Entry(cached.value, cached.savedAtMillis)) }
        }
    }

    override suspend fun createCategory(
        familyId: String,
        type: CategoryType,
        name: String,
        icon: String?,
    ): CategoryResult<Category> = change(
        familyId,
        request = { remote.createCategory(familyId, type, name, icon) },
        apply = { categories, created -> categories + created },
    )

    override suspend fun updateCategory(
        familyId: String,
        categoryId: String,
        newName: String?,
        icon: IconUpdate,
    ): CategoryResult<Unit> = change(
        familyId,
        request = { remote.updateCategory(familyId, categoryId, newName, icon) },
        apply = { categories, _ ->
            categories.updateCategory(categoryId) {
                it.copy(
                    name = newName ?: it.name,
                    icon = when (icon) {
                        IconUpdate.Unchanged -> it.icon
                        IconUpdate.Remove -> null
                        is IconUpdate.Set -> icon.key
                    },
                )
            }
        },
    )

    override suspend fun deprecateCategory(familyId: String, categoryId: String): CategoryResult<Unit> = change(
        familyId,
        request = { remote.deprecateCategory(familyId, categoryId) },
        apply = { categories, _ ->
            categories.updateCategory(categoryId) { it.copy(status = CategoryStatus.DEPRECATED) }
        },
    )

    override suspend fun deleteCategory(familyId: String, categoryId: String): CategoryResult<Unit> = change(
        familyId,
        request = { remote.deleteCategory(familyId, categoryId) },
        apply = { categories, _ -> categories.filterNot { it.id == categoryId } },
    )

    override suspend fun addTag(familyId: String, categoryId: String, name: String): CategoryResult<Unit> =
        change(
            familyId,
            request = { remote.addTag(familyId, categoryId, name) },
            apply = { categories, created ->
                categories.updateCategory(categoryId) { it.copy(tags = it.tags + created) }
            },
        ).map { }

    override suspend fun renameTag(
        familyId: String,
        categoryId: String,
        tagId: String,
        name: String,
    ): CategoryResult<Unit> = change(
        familyId,
        request = { remote.renameTag(familyId, categoryId, tagId, name) },
        apply = { categories, _ -> categories.updateTag(categoryId, tagId) { it.copy(name = name) } },
    )

    override suspend fun reorderTags(
        familyId: String,
        categoryId: String,
        activeTagIdsInOrder: List<String>,
    ): CategoryResult<Unit> = change(
        familyId,
        request = {
            // El backend exige todos los tags de la categoría: los activos en el nuevo orden y, después,
            // los archivados en su orden actual.
            val category = state.value[familyId]?.categories?.firstOrNull { it.id == categoryId }
                ?: return@change CategoryResult.Failure(CategoryFailure.NotFound)
            val archived = category.tags
                .filter { it.status == TagStatus.DEPRECATED }
                .sortedBy { it.displayOrder }
                .map { it.id }
            val sentOrder = activeTagIdsInOrder + archived
            remote.reorderTags(familyId, categoryId, sentOrder).map { sentOrder }
        },
        apply = { categories, sentOrder ->
            categories.updateCategory(categoryId) { category ->
                val byId = category.tags.associateBy { it.id }
                category.copy(tags = sentOrder.mapIndexedNotNull { index, id -> byId[id]?.copy(displayOrder = index) })
            }
        },
    ).map { }

    override suspend fun deprecateTag(familyId: String, categoryId: String, tagId: String): CategoryResult<Unit> =
        change(
            familyId,
            request = { remote.deprecateTag(familyId, categoryId, tagId) },
            apply = { categories, _ ->
                categories.updateTag(categoryId, tagId) { it.copy(status = TagStatus.DEPRECATED) }
            },
        )

    override suspend fun deleteTag(familyId: String, categoryId: String, tagId: String): CategoryResult<Unit> =
        change(
            familyId,
            request = { remote.deleteTag(familyId, categoryId, tagId) },
            apply = { categories, _ ->
                categories.updateCategory(categoryId) { category ->
                    category.copy(tags = category.tags.filterNot { it.id == tagId })
                }
            },
        )

    /**
     * Ejecuta un cambio: sin conexión no llama al backend (RF-21); con éxito aplica [apply] a la lista
     * en memoria y en caché, conservando el `savedAt` de la última descarga completa. Un fallo no
     * cambia nada. Si no hay datos cargados de la familia solo se devuelve el resultado.
     */
    private suspend fun <T> change(
        familyId: String,
        request: suspend () -> CategoryResult<T>,
        apply: (List<Category>, T) -> List<Category>,
    ): CategoryResult<T> {
        if (!connectivity.isOnline.first()) return CategoryResult.Failure(CategoryFailure.Offline)
        val session = when (val current = session()) {
            is CategoryResult.Failure -> return current
            is CategoryResult.Success -> current.value
        }
        val epoch = storageLock.withLock { clearEpoch }
        val result = request()
        if (result !is CategoryResult.Success) return result
        storageLock.withLock {
            // Si se borró la caché mientras se esperaba la red, no se guarda nada.
            if (epoch != clearEpoch) return@withLock
            val entry = state.value[familyId] ?: return@withLock
            val updated = Entry(apply(entry.categories, result.value), entry.savedAtMillis)
            local.save(familyId, session.userId, updated.savedAtMillis, updated.categories)
            state.update { it + (familyId to updated) }
        }
        return result
    }

    private fun List<Category>.updateCategory(id: String, change: (Category) -> Category) =
        map { if (it.id == id) change(it) else it }

    private fun List<Category>.updateTag(categoryId: String, tagId: String, change: (Tag) -> Tag) =
        updateCategory(categoryId) { category ->
            category.copy(tags = category.tags.map { if (it.id == tagId) change(it) else it })
        }

    private fun <T, R> CategoryResult<T>.map(transform: (T) -> R): CategoryResult<R> = when (this) {
        is CategoryResult.Failure -> this
        is CategoryResult.Success -> CategoryResult.Success(transform(value))
    }
}
