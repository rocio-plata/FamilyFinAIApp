package com.rocioplata.familyfinaiapp.domain.header

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarInitialTest {
    @Test
    fun nombreEnMinuscula_devuelveInicialEnMayuscula() {
        assertEquals("R", avatarInitial("rocío"))
    }

    @Test
    fun nombreConEspaciosIniciales_ignoraLosEspacios() {
        assertEquals("R", avatarInitial("  Rocío Gómez"))
    }

    @Test
    fun inicialAcentuada_seConservaEnMayuscula() {
        assertEquals("É", avatarInitial("émile"))
    }

    @Test
    fun codePointFueraDelBMP_noSeCorta() {
        assertEquals("\uD835\uDC00", avatarInitial("\uD835\uDC00bc"))
    }

    @Test
    fun nombreVacioEnBlancoONulo_noTieneInicial() {
        assertNull(avatarInitial(""))
        assertNull(avatarInitial("   "))
        assertNull(avatarInitial(null))
    }
}
