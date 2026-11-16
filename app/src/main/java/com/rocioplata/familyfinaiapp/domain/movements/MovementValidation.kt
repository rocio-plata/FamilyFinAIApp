package com.rocioplata.familyfinaiapp.domain.movements

import java.time.LocalDate

/** Resultado de validar un campo: el valor ya normalizado, que es el que se envía y guarda, o su error. */
sealed interface FieldValidation<out T, out E> {
    data class Valid<out T>(val value: T) : FieldValidation<T, Nothing>
    data class Invalid<out E>(val error: E) : FieldValidation<Nothing, E>
}

enum class AmountError { REQUIRED, MUST_BE_POSITIVE }
enum class TitleError { REQUIRED, TOO_LONG }
enum class NoteError { TOO_LONG }
enum class DateError { FUTURE }

/**
 * Reglas de los campos de un movimiento (RF-05, RF-16, CA-06, CA-09). Los espacios del inicio y del final se
 * eliminan antes de validar; la longitud cuenta unidades UTF-16, como `length` en el backend.
 */
object MovementValidation {
    const val MAX_TITLE_LENGTH = 100
    const val MAX_NOTE_LENGTH = 500

    /** [amountMinor] es nulo si el campo está vacío o no es un número. */
    fun validateAmount(amountMinor: Long?): FieldValidation<Long, AmountError> = when {
        amountMinor == null -> FieldValidation.Invalid(AmountError.REQUIRED)
        amountMinor <= 0L -> FieldValidation.Invalid(AmountError.MUST_BE_POSITIVE)
        else -> FieldValidation.Valid(amountMinor)
    }

    fun validateTitle(title: String): FieldValidation<String, TitleError> {
        val trimmed = title.trim()
        return when {
            trimmed.isEmpty() -> FieldValidation.Invalid(TitleError.REQUIRED)
            trimmed.length > MAX_TITLE_LENGTH -> FieldValidation.Invalid(TitleError.TOO_LONG)
            else -> FieldValidation.Valid(trimmed)
        }
    }

    /** Las observaciones son opcionales: vacías o solo espacios equivalen a "sin observaciones" (`null`). */
    fun validateNote(note: String?): FieldValidation<String?, NoteError> {
        val trimmed = note?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> FieldValidation.Valid(null)
            trimmed.length > MAX_NOTE_LENGTH -> FieldValidation.Invalid(NoteError.TOO_LONG)
            else -> FieldValidation.Valid(trimmed)
        }
    }

    /** No puede ser futura: [today] es el día de hoy en la zona del dispositivo. */
    fun validateDate(date: LocalDate, today: LocalDate): FieldValidation<LocalDate, DateError> =
        if (date.isAfter(today)) FieldValidation.Invalid(DateError.FUTURE) else FieldValidation.Valid(date)
}
