package com.rocioplata.familyfinaiapp.domain.family

/** Por qué el backend no deja cambiar la moneda de la familia (RF-13). */
enum class CurrencyLockReason { MOVEMENTS, BUDGETS }

/**
 * Ajustes de la familia (RF-13). [isLocked] con [lockReasons] vacío es un bloqueo cuyo motivo la app no conoce
 * (un motivo nuevo del backend): nunca se interpreta como desbloqueada.
 */
data class FamilySettings(
    val currency: String,
    val isLocked: Boolean,
    val lockReasons: Set<CurrencyLockReason>,
)
