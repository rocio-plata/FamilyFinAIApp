package com.rocioplata.familyfinaiapp.ui.main

/**
 * Pantallas de la gestión de familias (RF-03, RF-04, RF-18 de la 009), a pantalla completa y sin cabecera ni barra
 * inferior. Son [ManagementRoute]: viven en memoria en [MainViewModel] y al cerrarlas se vuelve a la pestaña desde la
 * que se abrieron.
 */
sealed interface FamilyRoute : ManagementRoute {
    /** «Mi familia» de la familia activa con la que se abrió; no se abre otra mientras está abierta. */
    data object Manage : FamilyRoute

    /** Formulario de «Crear nueva familia»; no depende de ninguna familia y se abre también sin ellas (RF-24). */
    data object Create : FamilyRoute
}

/** «Invitaciones» recibidas (RF-18): no depende de ninguna familia y se abre también sin ellas (RF-24). */
data object InvitationsRoute : ManagementRoute
