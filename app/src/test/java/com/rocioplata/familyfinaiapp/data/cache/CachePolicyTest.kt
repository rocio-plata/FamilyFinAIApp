package com.rocioplata.familyfinaiapp.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class CachePolicyTest {
    private val saved = 1_000_000L
    private fun at(ageMs: Long) = saved + ageMs

    @Test
    fun desactualizada_soloConMasDe24Horas() {
        assertFalse(CachePolicy.isStale(saved, at(0)))
        assertFalse(CachePolicy.isStale(saved, at(24.hours.inWholeMilliseconds)))
        assertTrue(CachePolicy.isStale(saved, at(24.hours.inWholeMilliseconds + 1)))
    }

    @Test
    fun caducada_aPartirDe7Dias() {
        assertFalse(CachePolicy.isExpired(saved, at(7.days.inWholeMilliseconds - 1)))
        assertTrue(CachePolicy.isExpired(saved, at(7.days.inWholeMilliseconds)))
        assertTrue(CachePolicy.isExpired(saved, at(8.days.inWholeMilliseconds)))
    }
}
