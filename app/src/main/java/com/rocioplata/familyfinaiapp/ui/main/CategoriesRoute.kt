package com.rocioplata.familyfinaiapp.ui.main

import com.rocioplata.familyfinaiapp.domain.categories.CategoryType

/**
 * Pantalla de gestión de categorías abierta sobre las pestañas (RF-01). No hay librería de navegación:
 * la ruta vive en memoria en [MainViewModel], por lo que sobrevive a la recreación y se pierde al
 * terminar el proceso (la app reabre en Inicio). Sin ruta (`null`) se muestran las pestañas.
 */
sealed interface CategoriesRoute {
    data object List : CategoriesRoute

    /** Alta con el tipo preseleccionado según la pestaña visible (RF-09). */
    data class Create(val type: CategoryType) : CategoriesRoute

    data class Edit(val categoryId: String) : CategoriesRoute
}
