package com.rocioplata.familyfinaiapp.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyInputTest {
    // --- filter ---

    @Test
    fun filter_clpNoAdmiteSeparadorNiLetras() {
        assertEquals("45900", MoneyInput.filter("45.900", 0))
        assertEquals("459", MoneyInput.filter("4,5a9", 0))
        assertEquals("", MoneyInput.filter("abc-+ ", 0))
    }

    @Test
    fun filter_conDecimales_admiteUnSoloSeparador_comaOPunto() {
        assertEquals("12,5", MoneyInput.filter("12,5", 2))
        assertEquals("12.5", MoneyInput.filter("12.5", 2))
        assertEquals("12,56", MoneyInput.filter("12,5,6", 2))
        assertEquals("12,56", MoneyInput.filter("12,5.6", 2))
    }

    @Test
    fun filter_limitaLosDecimalesDeLaMoneda() {
        assertEquals("1,23", MoneyInput.filter("1,2345", 2))
        assertEquals("1,2", MoneyInput.filter("1,2345", 1))
    }

    @Test
    fun filter_conservaUnSeparadorFinalYUnoInicial_paraSeguirEscribiendo() {
        assertEquals("12,", MoneyInput.filter("12,", 2))
        assertEquals(",5", MoneyInput.filter(",5", 2))
    }

    @Test
    fun filter_quitaCerosAIzquierdaSobrantes() {
        assertEquals("7", MoneyInput.filter("007", 0))
        assertEquals("0", MoneyInput.filter("000", 0))
        assertEquals("0,5", MoneyInput.filter("00,5", 2))
        assertEquals("10", MoneyInput.filter("010", 0))
    }

    @Test
    fun filter_topeDeDoceDigitosEnteros() {
        assertEquals("123456789012", MoneyInput.filter("1234567890123", 0))
        assertEquals("123456789012,34", MoneyInput.filter("1234567890123,34", 2))
        assertEquals("123456789012", MoneyInput.filter("123456789012", 0))
    }

    @Test
    fun filter_vacio() {
        assertEquals("", MoneyInput.filter("", 2))
    }

    // --- parse ---

    @Test
    fun parse_clp() {
        assertEquals(45_900L, MoneyInput.parse("45900", 0))
        assertEquals(45_900L, MoneyInput.parse("45.900", 0))
    }

    @Test
    fun parse_usdYEur_conDosDecimales() {
        assertEquals(1_550L, MoneyInput.parse("15,5", 2))
        assertEquals(1_550L, MoneyInput.parse("15.5", 2))
        assertEquals(1_599L, MoneyInput.parse("15,99", 2))
        assertEquals(1_500L, MoneyInput.parse("15", 2))
        assertEquals(1_500L, MoneyInput.parse("15,", 2))
        assertEquals(50L, MoneyInput.parse(",5", 2))
        assertEquals(5L, MoneyInput.parse("0,05", 2))
    }

    @Test
    fun parse_vacio_yNoNumero_esNulo() {
        assertNull(MoneyInput.parse("", 2))
        assertNull(MoneyInput.parse("   ", 0))
        assertNull(MoneyInput.parse(",", 2))
        assertNull(MoneyInput.parse("abc", 0))
    }

    @Test
    fun parse_cero_esCero() {
        assertEquals(0L, MoneyInput.parse("0", 0))
        assertEquals(0L, MoneyInput.parse("0,00", 2))
        assertEquals(0L, MoneyInput.parse("000", 0))
    }

    @Test
    fun parse_cerosAIzquierda() {
        assertEquals(7L, MoneyInput.parse("007", 0))
        assertEquals(750L, MoneyInput.parse("007,5", 2))
    }

    @Test
    fun parse_maximo_cabeEnUnLong() {
        assertEquals(999_999_999_999L, MoneyInput.parse("999999999999", 0))
        assertEquals(99_999_999_999_999L, MoneyInput.parse("999999999999,99", 2))
    }

    @Test
    fun parse_decimalesSobrantes_seIgnoranComoEnElFiltro() {
        assertEquals(1_599L, MoneyInput.parse("15,999", 2))
    }

    @Test
    fun parse_enClpElSeparadorSeIgnora_noEsDecimal() {
        // Con una moneda sin decimales "45.900" es un separador de miles, y "45900,5" son cinco dígitos más.
        assertEquals(45_900L, MoneyInput.parse("45.900", 0))
        assertEquals(459_005L, MoneyInput.parse("45900,5", 0))
    }

    // --- format e ida y vuelta ---

    @Test
    fun format_conYSinDecimales() {
        assertEquals("45900", MoneyInput.format(45_900, 0))
        assertEquals("15,99", MoneyInput.format(1_599, 2))
        assertEquals("15.99", MoneyInput.format(1_599, 2, '.'))
        assertEquals("0,05", MoneyInput.format(5, 2))
        assertEquals("0,00", MoneyInput.format(0, 2))
        assertEquals("1250,00", MoneyInput.format(125_000, 2))
        assertEquals("0", MoneyInput.format(0, 0))
    }

    @Test
    fun format_rechazaNegativosYSeparadoresRaros() {
        assertThrows(IllegalArgumentException::class.java) { MoneyInput.format(-1, 2) }
        assertThrows(IllegalArgumentException::class.java) { MoneyInput.format(1, 2, ';') }
    }

    @Test
    fun idaYVuelta_textoYUnidadMenor() {
        val maximoPorDecimales = mapOf(0 to 999_999_999_999L, 2 to 99_999_999_999_999L)
        for ((digits, maximo) in maximoPorDecimales) {
            for (amount in listOf(0L, 1L, 5L, 99L, 100L, 1_599L, 45_900L, 123_456_789L, maximo)) {
                val text = MoneyInput.format(amount, digits)
                assertEquals("$amount con $digits decimales", amount, MoneyInput.parse(text, digits))
                // Lo que se muestra al editar es siempre un texto que el filtro deja intacto.
                assertEquals(text, MoneyInput.filter(text, digits))
            }
        }
    }
}
