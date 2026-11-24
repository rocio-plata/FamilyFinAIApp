package com.rocioplata.familyfinaiapp.ui.family

import androidx.annotation.StringRes
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure

/** Mensaje de cada fallo de la gestión de familias, para las pantallas que no lo muestran de otra forma (BG-06). */
@StringRes
fun FamilyAccessFailure.messageRes(): Int = when (this) {
    FamilyAccessFailure.Offline -> R.string.family_error_offline
    FamilyAccessFailure.Network -> R.string.family_error_network
    FamilyAccessFailure.InvalidName -> R.string.family_error_invalid_name
    FamilyAccessFailure.InvalidOrder -> R.string.family_error_invalid_order
    FamilyAccessFailure.InsufficientRole -> R.string.family_error_insufficient_role
    FamilyAccessFailure.NotAMember -> R.string.family_error_not_a_member
    FamilyAccessFailure.LastOwner -> R.string.family_error_last_owner
    FamilyAccessFailure.AlreadyMember -> R.string.family_error_already_member
    FamilyAccessFailure.InvalidEmail -> R.string.family_error_invalid_email
    FamilyAccessFailure.MemberNotFound -> R.string.family_error_member_not_found
    FamilyAccessFailure.InvitationNotFound -> R.string.family_error_invitation_not_found
    FamilyAccessFailure.InvitationExpired -> R.string.family_error_invitation_expired
    FamilyAccessFailure.InvitationNotPending -> R.string.family_error_invitation_not_pending
    FamilyAccessFailure.InvitationNotForUser -> R.string.family_error_invitation_not_for_user
    FamilyAccessFailure.CurrencyLocked -> R.string.family_error_currency_locked
    FamilyAccessFailure.Unauthorized, FamilyAccessFailure.InvalidData, FamilyAccessFailure.Unknown ->
        R.string.family_error_generic
}

@StringRes
fun FamilyCreateError.messageRes(): Int = when (this) {
    FamilyCreateError.Required -> R.string.family_create_error_required
    FamilyCreateError.TooLong -> R.string.family_create_error_too_long
    FamilyCreateError.Emoji -> R.string.family_create_error_emoji
    FamilyCreateError.Duplicate -> R.string.family_create_error_duplicate
    is FamilyCreateError.Failure -> failure.messageRes()
}

@StringRes
fun InviteFormError.messageRes(): Int = when (this) {
    InviteFormError.Required -> R.string.family_invite_error_required
    InviteFormError.InvalidFormat -> R.string.family_invite_error_invalid
    InviteFormError.TooLong -> R.string.family_invite_error_too_long
    InviteFormError.AlreadyInvited -> R.string.family_invite_error_already_invited
    InviteFormError.AlreadyMember -> R.string.family_invite_error_already_member
    is InviteFormError.Failure -> failure.messageRes()
}
