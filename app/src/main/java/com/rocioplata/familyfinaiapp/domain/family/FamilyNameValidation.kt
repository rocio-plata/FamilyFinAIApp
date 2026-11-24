package com.rocioplata.familyfinaiapp.domain.family

import java.util.Locale

enum class FamilyNameError { REQUIRED, TOO_LONG, EMOJI, DUPLICATE }

/** Resultado de validar un nombre: el valor ya recortado, que es el que se envía, o el error. */
sealed interface FamilyNameValidation {
    data class Valid(val name: String) : FamilyNameValidation
    data class Invalid(val error: FamilyNameError) : FamilyNameValidation
}

/**
 * Regla del nombre al crear una familia (RF-04). El backend solo exige de 1 a 60 caracteres (BG-03): lo demás se valida
 * aquí. La longitud cuenta unidades UTF-16, como en el backend; los espacios extremos se eliminan antes de validar.
 */
object FamilyNameRules {
    const val MAX_NAME_LENGTH = 60

    /** [existingNames]: los nombres de las demás familias del usuario; se compara sin distinguir mayúsculas. */
    fun validate(name: String, existingNames: Collection<String>): FamilyNameValidation {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> FamilyNameValidation.Invalid(FamilyNameError.REQUIRED)
            trimmed.length > MAX_NAME_LENGTH -> FamilyNameValidation.Invalid(FamilyNameError.TOO_LONG)
            containsEmoji(trimmed) -> FamilyNameValidation.Invalid(FamilyNameError.EMOJI)
            existingNames.any { sameName(it, trimmed) } -> FamilyNameValidation.Invalid(FamilyNameError.DUPLICATE)
            else -> FamilyNameValidation.Valid(trimmed)
        }
    }

    private fun sameName(a: String, b: String) =
        a.trim().lowercase(Locale.ROOT) == b.trim().lowercase(Locale.ROOT)

    /**
     * Emojis, banderas, teclas y sus secuencias, por rangos de puntos de código. Letras con tilde, ñ, signos y los
     * símbolos de texto © ® ™ no cuentan.
     */
    fun containsEmoji(text: String): Boolean = text.codePoints().anyMatch { cp ->
        cp in 0x1F000..0x1FAFF || // pictogramas, emoticonos, transporte, banderas y suplementos
            cp in 0x2600..0x27BF || // símbolos varios y dingbats
            cp in 0x2300..0x23FF || // técnicos (reloj, play…)
            cp in 0x2B00..0x2BFF || // flechas y formas (estrella, círculos)
            cp == 0x200D || // unión de secuencias
            cp == 0xFE0F || // presentación como emoji
            cp == 0x20E3 // tecla
    }
}
