package com.rocioplata.familyfinaiapp.domain.header

/** Valor de un recurso junto al instante (epoch ms UTC) en que se obtuvo del backend. */
data class Timed<T>(
    val value: T,
    val savedAtMillis: Long,
)

/** Cada recurso es nulo mientras no haya valor válido, de modo que falla y se muestra por separado. */
data class HeaderData(
    val families: Timed<List<Family>>? = null,
    val pendingInvitations: Timed<Int>? = null,
    val displayName: Timed<String>? = null,
    /** Algún recurso mostrado tiene más de 24 h sin confirmarse con el backend (CA-16). */
    val isStale: Boolean = false,
)
