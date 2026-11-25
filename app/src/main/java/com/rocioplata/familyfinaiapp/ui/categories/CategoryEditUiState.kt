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

    /**
     * Eliminar falló porque la categoría activa tiene movimientos asociados: se ofrece archivarla en su
     * lugar (RF-13). Aceptar llama a `archive()`; rechazar solo confirma el evento.
     */
    data object OfferArchive : CategoryEditEvent

    /**
     * Quitar un tag activo falló por movimientos asociados: se ofrece archivarlo en su lugar (RF-18).
     * Aceptar llama a `archiveTag(tagId)`; rechazar solo confirma el evento.
     */
    data class OfferArchiveTag(val tagId: String) : CategoryEditEvent

    /** Quitar un tag archivado falló por movimientos asociados: solo se informa (RF-19). */
    data object TagCannotDelete : CategoryEditEvent

    /** El tag ya no existe (otro miembro lo quitó): aviso; la lista ya se está actualizando. */
    data object TagNotFound : CategoryEditEvent

    /** No se pudo guardar el nuevo orden de los tags: se restauró el anterior (RF-17). */
    data object ReorderFailed : CategoryEditEvent

    /** «Tag reactivado» (RF-04 de la 010). */
    data object TagReactivated : CategoryEditEvent

    /** Reactivado, pero no se pudo llevar al final de los activos (RF-11 de la 010). */
    data object TagReactivatedNotMoved : CategoryEditEvent
}

/** Renombrando un tag (RF-16): el texto y el error del diálogo viven aquí para sobrevivir a la recreación. */
data class TagRenameDraft(val tagId: String, val name: String, val error: NameFieldError?)

sealed interface CategoryEditUiState {
    /** Edición abierta sin que la lista de la familia esté cargada todavía. */
    data object Loading : CategoryEditUiState

    /** Edición de una categoría que ya no está en la lista de la familia. */
    data object NotFound : CategoryEditUiState

    /** La categoría se archivó o eliminó: la pantalla vuelve al listado, ya actualizado (RF-12, RF-13). */
    data object Finished : CategoryEditUiState

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
        /** Nombre, icono o color distintos de lo guardado: pide confirmar antes de salir (CA-24). */
        val hasUnsavedChanges: Boolean,
        val isBusy: Boolean,
        val event: CategoryEditEvent?,
        /** Lo escrito en el campo para añadir un tag (RF-15); solo se vacía si el alta tiene éxito. */
        val newTagName: String = "",
        val tagError: NameFieldError? = null,
        val renaming: TagRenameDraft? = null,
        /** Clave del color elegido (o la guardada) o `null` si no hay; puede ser una que la app no conoce (RF-15 de la 008). */
        val color: String? = null,
        /** Color a dibujar en la vista previa: el elegido o el de respaldo por posición (RF-14 de la 008). */
        val effectiveColor: CategoryColorOption = CategoryColors.fallback(0),
    ) : CategoryEditUiState {
        val canSubmit: Boolean get() = !readOnly && !isBusy && (isCreate || hasUnsavedChanges)
    }
}
