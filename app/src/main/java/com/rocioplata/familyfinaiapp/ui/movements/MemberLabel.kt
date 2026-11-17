package com.rocioplata.familyfinaiapp.ui.movements

import androidx.annotation.StringRes
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember

/** Quién registró un movimiento, resuelto contra la sesión y la lista de miembros (RF-15, CA-24). */
sealed interface MemberLabel {
    /** Rol en la familia; solo el detalle lo muestra. Nulo si no se conoce o ya no es miembro. */
    val role: FamilyRole? get() = null

    /** Iniciales para el avatar; nulas si no hay un nombre del que sacarlas. */
    val initials: String? get() = null

    /** El propio usuario. Se reconoce por el `userId` de la sesión aunque la lista de miembros no esté. */
    data class You(val displayName: String?, override val role: FamilyRole?) : MemberLabel {
        override val initials: String? get() = initialsOf(displayName)
    }

    data class Named(val displayName: String, override val role: FamilyRole) : MemberLabel {
        override val initials: String get() = initialsOf(displayName) ?: ""
    }

    /** Es miembro pero su nombre viene nulo o vacío. */
    data class NoName(override val role: FamilyRole) : MemberLabel

    /** Ya no pertenece a la familia: sin rol. */
    data object FormerMember : MemberLabel

    /** No se pudo obtener la lista de miembros: el detalle lo indica y el listado lo omite. */
    data object Unavailable : MemberLabel
}

/**
 * [members] es nulo mientras no haya lista válida. Nunca se usa el correo: `FamilyMember` ni siquiera lo trae.
 */
fun resolveMemberLabel(recordedBy: String, currentUserId: String?, members: List<FamilyMember>?): MemberLabel {
    val member = members?.firstOrNull { it.userId == recordedBy }
    return when {
        currentUserId != null && recordedBy == currentUserId ->
            MemberLabel.You(member?.displayName?.trim()?.takeIf { it.isNotEmpty() }, member?.role)
        members == null -> MemberLabel.Unavailable
        member == null -> MemberLabel.FormerMember
        else -> {
            val name = member.displayName?.trim()?.takeIf { it.isNotEmpty() }
            if (name == null) MemberLabel.NoName(member.role) else MemberLabel.Named(name, member.role)
        }
    }
}

/**
 * Primera letra de las dos primeras palabras del nombre, en mayúscula ("Rocío Plata" → "RP", "Rocío" → "R");
 * nulo si no hay nombre.
 */
fun initialsOf(displayName: String?): String? =
    displayName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .takeIf { it.isNotEmpty() }

/**
 * Texto de [MemberLabel] o nulo si no hay nada que mostrar ([MemberLabel.Unavailable]: el listado lo omite y
 * el detalle muestra [R.string.movements_recorded_by_unavailable]). [getString] resuelve un recurso.
 */
fun MemberLabel.text(getString: (Int) -> String): String? = when (this) {
    is MemberLabel.You -> getString(R.string.movements_recorded_by_you)
    is MemberLabel.Named -> displayName
    is MemberLabel.NoName -> getString(R.string.movements_recorded_by_generic)
    MemberLabel.FormerMember -> getString(R.string.movements_recorded_by_former)
    MemberLabel.Unavailable -> null
}

@StringRes
fun FamilyRole.labelRes(): Int = when (this) {
    FamilyRole.OWNER -> R.string.role_owner
    FamilyRole.MEMBER -> R.string.role_member
}
