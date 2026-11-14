package com.rocioplata.familyfinaiapp.data.cache

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Regla común de la app para los datos guardados en el dispositivo: se descartan a partir de 7 días
 * y se marcan como posiblemente desactualizados con más de 24 h. Los instantes son epoch ms UTC.
 */
object CachePolicy {
    private val STALE_AFTER_MS = 24.hours.inWholeMilliseconds
    private val MAX_AGE_MS = 7.days.inWholeMilliseconds

    /** Más de 24 h desde que se obtuvo del backend. */
    fun isStale(savedAtMillis: Long, nowMillis: Long): Boolean = nowMillis - savedAtMillis > STALE_AFTER_MS

    /** 7 días o más: ya no se muestra y debe eliminarse. */
    fun isExpired(savedAtMillis: Long, nowMillis: Long): Boolean = nowMillis - savedAtMillis >= MAX_AGE_MS
}
