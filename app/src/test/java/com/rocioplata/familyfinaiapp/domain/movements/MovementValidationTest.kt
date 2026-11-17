package com.rocioplata.familyfinaiapp.domain.movements

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementValidationTest {
    private fun <T> valid(value: T) = FieldValidation.Valid(value)
    private fun <E> invalid(error: E) = FieldValidation.Invalid(error)

    // --- monto ---

    @Test
    fun monto_vacioEsObligatorio() {
        assertEquals(invalid(AmountError.REQUIRED), MovementValidation.validateAmount(null))
    }

    @Test
    fun monto_ceroONegativoNoEsValido() {
        assertEquals(invalid(AmountError.MUST_BE_POSITIVE), MovementValidation.validateAmount(0))
        assertEquals(invalid(AmountError.MUST_BE_POSITIVE), MovementValidation.validateAmount(-5))
    }

    @Test
    fun monto_positivoEsValido_tambienElMinimo() {
        assertEquals(valid(1L), MovementValidation.validateAmount(1))
        assertEquals(valid(45_900L), MovementValidation.validateAmount(45_900))
    }

    // --- concepto ---

    @Test
    fun concepto_vacioOSoloEspacios_esObligatorio() {
        assertEquals(invalid(TitleError.REQUIRED), MovementValidation.validateTitle(""))
        assertEquals(invalid(TitleError.REQUIRED), MovementValidation.validateTitle("   "))
        assertEquals(invalid(TitleError.REQUIRED), MovementValidation.validateTitle(" \t\n "))
    }

    @Test
    fun concepto_seRecortaAntesDeValidarYDevolverse() {
        assertEquals(valid("Supermercado Jumbo"), MovementValidation.validateTitle("  Supermercado Jumbo  "))
    }

    @Test
    fun concepto_limiteDeCien() {
        assertEquals(valid("a".repeat(100)), MovementValidation.validateTitle("a".repeat(100)))
        assertEquals(invalid(TitleError.TOO_LONG), MovementValidation.validateTitle("a".repeat(101)))
    }

    @Test
    fun concepto_losEspaciosExtremosNoCuentanParaElLimite() {
        assertEquals(valid("a".repeat(100)), MovementValidation.validateTitle("  " + "a".repeat(100) + "  "))
    }

    // --- observaciones ---

    @Test
    fun observaciones_nulasVaciasOSoloEspacios_sonSinObservaciones() {
        assertEquals(valid(null), MovementValidation.validateNote(null))
        assertEquals(valid(null), MovementValidation.validateNote(""))
        assertEquals(valid(null), MovementValidation.validateNote("   \n "))
    }

    @Test
    fun observaciones_seRecortan() {
        assertEquals(valid("Compra semanal"), MovementValidation.validateNote("  Compra semanal \n"))
    }

    @Test
    fun observaciones_limiteDeQuinientos() {
        assertEquals(valid("a".repeat(500)), MovementValidation.validateNote("a".repeat(500)))
        assertEquals(invalid(NoteError.TOO_LONG), MovementValidation.validateNote("a".repeat(501)))
        assertEquals(valid("a".repeat(500)), MovementValidation.validateNote(" " + "a".repeat(500) + " "))
    }

    @Test
    fun limitesExpuestos() {
        assertEquals(100, MovementValidation.MAX_TITLE_LENGTH)
        assertEquals(500, MovementValidation.MAX_NOTE_LENGTH)
    }

    // --- fecha ---

    @Test
    fun fecha_hoyYPasadoSonValidos_futuroNo() {
        val today = LocalDate.of(2026, 10, 24)

        assertEquals(valid(today), MovementValidation.validateDate(today, today))
        assertEquals(valid(today.minusDays(1)), MovementValidation.validateDate(today.minusDays(1), today))
        assertEquals(valid(today.minusYears(3)), MovementValidation.validateDate(today.minusYears(3), today))
        assertEquals(invalid(DateError.FUTURE), MovementValidation.validateDate(today.plusDays(1), today))
    }
}
