package com.rocioplata.familyfinaiapp.ui.categories

import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag

/** Error del campo de nombre: los dos de validación y el duplicado que decide el backend (CA-09). */
enum class NameFieldError { REQUIRED, TOO_LONG, DUPLICATE }

/** Algo que la pantalla debe mostrar o hacer una sola vez y luego confirmar con `consumeEvent()`. */
sealed interface CategoryEditEvent {
    /** Un cambio falló: aviso, sin perder lo escrito (RF-21). */
    data class Failed(val failure: CategoryFailure) : CategoryEditEvent

    /** La categoría ya no existe: se avisa y se vuelve al listado, que ya se está actualizando. */
    data class Leave(val failure: CategoryFailure) : CategoryEditEvent

    /** El alta se completó: la pantalla pasa a editar la categoría creada (RF-09). */
    data class Created(val categoryId: String) : CategoryEditEvent
}

sealed interface CategoryEditUiState {
    /** Edición abierta sin que la lista de la familia esté cargada todavía. */
    data object Loading : CategoryEditUiState

    /** Edición de una categoría que ya no está en la lista de la familia. */
    data object NotFound : CategoryEditUiState

    data class Form(
        val isCreate: Boolean,
        val type: CategoryType,
        /** El tipo solo se elige al crear (RF-09); en edición se muestra y no cambia (RF-10). */
        val typeEditable: Boolean,
        /** Lo escrito, sin recortar. */
        val name: String,
        val nameError: NameFieldError?,
        /** Clave del catálogo elegida o `null` si no hay icono (RF-10, RF-11). */
        val icon: String?,
        /** Integrante, categoría archivada o rol perdido (RF-07, RF-14, RF-08): solo se consulta. */
        val readOnly: Boolean,
        val archived: Boolean,
        /** Activos por su posición y, después, los archivados (RF-19). Vacío al crear. */
        val tags: List<Tag>,
        /** Nombre o icono distintos de lo guardado: pide confirmar antes de salir (CA-24). */
        val hasUnsavedChanges: Boolean,
        val isBusy: Boolean,
        val event: CategoryEditEvent?,
    ) : CategoryEditUiState {
        val canSubmit: Boolean get() = !readOnly && !isBusy && (isCreate || hasUnsavedChanges)
    }
}
