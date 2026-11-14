package com.rocioplata.familyfinaiapp.domain.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryValidationTest {
    private fun valid(name: String) = NameValidation.Valid(name)
    private fun invalid(error: NameError) = NameValidation.Invalid(error)

    @Test
    fun nombreDeCategoriaValido_sinError() {
        assertEquals(valid("Comida"), CategoryValidation.validateCategoryName("Comida"))
    }

    @Test
    fun nombreDeCategoria_vacioOSoloEspacios_esObligatorio() {
        assertEquals(invalid(NameError.REQUIRED), CategoryValidation.validateCategoryName(""))
        assertEquals(invalid(NameError.REQUIRED), CategoryValidation.validateCategoryName("   "))
        assertEquals(invalid(NameError.REQUIRED), CategoryValidation.validateCategoryName(" \t\n "))
    }

    @Test
    fun nombreDeCategoria_seRecortaAntesDeValidarYDevolverse() {
        assertEquals(valid("Comida"), CategoryValidation.validateCategoryName("  Comida  "))
        assertEquals(valid("Casa y hogar"), CategoryValidation.validateCategoryName(" Casa y hogar"))
    }

    @Test
    fun nombreDeCategoria_limiteDe50() {
        assertEquals(valid("a".repeat(50)), CategoryValidation.validateCategoryName("a".repeat(50)))
        assertEquals(invalid(NameError.TOO_LONG), CategoryValidation.validateCategoryName("a".repeat(51)))
    }

    @Test
    fun nombreDeCategoria_losEspaciosExtremosNoCuentanParaElLimite() {
        assertEquals(valid("a".repeat(50)), CategoryValidation.validateCategoryName("  " + "a".repeat(50) + "  "))
    }

    @Test
    fun nombreDeTag_validoYRecortado() {
        assertEquals(valid("Super"), CategoryValidation.validateTagName("  Super "))
    }

    @Test
    fun nombreDeTag_vacioOSoloEspacios_esObligatorio() {
        assertEquals(invalid(NameError.REQUIRED), CategoryValidation.validateTagName(""))
        assertEquals(invalid(NameError.REQUIRED), CategoryValidation.validateTagName("   "))
    }

    @Test
    fun nombreDeTag_limiteDe30() {
        assertEquals(valid("a".repeat(30)), CategoryValidation.validateTagName("a".repeat(30)))
        assertEquals(invalid(NameError.TOO_LONG), CategoryValidation.validateTagName("a".repeat(31)))
    }

    @Test
    fun losLimitesDeCategoriaYTagSonDistintos() {
        val name = "a".repeat(40)
        assertEquals(valid(name), CategoryValidation.validateCategoryName(name))
        assertEquals(invalid(NameError.TOO_LONG), CategoryValidation.validateTagName(name))
    }

    @Test
    fun espaciosInternosSeConservan() {
        assertEquals(valid("a  b"), CategoryValidation.validateTagName("a  b"))
    }
}
