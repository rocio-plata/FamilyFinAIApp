package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFormatTest {
    private val us = Locale.US
    private val es = Locale.forLanguageTag("es-CL")

    @Test
    fun clpNoTieneDecimales() {
        val text = Money(45_900, "CLP").formatted(us)

        assertTrue(text, text.contains("45,900"))
        assertFalse(text, text.contains("."))
    }

    @Test
    fun usdTieneDosDecimales() {
        assertEquals("$1,234.50", Money(123_450, "USD").formatted(us))
        assertEquals("$0.05", Money(5, "USD").formatted(us))
    }

    @Test
    fun usaLaConfiguracionRegional() {
        val text = Money(1_200_000, "CLP").formatted(es)

        assertTrue(text, text.contains("1.200.000"))
    }

    @Test
    fun unMontoNegativoLlevaElSignoDelFormato() {
        val text = Money(-248_350, "CLP").formatted(es)

        assertTrue(text, text.contains("248.350"))
        assertTrue(text, text.contains("-"))
    }

    @Test
    fun elGastoLlevaSignoNegativo() {
        val text = Money(45_900, "CLP").formattedFor(MovementType.EXPENSE, es)

        assertTrue(text, text.startsWith("-"))
        assertTrue(text, text.contains("45.900"))
    }

    @Test
    fun elIngresoLlevaSignoPositivo() {
        assertEquals("+$1,200.00", Money(120_000, "USD").formattedFor(MovementType.INCOME, us))
    }

    @Test
    fun elGastoEnUsdLlevaSignoNegativoYDecimales() {
        assertEquals("-$12.30", Money(1_230, "USD").formattedFor(MovementType.EXPENSE, us))
    }

    @Test
    fun elSignoDependeDelTipoYNoDelValor() {
        assertEquals("-$1.00", Money(-100, "USD").formattedFor(MovementType.EXPENSE, us))
    }

    @Test
    fun elMaximoDe12DigitosEnteros_noPierdePrecision() {
        val text = Money(999_999_999_999_99, "USD").formatted(us)

        assertEquals("$999,999,999,999.99", text)
    }

    @Test
    fun formatoDeDia() {
        assertEquals("Jueves 12 nov", formatDay(LocalDate.of(2026, 11, 12), es).replace(".", ""))
        assertEquals("Wednesday 11 Nov", formatDay(LocalDate.of(2026, 11, 11), us))
    }

    @Test
    fun formatoDeMes() {
        assertEquals("Noviembre 2026", formatMonth(YearMonth.of(2026, 11), es))
        assertEquals("November 2026", formatMonth(YearMonth.of(2026, 11), us))
    }

    @Test
    fun formatoDeFechaCompleta() {
        val text = formatDate(LocalDate.of(2026, 11, 12), es)

        assertTrue(text, text.contains("12") && text.contains("noviembre") && text.contains("2026"))
    }

    @Test
    fun conSignoExplicito_positivoLlevaMas_negativoSuSigno_yCeroNinguno() {
        assertEquals("+$1,175.00", Money(117_500, "USD").formattedSigned(us))
        assertEquals("-$30.00", Money(-3_000, "USD").formattedSigned(us))
        assertEquals("$0.00", Money(0, "USD").formattedSigned(us))
    }
}
