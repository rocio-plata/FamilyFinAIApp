package com.rocioplata.familyfinaiapp.ui.movements

import androidx.compose.material3.ExperimentalMaterial3Api
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class NotAfterTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val selectable = NotAfter(today)

    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun hoyYLosDiasPasados_sePuedenElegir() {
        assertTrue(selectable.isSelectableDate(millis(today)))
        assertTrue(selectable.isSelectableDate(millis(today.minusDays(1))))
        assertTrue(selectable.isSelectableDate(millis(LocalDate.of(2020, 1, 1))))
    }

    @Test
    fun ningunDiaFuturo_sePuedeElegir() {
        assertFalse(selectable.isSelectableDate(millis(today.plusDays(1))))
        assertFalse(selectable.isSelectableDate(millis(LocalDate.of(2027, 1, 1))))
    }

    @Test
    fun noSeOfreceUnAnioFuturo() {
        assertTrue(selectable.isSelectableYear(2026))
        assertFalse(selectable.isSelectableYear(2027))
    }
}
