package com.rocioplata.familyfinaiapp.domain.movements

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Primer y último instante (inclusivos, al milisegundo) de un mes, para pedir el listado con `from` y `to`. */
data class MonthBounds(val from: Instant, val to: Instant)

/**
 * Conversión entre el día de un movimiento y los instantes que maneja el backend (BG-09 de la 007). El
 * movimiento solo registra el día (RF-16), pero el backend guarda un instante, rechaza los futuros y filtra
 * por instante. Todo usa la zona del [clock], que en la app es la del dispositivo; en los tests se inyectan
 * un reloj y una zona fijos.
 */
class MovementDates(private val clock: Clock = Clock.systemDefaultZone()) {
    private val zone: ZoneId get() = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    fun currentMonth(): YearMonth = YearMonth.from(today())

    /**
     * Instante a enviar para el día elegido: el actual si es hoy (a las 12:00 podría ser futuro) o las 12:00
     * de ese día en la zona del dispositivo si es pasado. Nunca es futuro; un día futuro es un error de
     * programación porque la validación ([MovementValidation.validateDate]) lo impide antes.
     */
    fun instantToSend(day: LocalDate): Instant {
        val today = today()
        require(!day.isAfter(today)) { "Un movimiento no puede tener fecha futura: $day" }
        return if (day == today) clock.instant() else ZonedDateTime.of(day, LocalTime.NOON, zone).toInstant()
    }

    /** Día de un instante del backend en la zona del dispositivo. */
    fun dayOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** Del primer instante del día 1 al último milisegundo del mes, en la zona del dispositivo. */
    fun boundsOf(month: YearMonth): MonthBounds {
        val from = month.atDay(1).atStartOfDay(zone).toInstant()
        val nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        return MonthBounds(from, nextMonthStart.minusMillis(1))
    }
}
