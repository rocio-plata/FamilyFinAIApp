package com.rocioplata.familyfinaiapp.ui.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CategoryIconsTest {
    private val expectedKeys = listOf(
        "shopping_cart", "restaurant", "local_cafe", "medical_services", "home",
        "directions_car", "local_shipping", "bolt", "flight", "fitness_center",
        "sports_soccer", "beach_access", "school", "pets", "card_giftcard",
    )

    @Test
    fun elCatalogoTieneLos15IconosEnElOrdenDelMockup() {
        assertEquals(expectedKeys, CategoryIcons.options.map { it.key })
    }

    @Test
    fun lasClavesSonUnicasYNoVacias() {
        val keys = CategoryIcons.options.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertFalse(keys.any { it.isBlank() })
    }

    @Test
    fun cadaIconoTieneUnaDescripcionPropiaDistintaDeLaGenerica() {
        val descriptions = CategoryIcons.options.map { it.descriptionRes }
        assertEquals(descriptions.size, descriptions.toSet().size)
        assertFalse(CategoryIcons.generic.descriptionRes in descriptions)
    }

    @Test
    fun cadaIconoDelCatalogoEsDistintoDelGenerico() {
        CategoryIcons.options.forEach { assertNotEquals(it.key, CategoryIcons.generic.icon, it.icon) }
    }

    @Test
    fun claveConocida_devuelveSuIcono() {
        assertSame(Icons.Filled.ShoppingCart, CategoryIcons.displayOf("shopping_cart").icon)
        assertSame(Icons.Filled.Home, CategoryIcons.displayOf("home").icon)
        expectedKeys.forEach { assertEquals(it, CategoryIcons.displayOf(it).key) }
    }

    @Test
    fun claveNula_muestraElGenerico() {
        assertSame(CategoryIcons.generic, CategoryIcons.displayOf(null))
        assertNull(CategoryIcons.find(null))
    }

    @Test
    fun claveDesconocida_muestraElGenerico() {
        listOf("", " ", "no_existe", "SHOPPING_CART", "shopping_cart ", "home\n").forEach {
            assertSame(it, CategoryIcons.generic, CategoryIcons.displayOf(it))
            assertNull(it, CategoryIcons.find(it))
        }
    }

    @Test
    fun elGenericoNoEsUnaOpcionElegible() {
        assertFalse(CategoryIcons.options.any { it.key == CategoryIcons.generic.key })
        assertEquals("", CategoryIcons.generic.key)
    }
}
