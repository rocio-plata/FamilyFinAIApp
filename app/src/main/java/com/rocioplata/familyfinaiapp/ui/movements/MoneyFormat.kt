package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * Monto con la moneda y los decimales de [Money.currency] y el formato de la configuración regional (RF-02).
 * Un monto negativo (el balance de un mes) lleva el signo del formato regional.
 */
fun Money.formatted(locale: Locale = Locale.getDefault()): String =
    currencyFormat(currency, locale).format(BigDecimal.valueOf(amountMinor, fractionDigits))

/**
 * Monto de un movimiento con su signo explícito, para distinguir gasto de ingreso sin depender del color
 * (RF-02): `-` en un gasto y `+` en un ingreso, delante del monto formateado sin signo propio.
 */
fun Money.formattedFor(type: MovementType, locale: Locale = Locale.getDefault()): String {
    val absolute = BigDecimal.valueOf(amountMinor, fractionDigits).abs()
    val sign = when (type) {
        MovementType.EXPENSE -> "-"
        MovementType.INCOME -> "+"
    }
    return sign + currencyFormat(currency, locale).format(absolute)
}

private fun currencyFormat(currency: String, locale: Locale): NumberFormat =
    NumberFormat.getCurrencyInstance(locale).apply {
        val iso = Currency.getInstance(currency)
        this.currency = iso
        minimumFractionDigits = iso.defaultFractionDigits
        maximumFractionDigits = iso.defaultFractionDigits
    }

/** Cabecera de un día del listado, p. ej. "Miércoles 12 nov" (RF-02). */
fun formatDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE d MMM", locale).format(date).capitalized(locale)

/** Mes mostrado en el selector, p. ej. "Noviembre 2026" (RF-01). */
fun formatMonth(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).capitalized(locale)

/** Mes abreviado con la abreviatura de la configuración regional, p. ej. "sept 2026" («Archivada en …», RF-09 de la 010). */
fun formatShortMonth(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("LLL yyyy", locale).format(month)

/** Fecha completa del detalle y del formulario, p. ej. "12 de noviembre de 2026". */
fun formatDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)

private fun String.capitalized(locale: Locale): String =
    replaceFirstChar { it.titlecase(locale) }

/**
 * Monto con signo explícito cuando el resultado no es cero: `+` si es positivo y el signo del formato
 * regional si es negativo; para subtotales y balances (RF-17).
 */
fun Money.formattedSigned(locale: Locale = Locale.getDefault()): String =
    if (amountMinor > 0) "+" + formatted(locale) else formatted(locale)
