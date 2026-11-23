package com.rocioplata.familyfinaiapp.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.CategoryValidation
import com.rocioplata.familyfinaiapp.domain.categories.ColorUpdate
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.categories.NameError
import com.rocioplata.familyfinaiapp.domain.categories.NameValidation
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Alta ([categoryId] nulo) y edición de una categoría (RF-07 a RF-10, RF-14; color en RF-15 de la 008). El
 * borrador de nombre, icono, color y tipo vive aquí y se descarta con el ViewModel; la categoría y sus tags salen del mismo flujo
 * del repositorio que el listado, así que lo guardado se refleja sin recargar. Se crea con la clave
 * `sesión-familia-categoría` (o «nueva»). Las operaciones van de una en una (RF-21, CA-21).
 */
class CategoryEditViewModel(
    private val repository: CategoriesRepository,
    private val familyId: String,
    private val categoryId: String?,
    initialType: CategoryType,
    initialCanManage: Boolean,
    private val onInsufficientRole: () -> Unit,
) : ViewModel() {
    private data class Draft(val name: String, val icon: String?, val type: CategoryType, val color: String?)

    private val isCreate = categoryId == null
    private val canManage = MutableStateFlow(initialCanManage)
    private val draft = MutableStateFlow<Draft?>(if (isCreate) Draft("", null, initialType, null) else null)
    private val nameError = MutableStateFlow<NameFieldError?>(null)
    private val busy = MutableStateFlow(false)
    private val event = MutableStateFlow<CategoryEditEvent?>(null)
    private val finished = MutableStateFlow(false)
    private val tagUi = MutableStateFlow(TagUi())

    // Mientras se elimina, la categoría desaparece de la lista antes de que llegue la respuesta: no es «no encontrada».
    private val deleting = MutableStateFlow(false)

    // Una sola colección del flujo del repositorio, compartida por el estado y por las comprobaciones.
    private val categories: StateFlow<List<Category>?> = repository.categories(familyId)
        .map { it.categories }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<CategoryEditUiState> = combine(
        categories,
        draft,
        nameError,
        canManage,
        combine(busy, event, finished, deleting, tagUi) { isBusy, pending, done, isDeleting, tags ->
            Ops(isBusy, pending, done, isDeleting, tags)
        },
    ) { list, draft, nameError, canManage, ops ->
        buildState(list?.firstOrNull { it.id == categoryId }, list, draft, nameError, canManage, ops)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoryEditUiState.Loading)

    init {
        if (!isCreate) {
            // El borrador de una edición parte de lo guardado cuando la categoría está disponible.
            viewModelScope.launch {
                val category = categories.first { list -> list?.any { it.id == categoryId } == true }
                    ?.first { it.id == categoryId }
                if (category != null && draft.value == null) {
                    draft.value = Draft(category.name, category.icon, category.type, category.color)
                }
            }
        }
    }

    /** El rol puede cambiar con la pantalla abierta (RF-08): la pantalla pasa sola a solo lectura. */
    fun setCanManage(value: Boolean) {
        canManage.value = value
    }

    fun setName(text: String) {
        if (isReadOnly()) return
        val current = draft.value ?: return
        draft.value = current.copy(name = text.take(CategoryValidation.MAX_CATEGORY_NAME_LENGTH))
        nameError.value = null
    }

    fun setIcon(key: String?) {
        if (isReadOnly()) return
        val current = draft.value ?: return
        draft.value = current.copy(icon = key)
    }

    /**
     * Elige el color (RF-15 de la 008) o, con `null`, lo quita. Solo admite claves del catálogo: una clave
     * desconocida se ignora, porque el selector nunca la ofrece.
     */
    fun setColor(key: String?) {
        if (isReadOnly()) return
        if (key != null && CategoryColors.find(key) == null) return
        val current = draft.value ?: return
        draft.value = current.copy(color = key)
    }

    /** Solo al crear (RF-09): en edición el tipo no cambia. */
    fun setType(type: CategoryType) {
        if (!isCreate || isReadOnly()) return
        val current = draft.value ?: return
        draft.value = current.copy(type = type)
    }

    /** «Crear» (alta) o «Guardar» (edición). Una sola operación a la vez; validación antes de enviar. */
    fun submit() {
        val current = draft.value ?: return
        if (isReadOnly()) return
        val name = when (val validation = CategoryValidation.validateCategoryName(current.name)) {
            is NameValidation.Invalid -> {
                nameError.value = validation.error.toFieldError()
                return
            }
            is NameValidation.Valid -> validation.name
        }
        if (categoryId == null) {
            run(
                operation = { repository.createCategory(familyId, current.type, name, current.icon, current.color) },
                onSuccess = { created -> event.value = CategoryEditEvent.Created(created.id) },
            )
            return
        }
        val saved = currentCategory() ?: return
        val newName = name.takeIf { it != saved.name }
        val icon = when {
            current.icon == saved.icon -> IconUpdate.Unchanged
            current.icon == null -> IconUpdate.Remove
            else -> IconUpdate.Set(current.icon)
        }
        val color = when {
            current.color == saved.color -> ColorUpdate.Unchanged
            current.color == null -> ColorUpdate.Remove
            else -> ColorUpdate.Set(current.color)
        }
        if (newName == null && icon == IconUpdate.Unchanged && color == ColorUpdate.Unchanged) return
        run(
            operation = { repository.updateCategory(familyId, categoryId, newName, icon, color) },
            // El campo queda con el nombre ya recortado que se guardó.
            onSuccess = { draft.value = draft.value?.copy(name = name) },
        )
    }

    /** Archivar la categoría activa, ya confirmado por la persona (RF-12): es irreversible (BG-01). */
    fun archive() {
        val id = categoryId ?: return
        if (isReadOnly()) return
        run(
            operation = { repository.deprecateCategory(familyId, id) },
            onSuccess = { finished.value = true },
        )
    }

    /** Eliminar la categoría activa, ya confirmado (RF-13). Con movimientos asociados se ofrece archivarla. */
    fun delete() {
        val id = categoryId ?: return
        if (isReadOnly()) return
        run(
            operation = {
                deleting.value = true
                repository.deleteCategory(familyId, id).also { if (it !is CategoryResult.Success) deleting.value = false }
            },
            onSuccess = { finished.value = true },
        )
    }

    // --- Tags (RF-15 a RF-19): cada cambio se guarda al instante ---

    fun setNewTagName(text: String) {
        if (categoryId == null || isReadOnly()) return
        tagUi.update { it.copy(newName = text.take(CategoryValidation.MAX_TAG_NAME_LENGTH), error = null) }
    }

    /** Añade el tag escrito al final de la lista (RF-15). El campo solo se vacía si se guarda. */
    fun addTag() {
        val id = categoryId ?: return
        if (isReadOnly()) return
        val name = when (val validation = CategoryValidation.validateTagName(tagUi.value.newName)) {
            is NameValidation.Invalid -> {
                tagUi.update { it.copy(error = validation.error.toFieldError()) }
                return
            }
            is NameValidation.Valid -> validation.name
        }
        run(
            operation = { repository.addTag(familyId, id, name) },
            onSuccess = { tagUi.update { it.copy(newName = "") } },
            onFailure = { failure ->
                if (failure == CategoryFailure.DuplicateName) {
                    tagUi.update { it.copy(error = NameFieldError.DUPLICATE) }
                    true
                } else {
                    handleTagNotFound(failure)
                }
            },
        )
    }

    /** Abre el diálogo de renombrar un tag activo (RF-16); los archivados son de solo lectura (RF-19). */
    fun startRename(tagId: String) {
        if (categoryId == null || isReadOnly()) return
        val tag = currentCategory()?.tags?.firstOrNull { it.id == tagId } ?: return
        if (tag.status != TagStatus.ACTIVE) return
        tagUi.update { it.copy(renaming = TagRenameDraft(tagId, tag.name, null)) }
    }

    fun setRenameText(text: String) {
        tagUi.update { ui ->
            ui.renaming?.let { ui.copy(renaming = it.copy(name = text.take(CategoryValidation.MAX_TAG_NAME_LENGTH), error = null)) } ?: ui
        }
    }

    fun cancelRename() {
        tagUi.update { it.copy(renaming = null) }
    }

    /** Guarda el nuevo nombre (RF-16). Sin cambios reales no llama al backend y cierra el diálogo. */
    fun confirmRename() {
        val id = categoryId ?: return
        val draft = tagUi.value.renaming ?: return
        if (isReadOnly()) return
        val name = when (val validation = CategoryValidation.validateTagName(draft.name)) {
            is NameValidation.Invalid -> {
                tagUi.update { it.copy(renaming = draft.copy(error = validation.error.toFieldError())) }
                return
            }
            is NameValidation.Valid -> validation.name
        }
        if (currentCategory()?.tags?.firstOrNull { it.id == draft.tagId }?.name == name) {
            tagUi.update { it.copy(renaming = null) }
            return
        }
        run(
            operation = { repository.renameTag(familyId, id, draft.tagId, name) },
            onSuccess = { tagUi.update { it.copy(renaming = null) } },
            onFailure = { failure ->
                if (failure == CategoryFailure.DuplicateName) {
                    tagUi.update { ui -> ui.copy(renaming = ui.renaming?.copy(error = NameFieldError.DUPLICATE)) }
                    true
                } else {
                    handleTagNotFound(failure)
                }
            },
        )
    }

    /**
     * Quitar un tag, ya confirmado (RF-18, RF-19). Con movimientos asociados: un tag activo ofrece archivarse
     * y uno archivado solo informa.
     */
    fun removeTag(tagId: String) {
        val id = categoryId ?: return
        if (isReadOnly()) return
        val status = currentCategory()?.tags?.firstOrNull { it.id == tagId }?.status ?: return
        run(
            operation = { repository.deleteTag(familyId, id, tagId) },
            onSuccess = {},
            onFailure = { failure ->
                if (failure == CategoryFailure.HasAssociatedItems) {
                    event.value =
                        if (status == TagStatus.ACTIVE) CategoryEditEvent.OfferArchiveTag(tagId) else CategoryEditEvent.TagCannotDelete
                    true
                } else {
                    handleTagNotFound(failure)
                }
            },
        )
    }

    /** Archivar un tag activo, ya confirmado (irreversible, BG-01), p. ej. al aceptar la oferta de [removeTag]. */
    fun archiveTag(tagId: String) {
        val id = categoryId ?: return
        if (isReadOnly()) return
        if (currentCategory()?.tags?.firstOrNull { it.id == tagId }?.status != TagStatus.ACTIVE) return
        run(
            operation = { repository.deprecateTag(familyId, id, tagId) },
            onSuccess = {},
            onFailure = { handleTagNotFound(it) },
        )
    }

    // --- Reordenar tags (RF-17) ---

    /** Sube un tag activo una posición. Es la alternativa sin arrastre, utilizable con lector de pantalla. */
    fun moveTagUp(tagId: String) {
        val index = activeTagIds().indexOf(tagId)
        if (index > 0) moveTag(tagId, index - 1)
    }

    /** Baja un tag activo una posición (alternativa sin arrastre). */
    fun moveTagDown(tagId: String) {
        val index = activeTagIds().indexOf(tagId)
        if (index in 0 until activeTagIds().lastIndex) moveTag(tagId, index + 1)
    }

    /**
     * Mueve un tag activo a [toIndex] entre los activos (se ajusta a los límites) y guarda el orden: es lo que
     * se llama al soltar el arrastre. Los archivados no se mueven. El nuevo orden se muestra enseguida
     * (cambio optimista); si el backend lo rechaza se restaura el anterior, se informa y se actualiza la lista,
     * por si otro miembro cambió los tags.
     */
    fun moveTag(tagId: String, toIndex: Int) {
        val id = categoryId ?: return
        if (isReadOnly() || busy.value) return
        val active = activeTagIds()
        val from = active.indexOf(tagId)
        if (from < 0) return
        val target = toIndex.coerceIn(0, active.lastIndex)
        if (target == from) return
        val newOrder = active.toMutableList().apply {
            removeAt(from)
            add(target, tagId)
        }
        tagUi.update { it.copy(pendingOrder = newOrder) }
        run(
            operation = {
                repository.reorderTags(familyId, id, newOrder).also { result ->
                    // Si falla, se restaura el orden anterior sea cual sea el motivo.
                    if (result is CategoryResult.Failure) tagUi.update { it.copy(pendingOrder = null) }
                }
            },
            onSuccess = { tagUi.update { it.copy(pendingOrder = null) } },
            onFailure = { failure ->
                when (failure) {
                    CategoryFailure.Offline,
                    CategoryFailure.InsufficientRole,
                    CategoryFailure.CategoryNotActive,
                    CategoryFailure.Unauthorized,
                    -> false
                    CategoryFailure.NotFound -> handleTagNotFound(failure)
                    // Incluye el rechazo del orden (INVALID_TAG_ORDER), que no se distingue (plan). Se actualiza
                    // la lista por si otro miembro cambió los tags. El resto de casos ya la actualizan o no
                    // llegaron a enviar nada (sin conexión).
                    else -> {
                        event.value = CategoryEditEvent.ReorderFailed
                        refreshList()
                        true
                    }
                }
            },
        )
    }

    /** Ids de los tags activos en el orden guardado (el que devuelve el repositorio). */
    private fun activeTagIds(): List<String> =
        currentCategory()?.tags.orEmpty().filter { it.status == TagStatus.ACTIVE }.sortedBy { it.displayOrder }.map { it.id }

    /** Activos por su posición (o por el orden pendiente, si lo hay) y después los archivados. */
    private fun orderedTags(category: Category?, pending: List<String>?): List<Tag> {
        val tags = category?.tags.orEmpty()
        val active = tags.filter { it.status == TagStatus.ACTIVE }.sortedBy { it.displayOrder }
        val orderedActive = if (pending == null) {
            active
        } else {
            val byId = active.associateBy { it.id }
            pending.mapNotNull { byId[it] } + active.filter { it.id !in pending }
        }
        return orderedActive + tags.filter { it.status != TagStatus.ACTIVE }.sortedBy { it.displayOrder }
    }

    /**
     * El backend responde 404 tanto si falta el tag como si falta la categoría. Se actualiza la lista y se
     * avisa del tag; si en realidad faltaba la categoría, el estado pasa solo a «no encontrada» (la pantalla
     * lo muestra y vuelve al listado), sin necesidad de distinguirlo aquí.
     */
    private suspend fun handleTagNotFound(failure: CategoryFailure): Boolean {
        if (failure != CategoryFailure.NotFound) return false
        repository.refresh(familyId)
        tagUi.update { it.copy(renaming = null) }
        event.value = CategoryEditEvent.TagNotFound
        return true
    }

    /** La pantalla ya mostró o gestionó el evento pendiente. */
    fun consumeEvent() {
        event.value = null
    }

    /**
     * Ejecuta una operación de una en una. [onFailure] puede resolver un fallo propio de la operación
     * (devuelve `true`); si no, se aplica el tratamiento común de [handle].
     */
    private fun <T> run(
        operation: suspend () -> CategoryResult<T>,
        onSuccess: (T) -> Unit,
        onFailure: suspend (CategoryFailure) -> Boolean = { false },
    ) {
        // El control síncrono evita que dos pulsaciones seguidas envíen dos operaciones (CA-21).
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is CategoryResult.Success -> onSuccess(result.value)
                    is CategoryResult.Failure -> if (!onFailure(result.failure)) handle(result.failure)
                }
            } finally {
                busy.value = false
            }
        }
    }

    private fun handle(failure: CategoryFailure) {
        when (failure) {
            CategoryFailure.DuplicateName -> nameError.value = NameFieldError.DUPLICATE
            CategoryFailure.InsufficientRole -> {
                event.value = CategoryEditEvent.Failed(failure)
                onInsufficientRole()
            }
            CategoryFailure.NotFound -> {
                event.value = CategoryEditEvent.Leave(failure)
                refreshList()
            }
            CategoryFailure.CategoryNotActive -> {
                event.value = CategoryEditEvent.Failed(failure)
                refreshList()
            }
            CategoryFailure.HasAssociatedItems -> event.value = CategoryEditEvent.OfferArchive
            // Lo resuelve la sesión (003): no hay mensaje propio.
            CategoryFailure.Unauthorized -> Unit
            else -> event.value = CategoryEditEvent.Failed(failure)
        }
    }

    private fun refreshList() {
        viewModelScope.launch { repository.refresh(familyId) }
    }

    private fun currentCategory(): Category? = categories.value?.firstOrNull { it.id == categoryId }

    private fun isReadOnly(): Boolean =
        !canManage.value || (currentCategory()?.status == CategoryStatus.DEPRECATED)

    private fun buildState(
        category: Category?,
        list: List<Category>?,
        draft: Draft?,
        nameError: NameFieldError?,
        canManage: Boolean,
        ops: Ops,
    ): CategoryEditUiState {
        if (ops.finished) return CategoryEditUiState.Finished
        if (!isCreate) {
            if (list == null) return CategoryEditUiState.Loading
            if (category == null) return if (ops.deleting) CategoryEditUiState.Loading else CategoryEditUiState.NotFound
        }
        draft ?: return CategoryEditUiState.Loading
        val archived = category?.status == CategoryStatus.DEPRECATED
        val readOnly = !canManage || archived
        val type = category?.type ?: draft.type
        return CategoryEditUiState.Form(
            isCreate = isCreate,
            type = type,
            typeEditable = isCreate && !readOnly,
            name = draft.name,
            nameError = nameError,
            icon = draft.icon,
            readOnly = readOnly,
            archived = archived,
            tags = orderedTags(category, ops.tags.pendingOrder),
            hasUnsavedChanges = !readOnly && hasChanges(category, draft),
            isBusy = ops.busy,
            event = ops.event,
            newTagName = ops.tags.newName,
            tagError = ops.tags.error,
            renaming = ops.tags.renaming,
            color = draft.color,
            effectiveColor = effectiveColor(category, list, draft),
        )
    }

    /**
     * Color que se dibuja en la vista previa: el elegido o, sin elegir (o con una clave que la app no conoce), el
     * de respaldo de su posición. Una categoría nueva ocupará el último lugar entre las de su tipo.
     */
    private fun effectiveColor(category: Category?, list: List<Category>?, draft: Draft): CategoryColorOption {
        val position = if (category != null) {
            CategoryColors.positionOf(category, list.orEmpty())
        } else {
            list.orEmpty().count { it.type == draft.type }
        }
        return CategoryColors.resolve(draft.color, position)
    }

    /** Alta: algo escrito o un icono o color elegido. Edición: nombre (recortado), icono o color distintos de lo guardado. */
    private fun hasChanges(category: Category?, draft: Draft): Boolean =
        if (category == null) {
            draft.name.trim().isNotEmpty() || draft.icon != null || draft.color != null
        } else {
            draft.name.trim() != category.name || draft.icon != category.icon || draft.color != category.color
        }

    private fun NameError.toFieldError() = when (this) {
        NameError.REQUIRED -> NameFieldError.REQUIRED
        NameError.TOO_LONG -> NameFieldError.TOO_LONG
    }

    /** [pendingOrder] es el orden de los tags activos propuesto y aún sin confirmar por el backend (cambio optimista). */
    private data class TagUi(
        val newName: String = "",
        val error: NameFieldError? = null,
        val renaming: TagRenameDraft? = null,
        val pendingOrder: List<String>? = null,
    )

    private data class Ops(
        val busy: Boolean,
        val event: CategoryEditEvent?,
        val finished: Boolean,
        val deleting: Boolean,
        val tags: TagUi,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
