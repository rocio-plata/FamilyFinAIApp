package com.rocioplata.familyfinaiapp.domain.family

import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FamilyInvitationTest {
    private val expiresAt = Instant.parse("2026-11-29T12:00:00Z")
    private val sent = SentInvitation("i-1", "hermano@example.com", FamilyRole.MEMBER, expiresAt)

    @Test
    fun unaInvitacionVigenteHastaSuFechaInclusive_yVencidaDespues() {
        assertFalse(sent.isExpired(expiresAt.minusMillis(1)))
        assertFalse(sent.isExpired(expiresAt))
        assertTrue(sent.isExpired(expiresAt.plusMillis(1)))
    }

    @Test
    fun toString_noIncluyeElCorreoNiLosNombres() {
        val received = ReceivedInvitation("i-2", "f-1", "Familia Gómez", "Carolina Soto", FamilyRole.OWNER, expiresAt)

        assertFalse(sent.toString().contains("hermano"))
        assertFalse(sent.toString().contains("example.com"))
        assertFalse(received.toString().contains("Gómez"))
        assertFalse(received.toString().contains("Carolina"))
        assertTrue(sent.toString().contains("i-1"))
        assertTrue(received.toString().contains("i-2"))
    }
}
