package com.rocioplata.familyfinaiapp.ui.categories

import androidx.compose.ui.graphics.Color
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.ui.theme.AppSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class CategoryColorsTest {
    private val expectedKeys = listOf("sage", "amber", "slate", "coral", "plum", "olive", "ocean", "rose", "sand", "indigo", "brick", "teal", "lavender", "mustard", "mauve", "graphite")

    // Superficie de las tarjetas en modo oscuro: `ColorInverseSurface` de `ui/theme/Theme.kt` (es privado).
    private val darkSurface = Color(0xFF263331)

    private fun category(id: String, type: CategoryType = CategoryType.EXPENSE, color: String? = null) =
        Category(id, type, "Cat $id", null, CategoryStatus.ACTIVE, emptyList(), color)

    @Test
    fun elCatalogoTieneLos16ColoresEnElOrdenDelPlan() {
        assertEquals(expectedKeys, CategoryColors.options.map { it.key })
    }

    @Test
    fun lasClavesSonUnicasEnMinusculasYNoVacias() {
        val keys = CategoryColors.options.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.isNotBlank() && it == it.lowercase() && it.length <= 40 })
    }

    @Test
    fun cadaColorTieneUnNombrePropioParaElLectorDePantalla() {
        val names = CategoryColors.options.map { it.nameRes }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun cadaTonoTieneVersionClaraYOscuraDistintas_yNingunoSeRepite() {
        assertTrue(CategoryColors.options.all { it.tone.light != it.tone.dark })
        val all = CategoryColors.options.flatMap { listOf(it.tone.light, it.tone.dark) }
        assertEquals(all.size, all.toSet().size)
        CategoryColors.options.forEach {
            assertEquals(it.tone.light, it.tone.forMode(dark = false))
            assertEquals(it.tone.dark, it.tone.forMode(dark = true))
        }
    }

    @Test
    fun cadaColorSuperaElContrasteMinimoDe3a1ConLaSuperficieDeSuModo() {
        CategoryColors.options.forEach {
            val light = contrast(it.tone.light, AppSurface)
            val dark = contrast(it.tone.dark, darkSurface)
            assertTrue("${it.key} claro: $light", light >= 3.0)
            assertTrue("${it.key} oscuro: $dark", dark >= 3.0)
        }
    }

    @Test
    fun claveConocida_seUsaSeaCualSeaLaPosicion() {
        listOf(0, 5, 16, 99).forEach {
            assertEquals("coral", CategoryColors.resolve("coral", it).key)
        }
        assertEquals("coral", CategoryColors.find("coral")?.key)
    }

    @Test
    fun claveNulaODesconocida_usaElColorDeLaPosicion() {
        assertNull(CategoryColors.find(null))
        assertNull(CategoryColors.find("fucsia_99"))
        assertNull(CategoryColors.find("SAGE"))
        assertEquals("amber", CategoryColors.resolve(null, 1).key)
        assertEquals("slate", CategoryColors.resolve("fucsia_99", 2).key)
        assertEquals("sage", CategoryColors.resolve("", 0).key)
    }

    @Test
    fun elRespaldoEsCiclico_posiciones0_15_16_17() {
        assertEquals("sage", CategoryColors.fallback(0).key)
        assertEquals("graphite", CategoryColors.fallback(15).key)
        assertEquals("sage", CategoryColors.fallback(16).key)
        assertEquals("amber", CategoryColors.fallback(17).key)
        assertEquals("graphite", CategoryColors.fallback(-1).key)
    }

    @Test
    fun laPosicionSoloCuentaCategoriasDelMismoTipo() {
        val list = listOf(
            category("g1"), category("i1", CategoryType.INCOME), category("g2"),
            category("i2", CategoryType.INCOME), category("g3"),
        )

        assertEquals(listOf(0, 1, 2), list.filter { it.type == CategoryType.EXPENSE }.map { CategoryColors.positionOf(it, list) })
        assertEquals(listOf(0, 1), list.filter { it.type == CategoryType.INCOME }.map { CategoryColors.positionOf(it, list) })
    }

    @Test
    fun laPosicionNoCambiaPorLasCategoriasDeOtroTipo() {
        val gastos = listOf(category("g1"), category("g2"), category("g3"))
        val mezcladas = listOf(
            category("i0", CategoryType.INCOME), category("g1"), category("i1", CategoryType.INCOME),
            category("g2"), category("g3"), category("i2", CategoryType.INCOME),
        )

        gastos.forEach { assertEquals(CategoryColors.positionOf(it, gastos), CategoryColors.positionOf(it, mezcladas)) }
    }

    @Test
    fun archivarUnaCategoriaNoCambiaElColorDeLasDemas() {
        val active = listOf(category("g1"), category("g2"), category("g3"))
        val archived = active.mapIndexed { i, c -> if (i == 0) c.copy(status = CategoryStatus.DEPRECATED) else c }

        assertEquals(
            active.map { CategoryColors.resolve(it, active).key },
            archived.map { CategoryColors.resolve(it, archived).key },
        )
    }

    @Test
    fun categoriaFueraDeLaLista_cuentaComoLaPrimera() {
        assertEquals(0, CategoryColors.positionOf(category("fuera"), listOf(category("g1"), category("g2"))))
        assertEquals(0, CategoryColors.positionOf(category("fuera"), emptyList()))
    }

    @Test
    fun resolverUnaCategoria_usaSuClaveOSuPosicion() {
        val list = listOf(category("g1", color = "plum"), category("g2"), category("g3", color = "fucsia_99"))

        assertEquals(listOf("plum", "amber", "slate"), list.map { CategoryColors.resolve(it, list).key })
    }

    @Test
    fun conMasDe16Categorias_elColorDeLaPosicion17EsElDeLaPosicion1() {
        val list = (0 until 20).map { category("g$it") }

        assertEquals(CategoryColors.resolve(list[1], list).key, CategoryColors.resolve(list[17], list).key)
        assertFalse(CategoryColors.resolve(list[0], list).key == CategoryColors.resolve(list[1], list).key)
    }

    // Contraste WCAG 2.x entre dos colores opacos.
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
