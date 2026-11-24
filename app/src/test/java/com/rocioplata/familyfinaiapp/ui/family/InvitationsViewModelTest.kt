package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.InvitationsRepository
import com.rocioplata.familyfinaiapp.domain.family.ReceivedInvitation
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationsViewModelTest {
    private val gomez = ReceivedInvitation("i-1", "f-1", "Familia Gómez", "Carolina", FamilyRole.OWNER, Instant.parse("2026-11-29T12:00:00Z"))
    private val depto = ReceivedInvitation("i-2", "f-2", "Departamento", null, FamilyRole.MEMBER, Instant.parse("2026-11-30T12:00:00Z"))

    /** Backend falso: `server` es lo que hay pendiente; un fallo configurado se devuelve en vez de aplicar. */
    private class FakeInvitations(initial: List<ReceivedInvitation>) : InvitationsRepository {
        val server = initial.toMutableList()
        var receivedFailure: FamilyAccessFailure? = null
        var actionFailure: FamilyAccessFailure? = null

        /** Si es verdadero, la invitación desaparece del backend aunque la acción falle (otro dispositivo, vencida…). */
        var removesOnFailure = false
        var receivedCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        var receivedGate: CompletableDeferred<Unit>? = null
        val accepted = mutableListOf<String>()
        val declined = mutableListOf<String>()

        override suspend fun received(): FamilyAccessResult<List<ReceivedInvitation>> {
            receivedCalls++
            receivedGate?.await()
            receivedFailure?.let { return FamilyAccessResult.Failure(it) }
            return FamilyAccessResult.Success(server.toList())
        }

        override suspend fun accept(invitation: ReceivedInvitation): FamilyAccessResult<Unit> {
            accepted += invitation.id
            return answer(invitation.id)
        }

        override suspend fun decline(invitationId: String): FamilyAccessResult<Unit> {
            declined += invitationId
            return answer(invitationId)
        }

        private suspend fun answer(id: String): FamilyAccessResult<Unit> {
            gate?.await()
            actionFailure?.let {
                if (removesOnFailure) server.removeAll { inv -> inv.id == id }
                return FamilyAccessResult.Failure(it)
            }
            server.removeAll { it.id == id }
            return FamilyAccessResult.Success(Unit)
        }
    }

    private class Rig(val vm: InvitationsViewModel, val repo: FakeInvitations, val connectivity: ControllableConnectivity)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun rig(initial: List<ReceivedInvitation> = listOf(gomez, depto), configure: FakeInvitations.() -> Unit = {}): Rig {
        val repo = FakeInvitations(initial).apply(configure)
        val connectivity = ControllableConnectivity()
        return Rig(InvitationsViewModel(repo, connectivity), repo, connectivity)
    }

    private val Rig.state get() = vm.uiState.value
    private val Rig.rows get() = (state.section as ReceivedSection.Content).rows

    // --- Carga ---

    @Test
    fun `al abrir carga la lista con sus datos`() = runTest {
        val rig = rig()
        assertTrue(rig.state.section is ReceivedSection.Loading)
        advanceUntilIdle()
        assertEquals(listOf("i-1", "i-2"), rig.rows.map { it.id })
        assertEquals(ReceivedInvitationRow("i-1", "f-1", "Familia Gómez", "Carolina", FamilyRole.OWNER, gomez.expiresAt), rig.rows[0])
        assertNull(rig.rows[1].invitedByName)
        assertEquals(1, rig.repo.receivedCalls)
    }

    @Test
    fun `sin invitaciones muestra el vacio`() = runTest {
        val rig = rig(emptyList())
        advanceUntilIdle()
        assertEquals(ReceivedSection.Empty, rig.state.section)
    }

    @Test
    fun `un fallo al cargar sin lista da error con reintentar`() = runTest {
        val rig = rig { receivedFailure = FamilyAccessFailure.Network }
        advanceUntilIdle()
        assertEquals(ReceivedSection.Error(FamilyAccessFailure.Network), rig.state.section)
        rig.repo.receivedFailure = null
        rig.vm.retry()
        advanceUntilIdle()
        assertEquals(2, rig.rows.size)
    }

    @Test
    fun `sin conexion al abrir da error Offline`() = runTest {
        val rig = rig { receivedFailure = FamilyAccessFailure.Offline }
        advanceUntilIdle()
        assertEquals(ReceivedSection.Error(FamilyAccessFailure.Offline), rig.state.section)
    }

    @Test
    fun `un fallo al recargar conserva la lista y avisa`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.repo.receivedFailure = FamilyAccessFailure.Network
        rig.vm.onStart()
        advanceUntilIdle()
        assertEquals(2, rig.rows.size)
        assertEquals(InvitationsEvent.Failed(FamilyAccessFailure.Network), rig.state.event)
        assertFalse(rig.state.isRefreshing)
    }

    @Test
    fun `al volver a primer plano recarga y marca el refresco`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.repo.server.add(0, ReceivedInvitation("i-3", "f-3", "Casa", "Ana", FamilyRole.MEMBER, gomez.expiresAt))
        rig.repo.receivedGate = CompletableDeferred()
        rig.vm.onStart()
        advanceUntilIdle()
        assertTrue(rig.state.isRefreshing)
        assertEquals(2, rig.rows.size)
        rig.repo.receivedGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(rig.state.isRefreshing)
        assertEquals(listOf("i-3", "i-1", "i-2"), rig.rows.map { it.id })
    }

    @Test
    fun `una carga en curso no se repite`() = runTest {
        val rig = rig { receivedGate = CompletableDeferred() }
        advanceUntilIdle()
        rig.vm.onStart()
        rig.vm.retry()
        advanceUntilIdle()
        assertEquals(1, rig.repo.receivedCalls)
        rig.repo.receivedGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, rig.rows.size)
    }

    @Test
    fun `al recuperar la conexion recarga y no al partir`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        assertEquals(1, rig.repo.receivedCalls)
        rig.connectivity.set(false)
        advanceUntilIdle()
        assertEquals(1, rig.repo.receivedCalls)
        rig.connectivity.set(true)
        advanceUntilIdle()
        assertEquals(2, rig.repo.receivedCalls)
    }

    // --- Aceptar (RF-19) ---

    @Test
    fun `aceptar quita la invitacion y ofrece cambiar de familia`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.vm.accept("i-1")
        advanceUntilIdle()
        assertEquals(listOf("i-1"), rig.repo.accepted)
        assertEquals(listOf("i-2"), rig.rows.map { it.id })
        assertEquals(InvitationsEvent.Accepted("f-1", "Familia Gómez"), rig.state.event)
        assertFalse(rig.state.isBusy)
    }

    @Test
    fun `aceptar la ultima deja el vacio`() = runTest {
        val rig = rig(listOf(gomez))
        advanceUntilIdle()
        rig.vm.accept("i-1")
        advanceUntilIdle()
        assertEquals(ReceivedSection.Empty, rig.state.section)
    }

    @Test
    fun `el aviso consumido desaparece`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.vm.accept("i-1")
        advanceUntilIdle()
        rig.vm.consumeEvent()
        assertNull(rig.state.event)
    }

    @Test
    fun `aceptar una invitacion desconocida no llama al backend`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.vm.accept("i-99")
        advanceUntilIdle()
        assertTrue(rig.repo.accepted.isEmpty())
    }

    @Test
    fun `la doble pulsacion de aceptar acepta una sola vez`() = runTest {
        val rig = rig { gate = CompletableDeferred() }
        advanceUntilIdle()
        rig.vm.accept("i-1")
        rig.vm.accept("i-1")
        assertEquals("i-1", rig.state.busyId)
        advanceUntilIdle()
        rig.repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("i-1"), rig.repo.accepted)
    }

    @Test
    fun `mientras se acepta una no se puede aceptar ni rechazar otra`() = runTest {
        val rig = rig { gate = CompletableDeferred() }
        advanceUntilIdle()
        rig.vm.accept("i-1")
        rig.vm.accept("i-2")
        rig.vm.requestDecline("i-2")
        advanceUntilIdle()
        assertNull(rig.state.pendingDecline)
        rig.repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("i-1"), rig.repo.accepted)
        assertTrue(rig.repo.declined.isEmpty())
    }

    @Test
    fun `sin conexion aceptar informa y deja la lista`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.repo.actionFailure = FamilyAccessFailure.Offline
        rig.vm.accept("i-1")
        advanceUntilIdle()
        assertEquals(InvitationsEvent.Failed(FamilyAccessFailure.Offline), rig.state.event)
        assertEquals(2, rig.rows.size)
        assertEquals(1, rig.repo.receivedCalls)
        assertFalse(rig.state.isBusy)
    }

    @Test
    fun `un fallo de red al aceptar informa sin recargar y se puede repetir`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.repo.actionFailure = FamilyAccessFailure.Network
        rig.vm.accept("i-1")
        advanceUntilIdle()
        assertEquals(InvitationsEvent.Failed(FamilyAccessFailure.Network), rig.state.event)
        assertEquals(1, rig.repo.receivedCalls)
        rig.repo.actionFailure = null
        rig.vm.accept("i-1")
        advanceUntilIdle()
        assertEquals(listOf("i-1", "i-1"), rig.repo.accepted)
        assertEquals(listOf("i-2"), rig.rows.map { it.id })
    }

    @Test
    fun `cada motivo por el que ya no se puede responder informa y recarga la lista`() = runTest {
        listOf(
            FamilyAccessFailure.InvitationNotFound,
            FamilyAccessFailure.InvitationExpired,
            FamilyAccessFailure.InvitationNotPending,
            FamilyAccessFailure.InvitationNotForUser,
        ).forEach { failure ->
            val rig = rig {
                actionFailure = failure
                removesOnFailure = true
            }
            advanceUntilIdle()
            rig.vm.accept("i-1")
            advanceUntilIdle()
            assertEquals("$failure", InvitationsEvent.Failed(failure), rig.state.event)
            assertEquals("$failure", 2, rig.repo.receivedCalls)
            assertEquals("$failure", listOf("i-2"), rig.rows.map { it.id })
            assertFalse("$failure", rig.state.isBusy)
        }
    }

    @Test
    fun `si la recarga tras el fallo tambien falla se conserva el motivo`() = runTest {
        val rig = rig {
            actionFailure = FamilyAccessFailure.InvitationExpired
        }
        advanceUntilIdle()
        rig.repo.receivedFailure = FamilyAccessFailure.Network
        rig.vm.accept("i-1")
        advanceUntilIdle()
        assertEquals(InvitationsEvent.Failed(FamilyAccessFailure.InvitationExpired), rig.state.event)
        assertEquals(2, rig.rows.size)
    }

    @Test
    fun `un fallo desconocido informa y no recarga`() = runTest {
        val rig = rig { actionFailure = FamilyAccessFailure.Unknown }
        advanceUntilIdle()
        rig.vm.accept("i-2")
        advanceUntilIdle()
        assertEquals(InvitationsEvent.Failed(FamilyAccessFailure.Unknown), rig.state.event)
        assertEquals(1, rig.repo.receivedCalls)
    }

    // --- Rechazar (RF-20) ---

    @Test
    fun `rechazar pide confirmacion antes de llamar al backend`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.vm.requestDecline("i-2")
        assertEquals("i-2", rig.state.pendingDecline?.id)
        advanceUntilIdle()
        assertTrue(rig.repo.declined.isEmpty())
    }

    @Test
    fun `cancelar el rechazo no cambia nada`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.vm.requestDecline("i-2")
        rig.vm.dismissDecline()
        advanceUntilIdle()
        assertNull(rig.state.pendingDecline)
        assertTrue(rig.repo.declined.isEmpty())
        assertEquals(2, rig.rows.size)
        assertNull(rig.state.event)
    }

    @Test
    fun `confirmar el rechazo quita la invitacion`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.vm.requestDecline("i-2")
        rig.vm.confirmDecline()
        advanceUntilIdle()
        assertEquals(listOf("i-2"), rig.repo.declined)
        assertEquals(listOf("i-1"), rig.rows.map { it.id })
        assertNull(rig.state.pendingDecline)
        assertEquals(InvitationsEvent.Declined, rig.state.event)
    }

    @Test
    fun `confirmar sin haber pedido el rechazo no hace nada`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.vm.confirmDecline()
        advanceUntilIdle()
        assertTrue(rig.repo.declined.isEmpty())
    }

    @Test
    fun `la doble confirmacion rechaza una sola vez`() = runTest {
        val rig = rig { gate = CompletableDeferred() }
        advanceUntilIdle()
        rig.vm.requestDecline("i-2")
        rig.vm.confirmDecline()
        rig.vm.confirmDecline()
        advanceUntilIdle()
        rig.repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("i-2"), rig.repo.declined)
    }

    @Test
    fun `no se pide otro rechazo con uno pendiente`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.vm.requestDecline("i-1")
        rig.vm.requestDecline("i-2")
        assertEquals("i-1", rig.state.pendingDecline?.id)
    }

    @Test
    fun `sin conexion rechazar informa y conserva la lista`() = runTest {
        val rig = rig()
        advanceUntilIdle()
        rig.repo.actionFailure = FamilyAccessFailure.Offline
        rig.vm.requestDecline("i-1")
        rig.vm.confirmDecline()
        advanceUntilIdle()
        assertEquals(InvitationsEvent.Failed(FamilyAccessFailure.Offline), rig.state.event)
        assertNull(rig.state.pendingDecline)
        assertEquals(2, rig.rows.size)
    }

    @Test
    fun `rechazar una ya respondida en otro dispositivo informa y recarga`() = runTest {
        val rig = rig {
            actionFailure = FamilyAccessFailure.InvitationNotPending
            removesOnFailure = true
        }
        advanceUntilIdle()
        rig.vm.requestDecline("i-1")
        rig.vm.confirmDecline()
        advanceUntilIdle()
        assertEquals(InvitationsEvent.Failed(FamilyAccessFailure.InvitationNotPending), rig.state.event)
        assertEquals(listOf("i-2"), rig.rows.map { it.id })
        assertEquals(2, rig.repo.receivedCalls)
    }

    @Test
    fun `mientras se rechaza no se recarga con el volver a primer plano`() = runTest {
        val rig = rig { gate = CompletableDeferred() }
        advanceUntilIdle()
        rig.vm.requestDecline("i-1")
        rig.vm.confirmDecline()
        rig.vm.onStart()
        advanceUntilIdle()
        assertEquals(1, rig.repo.receivedCalls)
        rig.repo.gate!!.complete(Unit)
        advanceUntilIdle()
    }
}
