package com.rocioplata.familyfinaiapp.ui.main

import com.rocioplata.familyfinaiapp.domain.movements.MovementType

/**
 * Pantallas de un movimiento (RF-04, RF-08 y RF-09), a pantalla completa y sin cabecera ni barra inferior.
 * Son [ManagementRoute]: viven en memoria en [MainViewModel], ligadas a la familia con la que se abrieron,
 * y al cerrarlas se vuelve a la pestaña desde la que se abrieron.
 */
sealed interface MovementRoute : ManagementRoute {
    /**
     * "Nuevo movimiento": el botón "+" de Inicio y de Movimientos lo abre vacío; una tarjeta de categoría del
     * dashboard lo abre con el [type] y la [categoryId] ya elegidos (RF-11 de la 008).
     */
    data class Create(val categoryId: String? = null, val type: MovementType? = null) : MovementRoute

    /** Detalle de un movimiento del listado. */
    data class Detail(val movementId: String) : MovementRoute

    /** Edición; solo se abre desde el detalle del mismo movimiento y atrás vuelve a él. */
    data class Edit(val movementId: String) : MovementRoute
}
