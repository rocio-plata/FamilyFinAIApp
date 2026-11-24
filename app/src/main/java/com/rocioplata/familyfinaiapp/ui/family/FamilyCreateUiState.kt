package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure

/** Qué falla junto al campo del nombre al crear una familia (RF-04); el texto escrito se conserva siempre (RF-25). */
sealed interface FamilyCreateError {
    data object Required : FamilyCreateError
    data object TooLong : FamilyCreateError
    data object Emoji : FamilyCreateError

    /** Ya perteneces a una familia con ese nombre. */
    data object Duplicate : FamilyCreateError

    /** El backend o la conexión no dejaron crearla. */
    data class Failure(val failure: FamilyAccessFailure) : FamilyCreateError
}

/**
 * Formulario de «Crear nueva familia» (RF-04, RF-05). Vive en el ViewModel y sobrevive a la recreación de la pantalla
 * (CA-31).
 */
data class FamilyCreateUiState(
    val text: String = "",
    val error: FamilyCreateError? = null,
    /** Hay una creación en curso: no se admite otra ni cerrar el formulario. */
    val isSaving: Boolean = false,
    /** Se pidió salir con el nombre escrito: se muestra la confirmación de descarte. */
    val confirmingDiscard: Boolean = false,
) {
    /** Hay algo escrito que se perdería al salir. */
    val hasChanges: Boolean get() = text.isNotBlank()
}
