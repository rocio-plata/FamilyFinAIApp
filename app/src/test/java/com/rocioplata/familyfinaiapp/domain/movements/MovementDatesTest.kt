package com.rocioplata.familyfinaiapp.domain.movements

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementDatesTest {
    private val santiago = ZoneId.of("America/Santiago")
    private val utc = ZoneId.of("UTC")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun dates(now: String, zone: ZoneId) = MovementDates(Clock.fixed(Instant.parse(now), zone))

    // --- hoy cerca de la medianoche, según la zona ---

    @Test
    fun hoy_dependeDeLaZonaDelDispositivo_cercaDeLaMedianoche() {
        // 02:30 UTC del 24 de octubre: en Santiago (UTC-3, horario de verano) aún es el día 23.
        val now = "2026-10-24T02:30:00Z"

        assertEquals(LocalDate.of(2026, 10, 23), dates(now, santiago).today())
        assertEquals(LocalDate.of(2026, 10, 24), dates(now, utc).today())
        assertEquals(LocalDate.of(2026, 10, 24), dates(now, madrid).today())
        assertEquals(LocalDate.of(2026, 10, 24), dates(now, tokyo).today())
    }

    @Test
    fun mesEnCurso() {
        assertEquals(YearMonth.of(2026, 10), dates("2026-10-24T12:00:00Z", santiago).currentMonth())
        // Un instante del 1 de noviembre UTC todavía es octubre en Santiago.
        assertEquals(YearMonth.of(2026, 10), dates("2026-11-01T01:00:00Z", santiago).currentMonth())
        assertEquals(YearMonth.of(2026, 11), dates("2026-11-01T01:00:00Z", utc).currentMonth())
    }

    // --- día → instante a enviar ---

    @Test
    fun hoy_seEnviaElInstanteActual() {
        val now = "2026-10-24T14:35:10.123Z"

        assertEquals(Instant.parse(now), dates(now, santiago).instantToSend(LocalDate.of(2026, 10, 24)))
        assertEquals(Instant.parse(now), dates(now, tokyo).instantToSend(LocalDate.of(2026, 10, 24)))
    }

    @Test
    fun hoyTemprano_nuncaEsUnInstanteFuturo() {
        // 07:00 locales en Santiago: las 12:00 de hoy serían futuro, así que se envía el instante actual.
        val now = "2026-10-24T10:00:00Z"
        val dates = dates(now, santiago)

        assertEquals(Instant.parse(now), dates.instantToSend(LocalDate.of(2026, 10, 24)))
    }

    @Test
    fun diaPasado_alMediodiaLocalDeEseDia() {
        val now = "2026-10-24T14:00:00Z"

        // Santiago en octubre es UTC-3 (horario de verano): 12:00 locales = 15:00 UTC.
        assertEquals(
            Instant.parse("2026-10-22T15:00:00Z"),
            dates(now, santiago).instantToSend(LocalDate.of(2026, 10, 22)),
        )
        assertEquals(Instant.parse("2026-10-22T12:00:00Z"), dates(now, utc).instantToSend(LocalDate.of(2026, 10, 22)))
        // Madrid a finales de octubre antes del cambio de hora es UTC+2.
        assertEquals(
            Instant.parse("2026-10-22T10:00:00Z"),
            dates(now, madrid).instantToSend(LocalDate.of(2026, 10, 22)),
        )
        assertEquals(Instant.parse("2026-10-22T03:00:00Z"), dates(now, tokyo).instantToSend(LocalDate.of(2026, 10, 22)))
    }

    @Test
    fun ayer_enMadridCercaDeLaMedianoche() {
        // 22:30 UTC del 23 son las 00:30 del 24 en Madrid: ayer es el 23, a las 12:00 locales (10:00 UTC).
        val dates = dates("2026-10-23T22:30:00Z", madrid)

        assertEquals(LocalDate.of(2026, 10, 24), dates.today())
        assertEquals(Instant.parse("2026-10-23T10:00:00Z"), dates.instantToSend(LocalDate.of(2026, 10, 23)))
    }

    @Test
    fun nuncaProduceUnInstanteFuturo_nuncaCambiaDeDia() {
        val nows = listOf(
            "2026-10-24T00:00:00Z", "2026-10-24T02:30:00Z", "2026-10-24T10:00:00Z",
            "2026-10-24T11:59:59Z", "2026-10-24T12:00:00Z", "2026-10-24T23:59:59Z",
        )
        for (zone in listOf(santiago, utc, madrid, tokyo)) {
            for (now in nows) {
                val dates = dates(now, zone)
                val today = dates.today()
                for (day in listOf(today, today.minusDays(1), today.minusDays(30), today.minusYears(2))) {
                    val sent = dates.instantToSend(day)
                    val label = "$zone $now $day -> $sent"
                    assertFalse(label, sent.isAfter(Instant.parse(now)))
                    // Ida y vuelta: leer el instante que se envió devuelve el mismo día.
                    assertEquals(label, day, dates.dayOf(sent))
                }
            }
        }
    }

    @Test
    fun diaFuturo_esUnErrorDeProgramacion() {
        val dates = dates("2026-10-24T14:00:00Z", santiago)

        assertThrows(IllegalArgumentException::class.java) { dates.instantToSend(LocalDate.of(2026, 10, 25)) }
    }

    @Test
    fun diaPasado_conCambioDeHora_sigueAlMediodia() {
        // 5 de abril de 2026 en Santiago: acaba el horario de verano la noche anterior (UTC-4 desde entonces).
        val dates = dates("2026-04-20T14:00:00Z", santiago)

        assertEquals(Instant.parse("2026-04-05T16:00:00Z"), dates.instantToSend(LocalDate.of(2026, 4, 5)))
        assertEquals(Instant.parse("2026-04-03T15:00:00Z"), dates.instantToSend(LocalDate.of(2026, 4, 3)))
    }

    // --- instante → día ---

    @Test
    fun dayOf_usaLaZonaDelDispositivo() {
        val instant = Instant.parse("2026-10-24T02:30:00Z")

        assertEquals(LocalDate.of(2026, 10, 23), dates("2026-10-25T00:00:00Z", santiago).dayOf(instant))
        assertEquals(LocalDate.of(2026, 10, 24), dates("2026-10-25T00:00:00Z", utc).dayOf(instant))
        assertEquals(LocalDate.of(2026, 10, 24), dates("2026-10-25T00:00:00Z", tokyo).dayOf(instant))
    }

    // --- límites del mes ---

    private fun bounds(zone: ZoneId, month: YearMonth) = dates("2026-12-31T12:00:00Z", zone).boundsOf(month)

    @Test
    fun limites_enUtc_conMesesDe28A31Dias() {
        val casos = mapOf(
            YearMonth.of(2026, 2) to ("2026-02-01T00:00:00Z" to "2026-02-28T23:59:59.999Z"),
            YearMonth.of(2028, 2) to ("2028-02-01T00:00:00Z" to "2028-02-29T23:59:59.999Z"),
            YearMonth.of(2026, 4) to ("2026-04-01T00:00:00Z" to "2026-04-30T23:59:59.999Z"),
            YearMonth.of(2026, 1) to ("2026-01-01T00:00:00Z" to "2026-01-31T23:59:59.999Z"),
            YearMonth.of(2026, 12) to ("2026-12-01T00:00:00Z" to "2026-12-31T23:59:59.999Z"),
        )
        for ((month, expected) in casos) {
            val result = bounds(utc, month)
            assertEquals("$month desde", Instant.parse(expected.first), result.from)
            assertEquals("$month hasta", Instant.parse(expected.second), result.to)
        }
    }

    @Test
    fun limites_enTokio_sinCambioDeHora() {
        val result = bounds(tokyo, YearMonth.of(2026, 10))

        assertEquals(Instant.parse("2026-09-30T15:00:00Z"), result.from)
        assertEquals(Instant.parse("2026-10-31T14:59:59.999Z"), result.to)
    }

    @Test
    fun limites_enSantiago_conCambioDeHoraDentroDelMes() {
        // Abril de 2026: empieza con UTC-3 y termina con UTC-4 (acaba el horario de verano el día 4).
        val result = bounds(santiago, YearMonth.of(2026, 4))

        assertEquals(Instant.parse("2026-04-01T03:00:00Z"), result.from)
        assertEquals(Instant.parse("2026-05-01T03:59:59.999Z"), result.to)
    }

    @Test
    fun limites_enMadrid_conCambioDeHoraDentroDelMes() {
        // Marzo de 2026: empieza con UTC+1 y termina con UTC+2 (empieza el horario de verano el día 29).
        val result = bounds(madrid, YearMonth.of(2026, 3))

        assertEquals(Instant.parse("2026-02-28T23:00:00Z"), result.from)
        assertEquals(Instant.parse("2026-03-31T21:59:59.999Z"), result.to)
    }

    @Test
    fun limites_losMesesSeguidosNoSeSolapanNiDejanHuecos() {
        for (zone in listOf(santiago, utc, madrid, tokyo)) {
            var month = YearMonth.of(2026, 1)
            repeat(11) {
                val current = bounds(zone, month)
                val next = bounds(zone, month.plusMonths(1))
                assertEquals("$zone $month", next.from, current.to.plusMillis(1))
                assertTrue(current.from.isBefore(current.to))
                month = month.plusMonths(1)
            }
        }
    }

    @Test
    fun limites_unInstanteDelMesCaeDentro_yElDelOtroMesFuera() {
        val dates = dates("2026-12-31T12:00:00Z", santiago)
        val octubre = dates.boundsOf(YearMonth.of(2026, 10))
        // Primer segundo de noviembre en Santiago (UTC-3): 03:00 UTC.
        val primeroDeNoviembre = Instant.parse("2026-11-01T03:00:00Z")

        assertTrue(primeroDeNoviembre.isAfter(octubre.to))
        assertFalse(primeroDeNoviembre.minusMillis(1).isAfter(octubre.to))
    }
}
