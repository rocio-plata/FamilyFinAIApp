package com.rocioplata.familyfinaiapp.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun decimalesDeCadaMoneda() {
        assertEquals(0, Money(1, "CLP").fractionDigits)
        assertEquals(2, Money(1, "USD").fractionDigits)
        assertEquals(2, Money(1, "EUR").fractionDigits)
    }

    @Test
    fun monedaInvalida_noTieneDecimales() {
        assertThrows(IllegalArgumentException::class.java) { Money.fractionDigitsOf("XXXX") }
    }

    @Test
    fun suma_yResta_enLaMismaMoneda() {
        assertEquals(Money(1_500, "CLP"), Money(1_000, "CLP") + Money(500, "CLP"))
        assertEquals(Money(-200, "USD"), Money(300, "USD") - Money(500, "USD"))
    }

    @Test
    fun sumaDeVariosMontos() {
        val montos = listOf(Money(38_450, "CLP"), Money(4_750, "CLP"), Money(25_000, "CLP"))

        assertEquals(Money(68_200, "CLP"), montos.sum("CLP"))
    }

    @Test
    fun sumaSinMontos_esCeroEnLaMoneda() {
        assertEquals(Money(0, "EUR"), emptyList<Money>().sum("EUR"))
    }

    @Test
    fun balance_esIngresosMenosGastos() {
        val ingresos = listOf(Money(1_200_000, "CLP")).sum("CLP")
        val gastos = listOf(Money(1_448_350, "CLP")).sum("CLP")

        assertEquals(Money(-248_350, "CLP"), ingresos - gastos)
    }

    @Test
    fun monedasDistintas_noSePuedenOperar() {
        assertThrows(IllegalArgumentException::class.java) { Money(1, "CLP") + Money(1, "USD") }
        assertThrows(IllegalArgumentException::class.java) { Money(1, "CLP") - Money(1, "USD") }
    }

    @Test
    fun desbordamiento_noPasaEnSilencio() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE, "CLP") + Money(1, "CLP") }
    }
}
