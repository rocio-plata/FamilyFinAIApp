package com.rocioplata.familyfinaiapp.domain.paymentmethods

enum class PaymentMethodNameError { REQUIRED, TOO_LONG }

/** Resultado de validar un nombre: el valor ya recortado, que es el que se envía y guarda, o el error. */
sealed interface PaymentMethodNameValidation {
    data class Valid(val name: String) : PaymentMethodNameValidation
    data class Invalid(val error: PaymentMethodNameError) : PaymentMethodNameValidation
}

/**
 * Regla de nombre de un medio de pago (RF-06, RF-07, RF-13). Los espacios del inicio y del final se
 * eliminan antes de validar; la longitud cuenta unidades UTF-16, como `length` en el backend.
 */
object PaymentMethodValidation {
    const val MAX_NAME_LENGTH = 40

    fun validateName(name: String): PaymentMethodNameValidation {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> PaymentMethodNameValidation.Invalid(PaymentMethodNameError.REQUIRED)
            trimmed.length > MAX_NAME_LENGTH -> PaymentMethodNameValidation.Invalid(PaymentMethodNameError.TOO_LONG)
            else -> PaymentMethodNameValidation.Valid(trimmed)
        }
    }
}
