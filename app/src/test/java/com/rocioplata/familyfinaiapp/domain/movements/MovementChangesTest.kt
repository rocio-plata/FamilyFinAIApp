package com.rocioplata.familyfinaiapp.domain.movements

import com.rocioplata.familyfinaiapp.domain.money.Money
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementChangesTest {
    @Test
    fun sinCambios_estaVacio_yNoHayNadaQueGuardar() {
        val changes = MovementChanges()

        assertTrue(changes.isEmpty)
        assertFalse(changes.hasDataChanges)
    }

    @Test
    fun cadaCampoDeDatos_cuentaComoCambioDeDatos() {
        listOf(
            MovementChanges(amount = Money(1_000, "CLP")),
            MovementChanges(title = "Nuevo"),
            MovementChanges(note = NoteUpdate.Set("Texto")),
            MovementChanges(note = NoteUpdate.Remove),
            MovementChanges(date = LocalDate.of(2026, 10, 23)),
            MovementChanges(paymentMethodId = "m-2"),
        ).forEach {
            assertTrue(it.toString(), it.hasDataChanges)
            assertFalse(it.toString(), it.isEmpty)
        }
    }

    @Test
    fun soloLaCategoria_noEsCambioDeDatos_peroNoEstaVacio() {
        val changes = MovementChanges(category = CategoryChange("c-2", tagId = null))

        assertFalse(changes.hasDataChanges)
        assertFalse(changes.isEmpty)
    }

    @Test
    fun datosYCategoria_sonAmbos() {
        val changes = MovementChanges(title = "Nuevo", category = CategoryChange("c-2", "t-1"))

        assertTrue(changes.hasDataChanges)
        assertFalse(changes.isEmpty)
        assertEquals("t-1", changes.category?.tagId)
    }
}
