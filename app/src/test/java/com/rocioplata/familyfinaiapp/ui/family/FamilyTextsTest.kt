package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FamilyTextsTest {
    @Test
    fun cadaFalloConMensajePropioTieneSuTexto() {
        assertEquals(R.string.family_error_offline, FamilyAccessFailure.Offline.messageRes())
        assertEquals(R.string.family_error_network, FamilyAccessFailure.Network.messageRes())
        assertEquals(R.string.family_error_invalid_name, FamilyAccessFailure.InvalidName.messageRes())
        assertEquals(R.string.family_error_invalid_order, FamilyAccessFailure.InvalidOrder.messageRes())
        assertEquals(R.string.family_error_insufficient_role, FamilyAccessFailure.InsufficientRole.messageRes())
        assertEquals(R.string.family_error_not_a_member, FamilyAccessFailure.NotAMember.messageRes())
        assertEquals(R.string.family_error_last_owner, FamilyAccessFailure.LastOwner.messageRes())
        assertEquals(R.string.family_error_already_member, FamilyAccessFailure.AlreadyMember.messageRes())
        assertEquals(R.string.family_error_invalid_email, FamilyAccessFailure.InvalidEmail.messageRes())
        assertEquals(R.string.family_error_member_not_found, FamilyAccessFailure.MemberNotFound.messageRes())
        assertEquals(R.string.family_error_invitation_not_found, FamilyAccessFailure.InvitationNotFound.messageRes())
        assertEquals(R.string.family_error_invitation_expired, FamilyAccessFailure.InvitationExpired.messageRes())
        assertEquals(R.string.family_error_invitation_not_pending, FamilyAccessFailure.InvitationNotPending.messageRes())
        assertEquals(R.string.family_error_invitation_not_for_user, FamilyAccessFailure.InvitationNotForUser.messageRes())
        assertEquals(R.string.family_error_currency_locked, FamilyAccessFailure.CurrencyLocked.messageRes())
    }

    @Test
    fun losFallosSinMensajePropioUsanElGenerico() {
        listOf(FamilyAccessFailure.Unauthorized, FamilyAccessFailure.InvalidData, FamilyAccessFailure.Unknown).forEach {
            assertEquals(it.toString(), R.string.family_error_generic, it.messageRes())
        }
    }

    @Test
    fun losFallosConMensajePropioNoComparten_texto() {
        val specific = listOf(
            FamilyAccessFailure.Offline, FamilyAccessFailure.Network, FamilyAccessFailure.InvalidName,
            FamilyAccessFailure.InvalidOrder, FamilyAccessFailure.InsufficientRole, FamilyAccessFailure.NotAMember,
            FamilyAccessFailure.LastOwner, FamilyAccessFailure.AlreadyMember, FamilyAccessFailure.InvalidEmail,
            FamilyAccessFailure.MemberNotFound, FamilyAccessFailure.InvitationNotFound, FamilyAccessFailure.InvitationExpired,
            FamilyAccessFailure.InvitationNotPending, FamilyAccessFailure.InvitationNotForUser, FamilyAccessFailure.CurrencyLocked,
        ).map { it.messageRes() }
        assertEquals(specific.size, specific.toSet().size)
        assertNotEquals(R.string.family_error_generic, specific.first())
    }

    @Test
    fun losErroresDelNombreDeLaFamiliaTienenSuTexto() {
        assertEquals(R.string.family_create_error_required, FamilyCreateError.Required.messageRes())
        assertEquals(R.string.family_create_error_too_long, FamilyCreateError.TooLong.messageRes())
        assertEquals(R.string.family_create_error_emoji, FamilyCreateError.Emoji.messageRes())
        assertEquals(R.string.family_create_error_duplicate, FamilyCreateError.Duplicate.messageRes())
    }

    @Test
    fun unFalloAlCrearUsaElTextoDelFallo() {
        listOf(FamilyAccessFailure.Offline, FamilyAccessFailure.Network, FamilyAccessFailure.Unknown).forEach {
            assertEquals(it.toString(), it.messageRes(), FamilyCreateError.Failure(it).messageRes())
        }
    }

    @Test
    fun losErroresDelCorreoDeLaInvitacionTienenSuTexto() {
        assertEquals(R.string.family_invite_error_required, InviteFormError.Required.messageRes())
        assertEquals(R.string.family_invite_error_invalid, InviteFormError.InvalidFormat.messageRes())
        assertEquals(R.string.family_invite_error_too_long, InviteFormError.TooLong.messageRes())
        assertEquals(R.string.family_invite_error_already_invited, InviteFormError.AlreadyInvited.messageRes())
        assertEquals(R.string.family_invite_error_already_member, InviteFormError.AlreadyMember.messageRes())
    }

    @Test
    fun unFalloAlInvitarUsaElTextoDelFallo() {
        listOf(FamilyAccessFailure.Offline, FamilyAccessFailure.Network, FamilyAccessFailure.InvalidEmail).forEach {
            assertEquals(it.toString(), it.messageRes(), InviteFormError.Failure(it).messageRes())
        }
    }
}
