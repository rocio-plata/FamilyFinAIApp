package com.rocioplata.familyfinaiapp.ui.main

/**
 * Pantalla de gestión abierta sobre las pestañas, a pantalla completa y sin cabecera ni barra inferior
 * (RF-01 de 005 y de 006). Es una sola ruta en memoria en [MainViewModel], por lo que dos gestiones
 * nunca están abiertas a la vez; sin ruta (`null`) se muestran las pestañas.
 */
sealed interface ManagementRoute

/** Listado de medios de pago de la familia activa; el alta y el renombrado son un diálogo sobre él. */
data object PaymentMethodsRoute : ManagementRoute
