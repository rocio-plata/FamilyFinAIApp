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
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.categories.NameError
import com.rocioplata.familyfinaiapp.domain.categories.NameValidation
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Alta ([categoryId] nulo) y edición de una categoría (RF-07 a RF-10, RF-14). El borrador de nombre,
 * icono y tipo vive aquí y se descarta con el ViewModel; la categoría y sus tags salen del mismo flujo
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
    private data class Draft(val name: String, val icon: String?, val type: CategoryType)

    private val isCreate = categoryId == null
    private val canManage = MutableStateFlow(initialCanManage)
    private val draft = MutableStateFlow<Draft?>(if (isCreate) Draft("", null, initialType) else null)
    private val nameError = MutableStateFlow<NameFieldError?>(null)
    private val busy = MutableStateFlow(false)
    private val event = MutableStateFlow<CategoryEditEvent?>(null)

    // Una sola colección del flujo del repositorio, compartida por el estado y por las comprobaciones.
    private val categories: StateFlow<List<Category>?> = repository.categories(familyId)
        .map { it.categories }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<CategoryEditUiState> = combine(
        categories,
        draft,
        nameError,
        canManage,
        combine(busy, event) { isBusy, pending -> isBusy to pending },
    ) { list, draft, nameError, canManage, (isBusy, pending) ->
        buildState(list?.firstOrNull { it.id == categoryId }, list != null, draft, nameError, canManage, isBusy, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoryEditUiState.Loading)

    init {
        if (!isCreate) {
            // El borrador de una edición parte de lo guardado cuando la categoría está disponible.
            viewModelScope.launch {
                val category = categories.first { list -> list?.any { it.id == categoryId } == true }
                    ?.first { it.id == categoryId }
                if (category != null && draft.value == null) draft.value = Draft(category.name, category.icon, category.type)
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
                operation = { repository.createCategory(familyId, current.type, name, current.icon) },
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
        if (newName == null && icon == IconUpdate.Unchanged) return
        run(
            operation = { repository.updateCategory(familyId, categoryId, newName, icon) },
            // El campo queda con el nombre ya recortado que se guardó.
            onSuccess = { draft.value = draft.value?.copy(name = name) },
        )
    }

    /** La pantalla ya mostró o gestionó el evento pendiente. */
    fun consumeEvent() {
        event.value = null
    }

    private fun <T> run(operation: suspend () -> CategoryResult<T>, onSuccess: (T) -> Unit) {
        // El control síncrono evita que dos pulsaciones seguidas envíen dos operaciones (CA-21).
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is CategoryResult.Success -> onSuccess(result.value)
                    is CategoryResult.Failure -> handle(result.failure)
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
        listLoaded: Boolean,
        draft: Draft?,
        nameError: NameFieldError?,
        canManage: Boolean,
        isBusy: Boolean,
        pending: CategoryEditEvent?,
    ): CategoryEditUiState {
        if (!isCreate) {
            if (!listLoaded) return CategoryEditUiState.Loading
            if (category == null) return CategoryEditUiState.NotFound
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
            tags = category?.tags.orEmpty().sortedWith(compareBy({ it.status != TagStatus.ACTIVE }, { it.displayOrder })),
            hasUnsavedChanges = !readOnly && hasChanges(category, draft),
            isBusy = isBusy,
            event = pending,
        )
    }

    /** Alta: algo escrito o un icono elegido. Edición: nombre (recortado) o icono distintos de lo guardado. */
    private fun hasChanges(category: Category?, draft: Draft): Boolean =
        if (category == null) {
            draft.name.trim().isNotEmpty() || draft.icon != null
        } else {
            draft.name.trim() != category.name || draft.icon != category.icon
        }

    private fun NameError.toFieldError() = when (this) {
        NameError.REQUIRED -> NameFieldError.REQUIRED
        NameError.TOO_LONG -> NameFieldError.TOO_LONG
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
