package com.rocioplata.familyfinaiapp.ui.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class TagDragTest {
    private val step = 100f

    // --- dragTargetIndex ---

    @Test
    fun sinDesplazamiento_laPosicionNoCambia() {
        assertEquals(2, dragTargetIndex(from = 2, offsetPx = 0f, stepPx = step, count = 5))
    }

    @Test
    fun menosDeLaMitadDeUnPaso_noCambiaDePosicion() {
        assertEquals(2, dragTargetIndex(2, 49f, step, 5))
        assertEquals(2, dragTargetIndex(2, -49f, step, 5))
    }

    @Test
    fun masDeLaMitadDeUnPaso_cambiaUnaPosicion() {
        assertEquals(3, dragTargetIndex(2, 51f, step, 5))
        assertEquals(1, dragTargetIndex(2, -51f, step, 5))
    }

    @Test
    fun variosPasos_muevenVariasPosiciones() {
        assertEquals(4, dragTargetIndex(1, 300f, step, 5))
        assertEquals(0, dragTargetIndex(3, -300f, step, 5))
    }

    @Test
    fun seAjustaALosLimites() {
        assertEquals(4, dragTargetIndex(2, 10_000f, step, 5))
        assertEquals(0, dragTargetIndex(2, -10_000f, step, 5))
    }

    @Test
    fun sinPasoMedidoOSinFilas_noHayMovimiento() {
        assertEquals(2, dragTargetIndex(2, 500f, 0f, 5))
        assertEquals(2, dragTargetIndex(2, 500f, -1f, 5))
        assertEquals(0, dragTargetIndex(2, 500f, step, 0))
    }

    // --- dragShift ---

    @Test
    fun alBajarUnTag_lasFilasIntermediasSubenUnPaso() {
        // from = 1, target = 3: las filas 2 y 3 se apartan hacia arriba.
        assertEquals(0f, dragShift(index = 0, from = 1, target = 3, stepPx = step), 0f)
        assertEquals(0f, dragShift(1, 1, 3, step), 0f)
        assertEquals(-step, dragShift(2, 1, 3, step), 0f)
        assertEquals(-step, dragShift(3, 1, 3, step), 0f)
        assertEquals(0f, dragShift(4, 1, 3, step), 0f)
    }

    @Test
    fun alSubirUnTag_lasFilasIntermediasBajanUnPaso() {
        // from = 3, target = 1: las filas 1 y 2 se apartan hacia abajo.
        assertEquals(0f, dragShift(0, 3, 1, step), 0f)
        assertEquals(step, dragShift(1, 3, 1, step), 0f)
        assertEquals(step, dragShift(2, 3, 1, step), 0f)
        assertEquals(0f, dragShift(3, 3, 1, step), 0f)
        assertEquals(0f, dragShift(4, 3, 1, step), 0f)
    }

    @Test
    fun sinCambioDePosicion_ningunaFilaSeAparta() {
        (0..4).forEach { assertEquals(0f, dragShift(it, 2, 2, step), 0f) }
    }

    @Test
    fun elResultadoVisualCoincideConElOrdenQueSePide() {
        // Posición visual de cada fila durante el arrastre: el tag arrastrado está en la posición destino y
        // las demás, en su índice más lo que se apartan (con paso 1, el desplazamiento es en filas).
        val order = listOf("a", "b", "c", "d", "e")
        listOf(1 to 3, 3 to 1, 0 to 4, 4 to 0, 2 to 2).forEach { (from, target) ->
            val visual = order.indices.map { i ->
                if (i == from) target.toFloat() else i + dragShift(i, from, target, 1f)
            }
            val shown = order.zip(visual).sortedBy { it.second }.map { it.first }
            val expected = order.toMutableList().apply { add(target, removeAt(from)) }
            assertEquals("de $from a $target", expected, shown)
        }
    }
}
