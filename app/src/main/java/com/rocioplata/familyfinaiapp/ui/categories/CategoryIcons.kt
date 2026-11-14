package com.rocioplata.familyfinaiapp.ui.categories

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector
import com.rocioplata.familyfinaiapp.R

/** Un icono del catálogo: su [key] estable es lo que guarda el backend (`icon`). */
data class CategoryIconOption(
    val key: String,
    val icon: ImageVector,
    @param:StringRes val descriptionRes: Int,
)

/**
 * Catálogo de los 15 iconos de categoría (RF-03, RF-11), en el orden del mockup 006. Sirve para mostrar
 * y para elegir. El backend no define claves propias: solo guarda las que envía la app, y las categorías
 * por defecto nacen sin icono. Una clave nula o que la app no conoce se muestra con el icono genérico.
 */
object CategoryIcons {
    val options: List<CategoryIconOption> = listOf(
        CategoryIconOption("shopping_cart", Icons.Filled.ShoppingCart, R.string.categories_icon_shopping_cart),
        CategoryIconOption("restaurant", Icons.Filled.Restaurant, R.string.categories_icon_restaurant),
        CategoryIconOption("local_cafe", Icons.Filled.LocalCafe, R.string.categories_icon_local_cafe),
        CategoryIconOption("medical_services", Icons.Filled.MedicalServices, R.string.categories_icon_medical_services),
        CategoryIconOption("home", Icons.Filled.Home, R.string.categories_icon_home),
        CategoryIconOption("directions_car", Icons.Filled.DirectionsCar, R.string.categories_icon_directions_car),
        CategoryIconOption("local_shipping", Icons.Filled.LocalShipping, R.string.categories_icon_local_shipping),
        CategoryIconOption("bolt", Icons.Filled.Bolt, R.string.categories_icon_bolt),
        CategoryIconOption("flight", Icons.Filled.Flight, R.string.categories_icon_flight),
        CategoryIconOption("fitness_center", Icons.Filled.FitnessCenter, R.string.categories_icon_fitness_center),
        CategoryIconOption("sports_soccer", Icons.Filled.SportsSoccer, R.string.categories_icon_sports_soccer),
        CategoryIconOption("beach_access", Icons.Filled.BeachAccess, R.string.categories_icon_beach_access),
        CategoryIconOption("school", Icons.Filled.School, R.string.categories_icon_school),
        CategoryIconOption("pets", Icons.Filled.Pets, R.string.categories_icon_pets),
        CategoryIconOption("card_giftcard", Icons.Filled.CardGiftcard, R.string.categories_icon_card_giftcard),
    )

    /** Se muestra cuando la categoría no tiene icono o tiene uno que la app no conoce. */
    val generic: CategoryIconOption = CategoryIconOption(
        key = "",
        icon = Icons.Filled.Category,
        descriptionRes = R.string.categories_icon_generic_description,
    )

    private val byKey = options.associateBy { it.key }

    /** La opción del catálogo para [key], o `null` si es nula o desconocida. */
    fun find(key: String?): CategoryIconOption? = key?.let(byKey::get)

    /** El icono a dibujar para [key]: el del catálogo o, si no hay, el genérico. */
    fun displayOf(key: String?): CategoryIconOption = find(key) ?: generic
}
