package com.rocioplata.familyfinaiapp.ui.categories

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.ui.theme.CategoryAmberDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryAmberLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryBrickDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryBrickLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryCoralDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryCoralLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryGraphiteDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryGraphiteLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryIndigoDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryIndigoLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryLavenderDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryLavenderLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryMauveDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryMauveLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryMustardDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryMustardLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryOceanDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryOceanLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryOliveDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryOliveLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryPlumDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryPlumLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryRoseDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryRoseLight
import com.rocioplata.familyfinaiapp.ui.theme.CategorySageDark
import com.rocioplata.familyfinaiapp.ui.theme.CategorySageLight
import com.rocioplata.familyfinaiapp.ui.theme.CategorySandDark
import com.rocioplata.familyfinaiapp.ui.theme.CategorySandLight
import com.rocioplata.familyfinaiapp.ui.theme.CategorySlateDark
import com.rocioplata.familyfinaiapp.ui.theme.CategorySlateLight
import com.rocioplata.familyfinaiapp.ui.theme.CategoryTealDark
import com.rocioplata.familyfinaiapp.ui.theme.CategoryTealLight

/** Un tono de la paleta en sus dos versiones: la de modo claro y la de modo oscuro. */
data class CategoryTone(val light: Color, val dark: Color) {
    fun forMode(dark: Boolean): Color = if (dark) this.dark else light
}

/** Un color del catálogo: su [key] estable es lo que guarda el backend (`color`) y [nameRes] lo que se anuncia. */
data class CategoryColorOption(
    val key: String,
    @param:StringRes val nameRes: Int,
    val tone: CategoryTone,
)

/** El color de este tono para el modo claro u oscuro que muestra el tema actual. */
@Composable
fun CategoryColorOption.displayColor(): Color = tone.forMode(dark = MaterialTheme.colorScheme.background.luminance() < 0.5f)

/** Fondo suave de un icono o tarjeta de categoría: el mismo color con transparencia, sin valores adicionales. */
fun Color.softBackground(): Color = copy(alpha = SOFT_BACKGROUND_ALPHA)

/** Negro o blanco, el que más contraste da sobre este color (la marca de «seleccionado» sobre un color). */
fun Color.readableOn(): Color = if (luminance() > 0.4f) Color.Black else Color.White

private const val SOFT_BACKGROUND_ALPHA = 0.16f

/**
 * Catálogo de los 16 colores de categoría (RF-14), en el orden del selector y del respaldo por posición:
 * alterna tonos cálidos, fríos y neutros para que categorías vecinas no queden con colores parecidos. El
 * backend solo guarda la clave (BG-01 de la 008). Una categoría sin color, o con una clave que la app no
 * conoce, usa el color de la posición que ocupa (ver [fallback] y [positionOf]).
 */
object CategoryColors {
    val options: List<CategoryColorOption> = listOf(
        CategoryColorOption("sage", R.string.categories_color_sage, CategoryTone(CategorySageLight, CategorySageDark)),
        CategoryColorOption("amber", R.string.categories_color_amber, CategoryTone(CategoryAmberLight, CategoryAmberDark)),
        CategoryColorOption("slate", R.string.categories_color_slate, CategoryTone(CategorySlateLight, CategorySlateDark)),
        CategoryColorOption("coral", R.string.categories_color_coral, CategoryTone(CategoryCoralLight, CategoryCoralDark)),
        CategoryColorOption("plum", R.string.categories_color_plum, CategoryTone(CategoryPlumLight, CategoryPlumDark)),
        CategoryColorOption("olive", R.string.categories_color_olive, CategoryTone(CategoryOliveLight, CategoryOliveDark)),
        CategoryColorOption("ocean", R.string.categories_color_ocean, CategoryTone(CategoryOceanLight, CategoryOceanDark)),
        CategoryColorOption("rose", R.string.categories_color_rose, CategoryTone(CategoryRoseLight, CategoryRoseDark)),
        CategoryColorOption("sand", R.string.categories_color_sand, CategoryTone(CategorySandLight, CategorySandDark)),
        CategoryColorOption("indigo", R.string.categories_color_indigo, CategoryTone(CategoryIndigoLight, CategoryIndigoDark)),
        CategoryColorOption("brick", R.string.categories_color_brick, CategoryTone(CategoryBrickLight, CategoryBrickDark)),
        CategoryColorOption("teal", R.string.categories_color_teal, CategoryTone(CategoryTealLight, CategoryTealDark)),
        CategoryColorOption("lavender", R.string.categories_color_lavender, CategoryTone(CategoryLavenderLight, CategoryLavenderDark)),
        CategoryColorOption("mustard", R.string.categories_color_mustard, CategoryTone(CategoryMustardLight, CategoryMustardDark)),
        CategoryColorOption("mauve", R.string.categories_color_mauve, CategoryTone(CategoryMauveLight, CategoryMauveDark)),
        CategoryColorOption("graphite", R.string.categories_color_graphite, CategoryTone(CategoryGraphiteLight, CategoryGraphiteDark)),
    )

    private val byKey = options.associateBy { it.key }

    /** La opción del catálogo para [key], o `null` si es nula o desconocida. */
    fun find(key: String?): CategoryColorOption? = key?.let(byKey::get)

    /** El color de respaldo para una [position] cualquiera: el catálogo se recorre de forma cíclica. */
    fun fallback(position: Int): CategoryColorOption = options[Math.floorMod(position, options.size)]

    /** El color a dibujar: el de la clave si el catálogo la conoce y, si no, el de [position]. */
    fun resolve(key: String?, position: Int): CategoryColorOption = find(key) ?: fallback(position)

    /**
     * Posición de [category] entre las categorías de su mismo tipo, en el orden de [categories] (el del
     * repositorio, que es el de la gestión), contando también las archivadas para que archivar una no cambie
     * el color de las demás. Así la misma categoría tiene el mismo color en el dashboard y en la gestión.
     * Una categoría que no está en la lista cuenta como la primera.
     */
    fun positionOf(category: Category, categories: List<Category>): Int =
        categories.filter { it.type == category.type }.indexOfFirst { it.id == category.id }.coerceAtLeast(0)

    /** Color final de [category] dentro de [categories]: su clave o, si no la tiene, el de su posición. */
    fun resolve(category: Category, categories: List<Category>): CategoryColorOption =
        resolve(category.color, positionOf(category, categories))
}
