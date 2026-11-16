package com.rocioplata.familyfinaiapp.domain.money

import java.util.Currency

/**
 * Dinero como entero en la unidad menor de la moneda (CLP sin decimales; USD y EUR en centavos), tal como
 * lo manda el backend (`Money {amount, currency}`). Nunca se usa coma flotante. No hay conversión entre
 * monedas: sumar o restar montos de monedas distintas es un error de programación.
 */
data class Money(val amountMinor: Long, val currency: String) {
    /** Decimales de la moneda según `java.util.Currency` (CLP 0, USD 2, EUR 2). */
    val fractionDigits: Int get() = fractionDigitsOf(currency)

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(amountMinor, other.amountMinor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(amountMinor, other.amountMinor), currency)
    }

    private fun requireSameCurrency(other: Money) =
        require(currency == other.currency) { "Monedas distintas: $currency y ${other.currency}" }

    companion object {
        fun zero(currency: String) = Money(0L, currency)

        /** Decimales de una moneda; lanza `IllegalArgumentException` si el código no es una moneda ISO 4217. */
        fun fractionDigitsOf(currency: String): Int = Currency.getInstance(currency).defaultFractionDigits
    }
}

/** Suma de varios montos de la misma moneda; sin montos es cero en [currency]. */
fun Iterable<Money>.sum(currency: String): Money = fold(Money.zero(currency)) { total, money -> total + money }
