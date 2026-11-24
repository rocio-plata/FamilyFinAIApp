package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.data.family.FakeHeaderRepository
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class FamilyManagementViewModelTest {
    private val me = "u-1"
    private val plata = Family("f-1", "Familia Plata", FamilyRole.OWNER, "CLP", memberCount = 9)
    private val depto = Family("f-2", "Depto", FamilyRole.MEMBER, "CLP", memberCount = 2)

    private val valentina = FamilyMember("u-1", "Valentina Plata", FamilyRole.OWNER)
    private val carlos = FamilyMember("u-2", "Carlos Mendoza", FamilyRole.OWNER)
    private val sofia = FamilyMember("u-3", "Sofía Silva", FamilyRole.MEMBER)
    private val ana = FamilyMember("u-4", "ana Pérez", FamilyRole.MEMBER)
    private val sinNombre = FamilyMember("u-5", null, FamilyRole.MEMBER)
    private val four = listOf(sofia, valentina, carlos, ana)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Rig(
        val vm: FamilyManagementViewModel,
        val header: FakeHeaderRepository,
        val members: ControllableMembers,
        val management: RecordingManagement,
        val connectivity: ControllableConnectivity,
    ) {
        var insufficientRole = 0
    }

    private fun TestScope.rig(
        initial: List<FamilyMember>? = four,
        families: List<Family>? = listOf(plata, depto),
        stale: Boolean = false,
        online: Boolean = true,
        currentUserId: String? = me,
        familyId: String = "f-1",
        configure: (ControllableMembers) -> Unit = {},
    ): Rig {
        val header = FakeHeaderRepository(families)
        val members = ControllableMembers(initial, stale).also(configure)
        val management = RecordingManagement(header, members, currentUserId)
        val connectivity = ControllableConnectivity(online)
        lateinit var rig: Rig
        val vm = FamilyManagementViewModel(
            familyId, currentUserId, header, members, management, connectivity, onInsufficientRole = { rig.insufficientRole++ },
        )
        rig = Rig(vm, header, members, management, connectivity)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return rig
    }

    private val Rig.state get() = vm.uiState.value

    private fun Rig.rows() = (state.members as MembersSection.Content).rows

    // --- Orden y datos ---

    @Test
    fun laListaVaConElPropioUsuarioPrimero_despuesPropietariosEIntegrantesPorNombre() = runTest {
        val rig = rig()

        assertEquals(listOf("u-1", "u-2", "u-4", "u-3"), rig.rows().map { it.userId })
    }

    @Test
    fun elPropioUsuarioSeMarcaComoTu_yNadieMasSiNoSeConoce() = runTest {
        val rig = rig()
        assertEquals(listOf("u-1"), rig.rows().filter { it.isYou }.map { it.userId })

        val unknown = rig(currentUserId = null)
        assertEquals(emptyList<String>(), unknown.rows().filter { it.isYou }.map { it.userId })
    }

    @Test
    fun quienNoTieneNombre_vaAlFinalDeSuGrupo_yConservaElNombreNulo() = runTest {
        val rig = rig(initial = four + sinNombre)

        assertEquals(listOf("u-1", "u-2", "u-4", "u-3", "u-5"), rig.rows().map { it.userId })
        assertNull(rig.rows().last().displayName)
    }

    @Test
    fun elOrdenNoDistingueMayusculasNiTildes() = runTest {
        val a = FamilyMember("u-6", "álvaro", FamilyRole.MEMBER)
        val b = FamilyMember("u-7", "Beatriz", FamilyRole.MEMBER)
        val rig = rig(initial = listOf(valentina, b, a))

        assertEquals(listOf("u-1", "u-6", "u-7"), rig.rows().map { it.userId })
    }

    @Test
    fun laFamiliaSaleDeLaCabecera_conElNumeroDeMiembrosDeLaListaSiEstaCargada() = runTest {
        val rig = rig()

        assertEquals(FamilyInfo("Familia Plata", "CLP", FamilyRole.OWNER, memberCount = 4), rig.state.family)
    }

    @Test
    fun sinListaCargada_elNumeroDeMiembrosEsElDeLaCabecera() = runTest {
        val rig = rig(initial = null) { it.gate = CompletableDeferred() }

        assertEquals(9, rig.state.family?.memberCount)
    }

    @Test
    fun laFamiliaSeActualizaEnVivoConLaCabecera() = runTest {
        val rig = rig()

        rig.header.updateFamily("f-1") { it.copy(currency = "USD") }
        advanceUntilIdle()

        assertEquals("USD", rig.state.family?.currency)
    }

    @Test
    fun siLaCabeceraNoTieneLaFamilia_noHayDatosDeFamilia() = runTest {
        val rig = rig(familyId = "f-9")

        assertNull(rig.state.family)
        assertFalse(rig.state.isOwner)
    }

    // --- Estados de la sección de miembros ---

    @Test
    fun conCopiaAntigua_laListaSeMarcaComoDesactualizada() = runTest {
        val rig = rig(stale = true)

        assertTrue((rig.state.members as MembersSection.Content).isStale)
    }

    @Test
    fun sinListaYMientrasCarga_estaCargando() = runTest {
        val rig = rig(initial = null) { it.gate = CompletableDeferred() }

        assertEquals(MembersSection.Loading, rig.state.members)
    }

    @Test
    fun sinListaYConFalloDeRed_hayErrorSoloEnLaSeccion_yReintentarFunciona() = runTest {
        val rig = rig(initial = null) { it.failure = FamilyMembersFailure.Network }
        assertEquals(MembersSection.Error, rig.state.members)
        assertEquals("Familia Plata", rig.state.family?.name)

        rig.members.failure = null
        rig.members.remote = four
        rig.vm.retry()
        advanceUntilIdle()

        assertEquals(4, rig.rows().size)
    }

    @Test
    fun conListaYFalloDeRed_sigueLaLista_sinError() = runTest {
        val rig = rig { it.failure = FamilyMembersFailure.Network }

        assertEquals(4, rig.rows().size)
    }

    @Test
    fun siElBackendDiceQueYaNoEresMiembro_seAvisa() = runTest {
        val rig = rig(initial = null) { it.failure = FamilyMembersFailure.NotAllowed }

        assertEquals(1, rig.insufficientRole)
        assertEquals(MembersSection.Error, rig.state.members)
    }

    // --- Correos y rol ---

    @Test
    fun elPropietarioConConexion_pideYVeLosCorreos() = runTest {
        val rig = rig { it.emails = mapOf("u-1" to "v@example.com", "u-3" to "s@example.com") }

        assertEquals(1, rig.members.refreshesWithEmails)
        assertEquals(0, rig.members.refreshes)
        assertEquals(mapOf("u-1" to "v@example.com", "u-3" to "s@example.com"), rig.rows().filter { it.email != null }.associate { it.userId to it.email })
        assertTrue(rig.state.isOwner)
    }

    @Test
    fun unIntegrante_pideSoloLaLista_yNuncaVeCorreos() = runTest {
        val rig = rig(familyId = "f-2") { it.emails = mapOf("u-1" to "v@example.com") }

        assertEquals(0, rig.members.refreshesWithEmails)
        assertEquals(1, rig.members.refreshes)
        assertEquals(emptyList<String?>(), rig.rows().mapNotNull { it.email })
        assertFalse(rig.state.isOwner)
    }

    @Test
    fun sinConexion_losCorreosNoSeMuestran_yAlVolverSeVuelvenAPedir() = runTest {
        val rig = rig { it.emails = mapOf("u-1" to "v@example.com") }
        assertEquals("v@example.com", rig.rows().first { it.userId == "u-1" }.email)

        rig.connectivity.set(false)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), rig.rows().mapNotNull { it.email })

        rig.connectivity.set(true)
        advanceUntilIdle()
        assertEquals(2, rig.members.refreshesWithEmails)
        assertEquals("v@example.com", rig.rows().first { it.userId == "u-1" }.email)
    }

    @Test
    fun abriendoSinConexion_aunqueLleguenCorreos_noSeMuestran_yLaListaSiSeVe() = runTest {
        val rig = rig(online = false) { it.emails = mapOf("u-1" to "v@example.com") }

        assertEquals(emptyList<String>(), rig.rows().mapNotNull { it.email })
        assertEquals(4, rig.rows().size)
    }

    @Test
    fun siElPropietarioPasaAIntegrante_laPantallaPasaASoloLecturaYSeOcultanLosCorreos() = runTest {
        val rig = rig { it.emails = mapOf("u-1" to "v@example.com") }
        assertTrue(rig.rows().any { it.canChangeRole })

        rig.header.updateFamily("f-1") { it.copy(role = FamilyRole.MEMBER) }
        advanceUntilIdle()

        assertFalse(rig.state.isOwner)
        assertEquals(FamilyRole.MEMBER, rig.state.family?.role)
        assertTrue(rig.rows().none { it.canChangeRole || it.canRemove })
        assertEquals(emptyList<String>(), rig.rows().mapNotNull { it.email })

        rig.header.updateFamily("f-1") { it.copy(role = FamilyRole.OWNER) }
        advanceUntilIdle()
        assertTrue(rig.state.isOwner)
        assertEquals("v@example.com", rig.rows().first { it.userId == "u-1" }.email)
    }

    // --- Qué acciones hay en cada fila ---

    @Test
    fun alUnicoPropietario_noSeLeOfreceDejarDeSerlo_peroSiAlResto() = runTest {
        val rig = rig(initial = listOf(valentina, sofia))

        val byId = rig.rows().associateBy { it.userId }
        assertFalse(byId.getValue("u-1").canChangeRole)
        assertTrue(byId.getValue("u-3").canChangeRole)
        assertEquals(FamilyRole.OWNER, byId.getValue("u-3").newRole)
        assertEquals(FamilyRole.MEMBER, byId.getValue("u-1").newRole)
    }

    @Test
    fun conDosPropietarios_cualquieraPuedeDejarDeSerlo_tambienElPropio() = runTest {
        val rig = rig()

        val byId = rig.rows().associateBy { it.userId }
        assertTrue(byId.getValue("u-1").canChangeRole)
        assertTrue(byId.getValue("u-2").canChangeRole)
    }

    @Test
    fun seQuitaAOtrasPersonas_nuncaAUnoMismo() = runTest {
        val rig = rig()

        val byId = rig.rows().associateBy { it.userId }
        assertFalse(byId.getValue("u-1").canRemove)
        assertTrue(byId.getValue("u-2").canRemove)
        assertTrue(byId.getValue("u-3").canRemove)
    }

    // --- Salir ---

    @Test
    fun salir_unPropietarioConOtroPropietario_esta_permitido() = runTest {
        assertEquals(LeaveState.Allowed, rig().state.leave)
    }

    @Test
    fun salir_laUnicaFamilia_noSePuede() = runTest {
        assertEquals(LeaveState.OnlyFamily, rig(families = listOf(plata)).state.leave)
        assertEquals(LeaveState.OnlyFamily, rig(families = listOf(depto), familyId = "f-2").state.leave)
    }

    @Test
    fun salir_siEsElUnicoPropietario_noSePuede() = runTest {
        assertEquals(LeaveState.OnlyOwner, rig(initial = listOf(valentina, sofia)).state.leave)
    }

    @Test
    fun salir_unPropietarioSinLista_necesitaConexion() = runTest {
        val rig = rig(initial = null) { it.failure = FamilyMembersFailure.Network }

        assertEquals(LeaveState.NeedsConnection, rig.state.leave)
    }

    @Test
    fun salir_unIntegrante_puedeAunqueNoHayaLista() = runTest {
        val rig = rig(initial = null, familyId = "f-2") { it.failure = FamilyMembersFailure.Network }

        assertEquals(LeaveState.Allowed, rig.state.leave)
    }

    // --- Cuándo se refresca ---

    @Test
    fun alAbrir_refrescaUnaVez_yOnStartVuelveAPedirlo() = runTest {
        val rig = rig()
        assertEquals(1, rig.members.refreshesWithEmails)

        rig.vm.onStart()
        advanceUntilIdle()

        assertEquals(2, rig.members.refreshesWithEmails)
    }

    @Test
    fun conUnaCargaEnCurso_otraPeticionSeIgnora() = runTest {
        val rig = rig(initial = null) { it.gate = CompletableDeferred() }

        rig.vm.onStart()
        rig.vm.retry()
        advanceUntilIdle()
        rig.members.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, rig.members.refreshesWithEmails)
    }

    @Test
    fun alRecuperarLaConexion_refresca_peroNoEnLaPrimeraEmision() = runTest {
        val rig = rig()
        assertEquals(1, rig.members.refreshesWithEmails)

        rig.connectivity.set(false)
        advanceUntilIdle()
        assertEquals(1, rig.members.refreshesWithEmails)
        rig.connectivity.set(true)
        advanceUntilIdle()

        assertEquals(2, rig.members.refreshesWithEmails)
    }

    // --- Acciones con confirmación (T-22) ---

    @Test
    fun cambiarElRolDeOtro_pideConfirmacion_yAlConfirmarLoAplica() = runTest {
        val rig = rig()

        rig.vm.requestChangeRole("u-3")
        advanceUntilIdle()
        assertEquals(FamilyPendingAction.ChangeRole("u-3", "Sofía Silva", FamilyRole.OWNER, isYou = false), rig.state.pendingAction)
        assertEquals(emptyList<Triple<String, String, FamilyRole>>(), rig.management.roleChanges)

        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(listOf(Triple("f-1", "u-3", FamilyRole.OWNER)), rig.management.roleChanges)
        assertNull(rig.state.pendingAction)
        assertFalse(rig.state.isBusy)
        assertEquals(FamilyRole.OWNER, rig.rows().first { it.userId == "u-3" }.role)
        assertNull(rig.state.event)
    }

    @Test
    fun cambiarElRol_cancelar_noCambiaNada() = runTest {
        val rig = rig()
        rig.vm.requestChangeRole("u-3")

        rig.vm.dismissPendingAction()
        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertNull(rig.state.pendingAction)
        assertEquals(emptyList<Triple<String, String, FamilyRole>>(), rig.management.roleChanges)
    }

    @Test
    fun dejarDeSerPropietario_elPropioRol_dejaLaPantallaEnSoloLectura() = runTest {
        val rig = rig()
        rig.vm.requestChangeRole("u-1")
        advanceUntilIdle()
        assertEquals(FamilyPendingAction.ChangeRole("u-1", "Valentina Plata", FamilyRole.MEMBER, isYou = true), rig.state.pendingAction)

        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertFalse(rig.state.isOwner)
        assertEquals(FamilyRole.MEMBER, rig.state.family?.role)
        assertTrue(rig.rows().none { it.canChangeRole || it.canRemove })
        assertEquals(LeaveState.Allowed, rig.state.leave)
    }

    @Test
    fun alUnicoPropietario_noSeLePideConfirmacionParaDejarDeSerlo() = runTest {
        val rig = rig(initial = listOf(valentina, sofia))

        rig.vm.requestChangeRole("u-1")
        advanceUntilIdle()

        assertNull(rig.state.pendingAction)
        assertEquals(emptyList<Triple<String, String, FamilyRole>>(), rig.management.roleChanges)
    }

    @Test
    fun unIntegrante_noPuedeCambiarRolesNiQuitar() = runTest {
        val rig = rig(familyId = "f-2")

        rig.vm.requestChangeRole("u-3")
        rig.vm.requestRemove("u-3")
        advanceUntilIdle()

        assertNull(rig.state.pendingAction)
    }

    @Test
    fun quitarAOtro_pideConfirmacion_yAlConfirmarSaleDeLaLista() = runTest {
        val rig = rig()

        rig.vm.requestRemove("u-3")
        advanceUntilIdle()
        assertEquals(FamilyPendingAction.Remove("u-3", "Sofía Silva"), rig.state.pendingAction)
        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(listOf("f-1" to "u-3"), rig.management.removals)
        assertEquals(listOf("u-1", "u-2", "u-4"), rig.rows().map { it.userId })
        assertEquals(3, rig.state.family?.memberCount)
    }

    @Test
    fun quitar_cancelar_noCambiaNada_yUnoMismoNoSePuedeQuitar() = runTest {
        val rig = rig()
        rig.vm.requestRemove("u-3")
        rig.vm.dismissPendingAction()
        rig.vm.requestRemove("u-1")
        advanceUntilIdle()

        assertNull(rig.state.pendingAction)
        assertEquals(emptyList<Pair<String, String>>(), rig.management.removals)
    }

    @Test
    fun salir_pideConfirmacion_yAlConfirmarLaFamiliaDesaparece() = runTest {
        val rig = rig()

        rig.vm.requestLeave()
        advanceUntilIdle()
        assertEquals(FamilyPendingAction.Leave("Familia Plata"), rig.state.pendingAction)
        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(listOf("f-1"), rig.management.leaves)
        assertNull(rig.state.family)
    }

    @Test
    fun salir_cancelar_noHaceNada() = runTest {
        val rig = rig()
        rig.vm.requestLeave()

        rig.vm.dismissPendingAction()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), rig.management.leaves)
        assertEquals("Familia Plata", rig.state.family?.name)
    }

    @Test
    fun salir_deshabilitado_ignoraLaPeticion_enCadaMotivo() = runTest {
        val unica = rig(families = listOf(plata))
        unica.vm.requestLeave()
        val unicoPropietario = rig(initial = listOf(valentina, sofia))
        unicoPropietario.vm.requestLeave()
        val sinLista = rig(initial = null) { it.failure = FamilyMembersFailure.Network }
        sinLista.vm.requestLeave()
        advanceUntilIdle()

        listOf(unica, unicoPropietario, sinLista).forEach {
            assertNull(it.state.pendingAction)
            assertEquals(emptyList<String>(), it.management.leaves)
        }
    }

    @Test
    fun unIntegrante_puedeSalir() = runTest {
        val rig = rig(familyId = "f-2")

        rig.vm.requestLeave()
        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(listOf("f-2"), rig.management.leaves)
    }

    // --- Fallos ---

    private fun TestScope.failing(failure: FamilyAccessFailure): Rig {
        val rig = rig()
        rig.management.failure = failure
        rig.vm.requestRemove("u-3")
        rig.vm.confirmPendingAction()
        advanceUntilIdle()
        return rig
    }

    @Test
    fun elUltimoPropietario_seInformaYSeVuelveALeerLaLista() = runTest {
        val rig = rig()
        rig.management.failure = FamilyAccessFailure.LastOwner
        val before = rig.members.refreshesWithEmails
        rig.vm.requestChangeRole("u-1")
        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(FamilyManagementEvent.Failed(FamilyAccessFailure.LastOwner), rig.state.event)
        assertEquals(before + 1, rig.members.refreshesWithEmails)
        assertEquals(0, rig.insufficientRole)
        assertEquals(FamilyRole.OWNER, rig.rows().first { it.userId == "u-1" }.role)
    }

    @Test
    fun unMiembroQueYaNoEsta_seInformaYSeVuelveALeerLaLista() = runTest {
        val rig = rig()
        val before = rig.members.refreshesWithEmails
        rig.management.failure = FamilyAccessFailure.MemberNotFound
        rig.vm.requestRemove("u-3")
        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(FamilyManagementEvent.Failed(FamilyAccessFailure.MemberNotFound), rig.state.event)
        assertEquals(before + 1, rig.members.refreshesWithEmails)
    }

    @Test
    fun rolInsuficienteONoSerMiembro_seInformaYSeVuelveAConsultarLaCabecera() = runTest {
        listOf(FamilyAccessFailure.InsufficientRole, FamilyAccessFailure.NotAMember).forEach { failure ->
            val rig = failing(failure)

            assertEquals(FamilyManagementEvent.Failed(failure), rig.state.event)
            assertEquals(failure.toString(), 1, rig.insufficientRole)
            assertEquals(emptyList<Pair<String, String>>(), rig.members.removals)
        }
    }

    @Test
    fun redOSinConexion_seInforman_sinMasEfectos() = runTest {
        listOf(FamilyAccessFailure.Network, FamilyAccessFailure.Offline, FamilyAccessFailure.Unknown).forEach { failure ->
            val rig = failing(failure)

            assertEquals(FamilyManagementEvent.Failed(failure), rig.state.event)
            assertEquals(0, rig.insufficientRole)
            assertEquals(1, rig.members.refreshesWithEmails)
            assertEquals(4, rig.rows().size)
            assertFalse(rig.state.isBusy)
            assertNull(rig.state.pendingAction)
        }
    }

    @Test
    fun unFallo_alSalir_dejaLaFamiliaYElMotivo() = runTest {
        val rig = rig()
        rig.management.failure = FamilyAccessFailure.InsufficientRole
        rig.vm.requestLeave()
        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals("Familia Plata", rig.state.family?.name)
        assertEquals(FamilyManagementEvent.Failed(FamilyAccessFailure.InsufficientRole), rig.state.event)
    }

    @Test
    fun consumirElEvento_loQuita() = runTest {
        val rig = failing(FamilyAccessFailure.Network)

        rig.vm.consumeEvent()
        advanceUntilIdle()

        assertNull(rig.state.event)
    }

    // --- Una operación a la vez ---

    @Test
    fun dobleConfirmacion_unaSolaLlamada_yMientrasTantoNadaMasSePide() = runTest {
        val rig = rig()
        rig.management.gate = CompletableDeferred()
        rig.vm.requestRemove("u-3")

        rig.vm.confirmPendingAction()
        rig.vm.confirmPendingAction()
        advanceUntilIdle()
        assertTrue(rig.state.isBusy)
        rig.vm.requestRemove("u-2")
        rig.vm.requestChangeRole("u-2")
        rig.vm.requestLeave()
        rig.vm.dismissPendingAction()
        advanceUntilIdle()
        assertNull(rig.state.pendingAction)

        rig.management.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, rig.management.removals.size)
        assertEquals(emptyList<Triple<String, String, FamilyRole>>(), rig.management.roleChanges)
        assertFalse(rig.state.isBusy)
    }

    @Test
    fun conUnaAccionPendiente_otraNoLaSustituye() = runTest {
        val rig = rig()

        rig.vm.requestRemove("u-3")
        rig.vm.requestChangeRole("u-2")
        rig.vm.requestLeave()
        advanceUntilIdle()

        assertEquals(FamilyPendingAction.Remove("u-3", "Sofía Silva"), rig.state.pendingAction)
    }

    @Test
    fun confirmarSinNadaPendiente_noHaceNada() = runTest {
        val rig = rig()

        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, String>>(), rig.management.removals)
        assertFalse(rig.state.isBusy)
    }
}
