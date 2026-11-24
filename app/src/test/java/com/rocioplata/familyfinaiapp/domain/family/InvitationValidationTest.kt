package com.rocioplata.familyfinaiapp.domain.family

import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class InvitationValidationTest {
    private val now = Instant.parse("2026-11-22T12:00:00Z")

    private fun sent(email: String, expiresAt: Instant) = SentInvitation("i-$email", email, FamilyRole.MEMBER, expiresAt)

    private fun validate(email: String, vararg sent: SentInvitation) =
        InvitationValidation.validateEmail(email, sent.toList(), now)

    private fun invalid(error: InvitationEmailError) = InvitationEmailValidation.Invalid(error)

    @Test
    fun correoValido_seDevuelveRecortado() {
        assertEquals(InvitationEmailValidation.Valid("a@b.cl"), validate("  a@b.cl "))
    }

    @Test
    fun vacioOSoloEspacios_esObligatorio() {
        assertEquals(invalid(InvitationEmailError.REQUIRED), validate(""))
        assertEquals(invalid(InvitationEmailError.REQUIRED), validate("   "))
    }

    @Test
    fun formatoInvalido_sinArrobaSinPuntoOConEspacios() {
        listOf("sin-arroba", "a@b", "a b@c.cl", "@c.cl", "a@@c.cl").forEach {
            assertEquals(it, invalid(InvitationEmailError.INVALID_FORMAT), validate(it))
        }
    }

    @Test
    fun limiteDe255_seAceptaConLos255YSeRechazaConLos256() {
        val ok = "a".repeat(255 - "@b.cl".length) + "@b.cl"
        assertEquals(255, ok.length)
        assertEquals(InvitationEmailValidation.Valid(ok), validate(ok))
        assertEquals(invalid(InvitationEmailError.TOO_LONG), validate("a$ok"))
    }

    @Test
    fun conInvitacionVigenteAlMismoCorreo_seRechazaTambienConOtrasMayusculas() {
        val vigente = sent("Hermano@Example.com", now.plusSeconds(60))

        assertEquals(invalid(InvitationEmailError.ALREADY_INVITED), validate("hermano@example.com", vigente))
        assertEquals(invalid(InvitationEmailError.ALREADY_INVITED), validate("  HERMANO@EXAMPLE.COM ", vigente))
    }

    @Test
    fun conInvitacionVencida_sePuedeInvitarDeNuevo() {
        val vencida = sent("hermano@example.com", now.minusMillis(1))

        assertEquals(InvitationEmailValidation.Valid("hermano@example.com"), validate("hermano@example.com", vencida))
    }

    @Test
    fun laInvitacionQueVenceJustoAhora_todaviaImpideInvitar() {
        val enSuInstante = sent("hermano@example.com", now)

        assertEquals(invalid(InvitationEmailError.ALREADY_INVITED), validate("hermano@example.com", enSuInstante))
    }

    @Test
    fun otroCorreoConInvitacionVigente_esValido() {
        assertEquals(
            InvitationEmailValidation.Valid("otro@example.com"),
            validate("otro@example.com", sent("hermano@example.com", now.plusSeconds(60))),
        )
    }
}
