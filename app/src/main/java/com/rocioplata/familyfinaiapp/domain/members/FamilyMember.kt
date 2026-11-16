package com.rocioplata.familyfinaiapp.domain.members

import com.rocioplata.familyfinaiapp.domain.header.FamilyRole

/**
 * Miembro actual de una familia (RF-15). Solo lo necesario para mostrar quién registró un movimiento: el
 * correo no se lee ni se guarda.
 */
data class FamilyMember(
    val userId: String,
    /** Nulo o vacío si el miembro no tiene nombre; la interfaz muestra un texto genérico. */
    val displayName: String?,
    val role: FamilyRole,
)

/** Miembros de una familia; `members` es nulo mientras no haya valor válido (ni caché ni backend). */
data class FamilyMembersData(
    val members: List<FamilyMember>? = null,
    /** La última descarga tiene más de 24 h (RF-13). */
    val isStale: Boolean = false,
)
