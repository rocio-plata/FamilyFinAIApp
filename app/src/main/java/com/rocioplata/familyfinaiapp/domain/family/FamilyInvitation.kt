package com.rocioplata.familyfinaiapp.domain.family

import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import java.time.Instant

/**
 * Invitación que el Propietario envió y sigue pendiente en el backend (RF-15, RF-16). El backend no cambia su estado al
 * vencer: se decide por la fecha. `toString()` no incluye el correo.
 */
data class SentInvitation(
    val id: String,
    val email: String,
    val role: FamilyRole,
    val expiresAt: Instant,
) {
    /** Vencida desde un instante después de [expiresAt]: en el propio instante todavía vale, como en el backend. */
    fun isExpired(now: Instant): Boolean = expiresAt.isBefore(now)

    override fun toString() = "SentInvitation(id=$id, role=$role)"
}

/**
 * Invitación recibida y vigente (RF-18). [invitedByName] es nulo si el backend no conoce el nombre de quien invita.
 * `toString()` no incluye nombres.
 */
data class ReceivedInvitation(
    val id: String,
    val familyId: String,
    val familyName: String,
    val invitedByName: String?,
    val role: FamilyRole,
    val expiresAt: Instant,
) {
    override fun toString() = "ReceivedInvitation(id=$id, role=$role)"
}
