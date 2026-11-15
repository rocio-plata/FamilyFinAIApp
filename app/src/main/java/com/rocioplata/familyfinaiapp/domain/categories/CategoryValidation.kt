package com.rocioplata.familyfinaiapp.domain.categories

enum class NameError { REQUIRED, TOO_LONG }

/** Resultado de validar un nombre: el valor ya recortado, que es el que se envía y guarda, o el error. */
sealed interface NameValidation {
    data class Valid(val name: String) : NameValidation
    data class Invalid(val error: NameError) : NameValidation
}

/**
 * Reglas de nombre de categoría y de tag (RF-09, RF-10, RF-15, RF-16). Los espacios del inicio y del
 * final se eliminan antes de validar; las longitudes cuentan unidades UTF-16, como `length` en el backend.
 */
object CategoryValidation {
    const val MAX_CATEGORY_NAME_LENGTH = 50
    const val MAX_TAG_NAME_LENGTH = 30

    fun validateCategoryName(name: String): NameValidation = validate(name, MAX_CATEGORY_NAME_LENGTH)

    fun validateTagName(name: String): NameValidation = validate(name, MAX_TAG_NAME_LENGTH)

    private fun validate(name: String, maxLength: Int): NameValidation {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameValidation.Invalid(NameError.REQUIRED)
            trimmed.length > maxLength -> NameValidation.Invalid(NameError.TOO_LONG)
            else -> NameValidation.Valid(trimmed)
        }
    }
}
