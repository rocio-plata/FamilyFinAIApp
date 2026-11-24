package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.data.family.FakeHeaderRepository
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilyManagementRepository
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
import com.rocioplata.familyfinaiapp.domain.header.HeaderRefreshResult
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
class FamilyCreateViewModelTest {
    private val plata = Family("f-1", "Familia Plata", FamilyRole.OWNER, "CLP", 3)
    private val nueva = Family("f-9", "Casa de Campo", FamilyRole.OWNER, "CLP", 1)

    /** Solo crea; registra los nombres enviados. */
    private class FakeManagement(private val header: FakeHeaderRepository) : FamilyManagementRepository {
        val created = mutableListOf<String>()
        var result: (String) -> FamilyAccessResult<Family> = { name ->
            FamilyAccessResult.Success(Family("f-new-${created.size}", name, FamilyRole.OWNER, "CLP", 1))
        }
        var gate: CompletableDeferred<Unit>? = null

        /** Si es verdadero, la familia llega a crearse en el backend aunque la respuesta falle. */
        var appliesBeforeFailing = false

        override suspend fun createFamily(name: String): FamilyAccessResult<Family> {
            created += name
            gate?.await()
            val outcome = result(name)
            if (outcome is FamilyAccessResult.Success) header.addFamily(outcome.value)
            if (outcome is FamilyAccessResult.Failure && appliesBeforeFailing) {
                header.addFamily(Family("f-late", name, FamilyRole.OWNER, "CLP", 1))
            }
            return outcome
        }

        override suspend fun reorderFamilies(orderedFamilyIds: List<String>) = error("no se usa")
        override suspend fun settings(familyId: String) = error("no se usa")
        override suspend fun changeCurrency(familyId: String, currency: String) = error("no se usa")
        override suspend fun changeMemberRole(familyId: String, memberId: String, newRole: FamilyRole) = error("no se usa")
        override suspend fun removeMember(familyId: String, memberId: String) = error("no se usa")
        override suspend fun leaveFamily(familyId: String) = error("no se usa")
        override suspend fun sentInvitations(familyId: String) = error("no se usa")
        override suspend fun invite(familyId: String, email: String, role: FamilyRole) = error("no se usa")
        override suspend fun revokeInvitation(invitationId: String) = error("no se usa")
    }

    private class Rig(val vm: FamilyCreateViewModel, val header: FakeHeaderRepository, val management: FakeManagement) {
        val createdFamilies = mutableListOf<Family>()
        var closes = 0
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun rig(families: List<Family>? = listOf(plata)): Rig {
        val header = FakeHeaderRepository(families)
        val management = FakeManagement(header)
        lateinit var rig: Rig
        val vm = FamilyCreateViewModel(management, header, onCreated = { rig.createdFamilies += it }, onClose = { rig.closes++ })
        rig = Rig(vm, header, management)
        return rig
    }

    private fun Rig.type(text: String) = vm.onNameChange(text)

    private val Rig.state get() = vm.uiState.value

    // --- Validación ---

    @Test
    fun nombreVacioOSoloEspacios_esObligatorio_yNoLlamaAlBackend() = runTest {
        val rig = rig()

        rig.vm.save()
        advanceUntilIdle()
        assertEquals(FamilyCreateError.Required, rig.state.error)

        rig.type("   ")
        rig.vm.save()
        advanceUntilIdle()
        assertEquals(FamilyCreateError.Required, rig.state.error)
        assertEquals(emptyList<String>(), rig.management.created)
    }

    @Test
    fun nombreConEmoji_seRechaza_yConservaLoEscrito() = runTest {
        val rig = rig()
        rig.type("Casa 🏠")

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(FamilyCreateError.Emoji, rig.state.error)
        assertEquals("Casa 🏠", rig.state.text)
        assertEquals(emptyList<String>(), rig.management.created)
    }

    @Test
    fun nombreRepetido_conOtrasMayusculasYEspacios_seRechaza() = runTest {
        val rig = rig()
        rig.type("  familia PLATA ")

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(FamilyCreateError.Duplicate, rig.state.error)
        assertEquals(emptyList<String>(), rig.management.created)
    }

    @Test
    fun elNombreNoPasaDe60Caracteres() = runTest {
        val rig = rig()

        rig.type("a".repeat(80))

        assertEquals(60, rig.state.text.length)
    }

    @Test
    fun editarElTexto_quitaElError() = runTest {
        val rig = rig()
        rig.vm.save()
        advanceUntilIdle()
        assertEquals(FamilyCreateError.Required, rig.state.error)

        rig.type("C")

        assertNull(rig.state.error)
    }

    // --- Éxito y fallos ---

    @Test
    fun conNombreValido_creaConElNombreRecortado_yAvisaDeLaFamiliaCreada() = runTest {
        val rig = rig()
        rig.type("  Casa de Campo  ")

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(listOf("Casa de Campo"), rig.management.created)
        assertEquals(listOf("Casa de Campo"), rig.createdFamilies.map { it.name })
        assertNull(rig.state.error)
        assertEquals(0, rig.closes)
    }

    @Test
    fun sinConexion_informa_noCreaNada_yConservaLoEscrito() = runTest {
        val rig = rig()
        rig.management.result = { FamilyAccessResult.Failure(FamilyAccessFailure.Offline) }
        rig.type("Casa de Campo")

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(FamilyCreateError.Failure(FamilyAccessFailure.Offline), rig.state.error)
        assertEquals("Casa de Campo", rig.state.text)
        assertFalse(rig.state.isSaving)
        assertEquals(emptyList<Family>(), rig.createdFamilies)
    }

    @Test
    fun unFalloDelBackend_seMuestraConSuMotivo_yPermiteReintentar() = runTest {
        val rig = rig()
        var attempts = 0
        rig.management.result = { name ->
            if (attempts++ == 0) FamilyAccessResult.Failure(FamilyAccessFailure.InvalidName)
            else FamilyAccessResult.Success(Family("f-2", name, FamilyRole.OWNER, "CLP", 1))
        }
        rig.type("Casa")
        rig.vm.save()
        advanceUntilIdle()
        assertEquals(FamilyCreateError.Failure(FamilyAccessFailure.InvalidName), rig.state.error)

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(listOf("f-2"), rig.createdFamilies.map { it.id })
    }

    // --- Fallo de red incierto ---

    @Test
    fun trasUnFalloDeRed_siLaFamiliaYaSeCreo_elReintentoNoLaCreaDeNuevo() = runTest {
        val rig = rig()
        rig.management.appliesBeforeFailing = true
        rig.management.result = { FamilyAccessResult.Failure(FamilyAccessFailure.Network) }
        rig.type("Casa de Campo")
        rig.vm.save()
        advanceUntilIdle()
        assertEquals(FamilyCreateError.Failure(FamilyAccessFailure.Network), rig.state.error)

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(1, rig.management.created.size)
        assertEquals(listOf("f-late"), rig.createdFamilies.map { it.id })
        assertEquals(1, rig.header.refreshes)
    }

    @Test
    fun trasUnFalloDeRed_siLaFamiliaNoSeCreo_elReintentoLaCreaDespuesDeComprobar() = runTest {
        val rig = rig()
        var attempts = 0
        rig.management.result = { name ->
            if (attempts++ == 0) FamilyAccessResult.Failure(FamilyAccessFailure.Network)
            else FamilyAccessResult.Success(Family("f-2", name, FamilyRole.OWNER, "CLP", 1))
        }
        rig.type("Casa de Campo")
        rig.vm.save()
        advanceUntilIdle()

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(listOf("Casa de Campo", "Casa de Campo"), rig.management.created)
        assertEquals(listOf("f-2"), rig.createdFamilies.map { it.id })
        assertEquals(1, rig.header.refreshes)
    }

    @Test
    fun trasUnFalloDeRed_siNoSePuedeComprobar_noSeEnvia() = runTest {
        val rig = rig()
        rig.management.result = { FamilyAccessResult.Failure(FamilyAccessFailure.Network) }
        rig.type("Casa de Campo")
        rig.vm.save()
        advanceUntilIdle()
        rig.header.refreshResult = HeaderRefreshResult(families = HeaderFailure.Network)

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(1, rig.management.created.size)
        assertEquals(FamilyCreateError.Failure(FamilyAccessFailure.Network), rig.state.error)
        assertEquals("Casa de Campo", rig.state.text)
    }

    @Test
    fun trasUnFalloDeRed_laComprobacionPuedeRepetirseHastaQueFuncione() = runTest {
        val rig = rig()
        rig.management.result = { FamilyAccessResult.Failure(FamilyAccessFailure.Network) }
        rig.type("Casa de Campo")
        rig.vm.save()
        advanceUntilIdle()
        rig.header.refreshResult = HeaderRefreshResult(families = HeaderFailure.Network)
        rig.vm.save()
        advanceUntilIdle()

        rig.header.refreshResult = HeaderRefreshResult()
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(2, rig.management.created.size)
        assertEquals(2, rig.header.refreshes)
    }

    @Test
    fun trasUnFalloDeRed_aunqueSeCambieElTexto_manda_loQueHayaEnElBackend() = runTest {
        val rig = rig()
        rig.management.appliesBeforeFailing = true
        rig.management.result = { FamilyAccessResult.Failure(FamilyAccessFailure.Network) }
        rig.type("Casa de Campo")
        rig.vm.save()
        advanceUntilIdle()

        rig.type("Otro nombre")
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(1, rig.management.created.size)
        assertEquals(listOf("Casa de Campo"), rig.createdFamilies.map { it.name })
    }

    @Test
    fun unFalloQueNoEsDeRed_noExigeComprobarNada() = runTest {
        val rig = rig()
        var attempts = 0
        rig.management.result = { name ->
            if (attempts++ == 0) FamilyAccessResult.Failure(FamilyAccessFailure.Offline)
            else FamilyAccessResult.Success(Family("f-2", name, FamilyRole.OWNER, "CLP", 1))
        }
        rig.type("Casa")
        rig.vm.save()
        advanceUntilIdle()

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(0, rig.header.refreshes)
        assertEquals(2, rig.management.created.size)
    }

    // --- Una operación a la vez ---

    @Test
    fun dobleToqueEnCrear_unaSolaLlamada() = runTest {
        val rig = rig()
        rig.management.gate = CompletableDeferred()
        rig.type("Casa de Campo")

        rig.vm.save()
        rig.vm.save()
        advanceUntilIdle()
        assertTrue(rig.state.isSaving)
        rig.vm.save()
        rig.management.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, rig.management.created.size)
        assertEquals(1, rig.createdFamilies.size)
        assertFalse(rig.state.isSaving)
    }

    @Test
    fun mientrasSeCrea_noSeEditaElTextoNiSePuedeSalir() = runTest {
        val rig = rig()
        rig.management.gate = CompletableDeferred()
        rig.type("Casa")
        rig.vm.save()
        advanceUntilIdle()

        rig.type("Otra cosa")
        rig.vm.requestClose()
        rig.vm.confirmDiscard()

        assertEquals("Casa", rig.state.text)
        assertFalse(rig.state.confirmingDiscard)
        assertEquals(0, rig.closes)
        rig.management.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    // --- Descarte ---

    @Test
    fun salirSinNadaEscrito_cierraDirectamente() = runTest {
        val rig = rig()

        rig.vm.requestClose()

        assertEquals(1, rig.closes)
        assertFalse(rig.state.confirmingDiscard)
    }

    @Test
    fun salirConSoloEspacios_cuentaComoSinCambios() = runTest {
        val rig = rig()
        rig.type("   ")

        rig.vm.requestClose()

        assertEquals(1, rig.closes)
    }

    @Test
    fun salirConElNombreEscrito_pideConfirmacion_ySeguirConservaLoEscrito() = runTest {
        val rig = rig()
        rig.type("Casa")

        rig.vm.requestClose()
        assertTrue(rig.state.confirmingDiscard)
        assertEquals(0, rig.closes)

        rig.vm.dismissDiscard()
        assertFalse(rig.state.confirmingDiscard)
        assertEquals("Casa", rig.state.text)
        assertEquals(0, rig.closes)
    }

    @Test
    fun confirmarElDescarte_cierra() = runTest {
        val rig = rig()
        rig.type("Casa")
        rig.vm.requestClose()

        rig.vm.confirmDiscard()

        assertEquals(1, rig.closes)
    }

    @Test
    fun confirmarSinHaberPedidoSalir_noCierra() = runTest {
        val rig = rig()
        rig.type("Casa")

        rig.vm.confirmDiscard()

        assertEquals(0, rig.closes)
    }

    @Test
    fun conLaConfirmacionAbierta_nadaMasSeProcesa() = runTest {
        val rig = rig()
        rig.type("Casa")
        rig.vm.requestClose()

        rig.vm.save()
        rig.type("Otra")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), rig.management.created)
        assertEquals("Casa", rig.state.text)
    }

    @Test
    fun sinListaDeFamiliasCargada_seValidaSinNombresPrevios() = runTest {
        val rig = rig(families = null)
        rig.type("Familia Plata")

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(listOf("Familia Plata"), rig.management.created)
    }
}
