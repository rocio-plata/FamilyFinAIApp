package com.rocioplata.familyfinaiapp.domain.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyNameRulesTest {
    private fun validate(name: String, vararg existing: String) = FamilyNameRules.validate(name, existing.toList())

    private fun invalid(error: FamilyNameError) = FamilyNameValidation.Invalid(error)

    @Test
    fun nombreValido_seDevuelveRecortado() {
        assertEquals(FamilyNameValidation.Valid("Familia Plata"), validate("  Familia Plata \n"))
    }

    @Test
    fun vacioOSoloEspacios_esObligatorio() {
        assertEquals(invalid(FamilyNameError.REQUIRED), validate(""))
        assertEquals(invalid(FamilyNameError.REQUIRED), validate("   \t "))
    }

    @Test
    fun limiteDe60_seAceptaConLos60YSeRechazaConLos61() {
        assertEquals(FamilyNameValidation.Valid("a".repeat(60)), validate("a".repeat(60)))
        assertEquals(invalid(FamilyNameError.TOO_LONG), validate("a".repeat(61)))
    }

    @Test
    fun losEspaciosExtremosNoCuentanParaElLimite() {
        assertEquals(FamilyNameValidation.Valid("a".repeat(60)), validate("  " + "a".repeat(60) + "  "))
    }

    @Test
    fun tildesEnieYSignos_seAceptan() {
        listOf("Cañas & Núñez", "¿Casa de campo?", "Papá y Mamá", "Depto. 3-B", "O'Higgins", "Año 2026", "© ® ™ casa").forEach {
            assertEquals(it, FamilyNameValidation.Valid(it), validate(it))
        }
    }

    @Test
    fun emojis_seRechazan_simplesCompuestosBanderasYTeclas() {
        listOf(
            "Casa 🏠", // simple
            "❤️ Familia", // símbolo con presentación de emoji
            "Familia 👨‍👩‍👧", // secuencia con uniones
            "Mi 🇨🇱 casa", // bandera
            "Tel 1️⃣", // tecla
            "Tono 👍🏽", // con tono de piel
            "Sol ☀", // símbolo del bloque de símbolos varios
        ).forEach {
            assertEquals(it, invalid(FamilyNameError.EMOJI), validate(it))
        }
    }

    @Test
    fun repetido_seCompareSinDistinguirMayusculasNiEspacios() {
        assertEquals(invalid(FamilyNameError.DUPLICATE), validate("familia plata", "Familia Plata"))
        assertEquals(invalid(FamilyNameError.DUPLICATE), validate("  FAMILIA PLATA ", "Mis finanzas", " Familia Plata "))
        assertEquals(invalid(FamilyNameError.DUPLICATE), validate("ÑANDÚ", "ñandú"))
    }

    @Test
    fun distintoDeLasDemas_esValido() {
        assertEquals(FamilyNameValidation.Valid("Plata 2"), validate("Plata 2", "Plata"))
    }

    @Test
    fun elOrdenDeLosErrores_esObligatorioLongitudEmojiRepetido() {
        assertEquals(invalid(FamilyNameError.TOO_LONG), validate("🏠".repeat(31)))
        assertEquals(invalid(FamilyNameError.EMOJI), validate("🏠", "🏠"))
    }

    @Test
    fun containsEmoji_noConfundeNumerosNiLetras() {
        assertFalse(FamilyNameRules.containsEmoji("1234567890 #*"))
        assertTrue(FamilyNameRules.containsEmoji("a😀b"))
    }
}
