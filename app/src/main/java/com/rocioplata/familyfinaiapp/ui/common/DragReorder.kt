package com.rocioplata.familyfinaiapp.ui.common

import kotlin.math.roundToInt

// Cálculo del reordenado por arrastre de las listas cortas de la app (etiquetas de una categoría y familias del selector).

/**
 * Posición, entre las filas que se pueden mover, a la que corresponde un arrastre de [offsetPx] desde [from], siendo [stepPx] lo
 * que ocupa una fila más su separación. Se ajusta a los límites; sin paso medido no hay movimiento.
 */
internal fun dragTargetIndex(from: Int, offsetPx: Float, stepPx: Float, count: Int): Int {
    if (count <= 0) return 0
    if (stepPx <= 0f) return from.coerceIn(0, count - 1)
    return (from + (offsetPx / stepPx).roundToInt()).coerceIn(0, count - 1)
}

/** Cuánto se aparta (en px) la fila [index] para dejar sitio a la fila que se arrastra de [from] a [target]. */
internal fun dragShift(index: Int, from: Int, target: Int, stepPx: Float): Float = when {
    index == from -> 0f
    from < target && index in (from + 1)..target -> -stepPx
    from > target && index in target until from -> stepPx
    else -> 0f
}
