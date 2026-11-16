package com.rocioplata.familyfinaiapp.ui.main

import com.rocioplata.familyfinaiapp.domain.categories.CategoryType

/**
 * Gestión de categorías (RF-01 de 005): listado, alta o edición de una categoría. Es una [ManagementRoute]:
 * vive en memoria en [MainViewModel], por lo que sobrevive a la recreación y se pierde al terminar el
 * proceso (la app reabre en Inicio).
 */
sealed interface CategoriesRoute : ManagementRoute {
    data object List : CategoriesRoute

    /** Alta con el tipo preseleccionado según la pestaña visible (RF-09). */
    data class Create(val type: CategoryType) : CategoriesRoute

    data class Edit(val categoryId: String) : CategoriesRoute
}
