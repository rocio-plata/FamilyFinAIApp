package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.data.family.FakeHeaderRepository
import com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilySettings
import com.rocioplata.familyfinaiapp.domain.family.SentInvitation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import java.time.Duration
import java.time.Instant
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
    private val clock = Instant.parse("2026-11-22T12:00:00Z")

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
        backend: (RecordingManagement) -> Unit = {},
        configure: (ControllableMembers) -> Unit = {},
    ): Rig {
        val header = FakeHeaderRepository(families)
        val members = ControllableMembers(initial, stale).also(configure)
        val management = RecordingManagement(header, members, currentUserId).also(backend)
        val connectivity = ControllableConnectivity(online)
        lateinit var rig: Rig
        val vm = FamilyManagementViewModel(
            familyId, currentUserId, header, members, management, connectivity,
            onInsufficientRole = { rig.insufficientRole++ }, now = { clock },
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

    // --- Moneda (T-24) ---

    private fun Rig.currency() = state.currency

    private fun unlocked(code: String = "CLP") = FamilyAccessResult.Success(FamilySettings(code, false, emptySet()))

    private fun locked(vararg reasons: CurrencyLockReason, code: String = "CLP") =
        FamilyAccessResult.Success(FamilySettings(code, true, reasons.toSet()))

    @Test
    fun unPropietario_pideLosAjustes_yVeLaMonedaConLasTresOpciones() = runTest {
        val rig = rig()

        assertEquals(1, rig.management.settingsCalls)
        assertEquals(CurrencySection.Content("CLP", listOf("CLP", "USD", "EUR"), isLocked = false, lockReasons = emptySet()), rig.currency())
    }

    @Test
    fun unIntegrante_noPideAjustes_ni_ve_laSeccionDeMoneda() = runTest {
        val rig = rig(familyId = "f-2")

        assertEquals(0, rig.management.settingsCalls)
        assertEquals(CurrencySection.Hidden, rig.currency())
        rig.vm.changeCurrency("USD")
        advanceUntilIdle()
        assertEquals(emptyList<Pair<String, String>>(), rig.management.currencyChanges)
    }

    @Test
    fun unaMonedaBloqueada_seMuestraConSuMotivo() = runTest {
        val movements = rigWith(locked(CurrencyLockReason.MOVEMENTS))
        val budgets = rigWith(locked(CurrencyLockReason.BUDGETS))
        val both = rigWith(locked(CurrencyLockReason.MOVEMENTS, CurrencyLockReason.BUDGETS))

        assertEquals(CurrencySection.Content("CLP", listOf("CLP", "USD", "EUR"), true, setOf(CurrencyLockReason.MOVEMENTS)), movements.currency())
        assertEquals(setOf(CurrencyLockReason.BUDGETS), (budgets.currency() as CurrencySection.Content).lockReasons)
        assertEquals(
            setOf(CurrencyLockReason.MOVEMENTS, CurrencyLockReason.BUDGETS),
            (both.currency() as CurrencySection.Content).lockReasons,
        )
    }

    @Test
    fun unMotivoDesconocido_siguiendoBloqueada_noSeTrataComoDesbloqueada() = runTest {
        val rig = rigWith(locked())

        assertEquals(CurrencySection.Content("CLP", listOf("CLP", "USD", "EUR"), isLocked = true, lockReasons = emptySet()), rig.currency())
        rig.vm.changeCurrency("USD")
        advanceUntilIdle()
        assertEquals(emptyList<Pair<String, String>>(), rig.management.currencyChanges)
    }

    private fun TestScope.rigWith(result: FamilyAccessResult<FamilySettings>): Rig {
        val header = FakeHeaderRepository(listOf(plata, depto))
        val members = ControllableMembers(four)
        val management = RecordingManagement(header, members, me).also { it.settingsResult = result }
        val connectivity = ControllableConnectivity(true)
        lateinit var rig: Rig
        val vm = FamilyManagementViewModel(
            "f-1", me, header, members, management, connectivity, onInsufficientRole = { rig.insufficientRole++ }, now = { clock },
        )
        rig = Rig(vm, header, members, management, connectivity)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return rig
    }

    @Test
    fun mientrasSeComprueba_estaCargando() = runTest {
        val header = FakeHeaderRepository(listOf(plata, depto))
        val members = ControllableMembers(four)
        val management = RecordingManagement(header, members, me).also { it.settingsGate = CompletableDeferred() }
        val vm = FamilyManagementViewModel("f-1", me, header, members, management, ControllableConnectivity(true))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(CurrencySection.Loading, vm.uiState.value.currency)
        assertEquals(4, (vm.uiState.value.members as MembersSection.Content).rows.size)
    }

    @Test
    fun siNoSePuedeComprobar_hayErrorSoloEnLaMoneda_yReintentarFunciona() = runTest {
        val rig = rigWith(FamilyAccessResult.Failure(FamilyAccessFailure.Network))
        assertEquals(CurrencySection.Error, rig.currency())
        assertEquals(4, rig.rows().size)

        rig.management.settingsResult = unlocked()
        rig.vm.retryCurrency()
        advanceUntilIdle()

        assertTrue(rig.currency() is CurrencySection.Content)
    }

    @Test
    fun siUnaNuevaLecturaFalla_seConservaLoYaCargado() = runTest {
        val rig = rig()
        rig.management.settingsResult = FamilyAccessResult.Failure(FamilyAccessFailure.Network)

        rig.vm.onStart()
        advanceUntilIdle()

        assertTrue(rig.currency() is CurrencySection.Content)
    }

    @Test
    fun siLosAjustesDicenQueYaNoEresMiembro_seAvisa() = runTest {
        val rig = rigWith(FamilyAccessResult.Failure(FamilyAccessFailure.NotAMember))

        assertEquals(1, rig.insufficientRole)
        assertEquals(CurrencySection.Error, rig.currency())
    }

    @Test
    fun cambiarLaMoneda_laAplicaALaCabecera_yAlosAjustes() = runTest {
        val rig = rig()

        rig.vm.changeCurrency("USD")
        advanceUntilIdle()

        assertEquals(listOf("f-1" to "USD"), rig.management.currencyChanges)
        assertEquals("USD", rig.state.family?.currency)
        assertEquals("USD", (rig.currency() as CurrencySection.Content).current)
        assertNull(rig.state.event)
        assertFalse(rig.state.isBusy)
    }

    @Test
    fun cambiarALaMismaMonedaOAUnaNoSoportada_noHaceNada() = runTest {
        val rig = rig()

        rig.vm.changeCurrency("CLP")
        rig.vm.changeCurrency("GBP")
        rig.vm.changeCurrency("")
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, String>>(), rig.management.currencyChanges)
    }

    @Test
    fun conLaSeccionSinCargar_noSePuedeCambiar() = runTest {
        val rig = rigWith(FamilyAccessResult.Failure(FamilyAccessFailure.Network))

        rig.vm.changeCurrency("USD")
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, String>>(), rig.management.currencyChanges)
    }

    @Test
    fun siSeBloqueaEntretanto_seInformaYSeMuestraElMotivo_conservandoLaMoneda() = runTest {
        val rig = rig()
        rig.management.failure = FamilyAccessFailure.CurrencyLocked
        rig.management.settingsResult = locked(CurrencyLockReason.MOVEMENTS)

        rig.vm.changeCurrency("USD")
        advanceUntilIdle()

        assertEquals(FamilyManagementEvent.Failed(FamilyAccessFailure.CurrencyLocked), rig.state.event)
        assertEquals(2, rig.management.settingsCalls)
        val section = rig.currency() as CurrencySection.Content
        assertTrue(section.isLocked)
        assertEquals(setOf(CurrencyLockReason.MOVEMENTS), section.lockReasons)
        assertEquals("CLP", section.current)
        assertEquals("CLP", rig.state.family?.currency)
    }

    @Test
    fun rolInsuficienteAlCambiarLaMoneda_avisaParaConsultarLaCabecera() = runTest {
        val rig = rig()
        rig.management.failure = FamilyAccessFailure.InsufficientRole

        rig.vm.changeCurrency("EUR")
        advanceUntilIdle()

        assertEquals(1, rig.insufficientRole)
        assertEquals(FamilyManagementEvent.Failed(FamilyAccessFailure.InsufficientRole), rig.state.event)
        assertEquals("CLP", rig.state.family?.currency)
    }

    @Test
    fun sinConexionOConFalloDeRed_alCambiarLaMoneda_seInformaSinMasEfectos() = runTest {
        listOf(FamilyAccessFailure.Offline, FamilyAccessFailure.Network).forEach { failure ->
            val rig = rig()
            rig.management.failure = failure

            rig.vm.changeCurrency("USD")
            advanceUntilIdle()

            assertEquals(FamilyManagementEvent.Failed(failure), rig.state.event)
            assertEquals(1, rig.management.settingsCalls)
            assertEquals(0, rig.insufficientRole)
            assertEquals("CLP", (rig.currency() as CurrencySection.Content).current)
        }
    }

    @Test
    fun dobleSeleccionDeMoneda_unaSolaLlamada_yMientrasTantoNadaMasSePide() = runTest {
        val rig = rig()
        rig.management.gate = CompletableDeferred()

        rig.vm.changeCurrency("USD")
        rig.vm.changeCurrency("EUR")
        advanceUntilIdle()
        rig.vm.requestRemove("u-3")
        assertTrue(rig.state.isBusy)
        assertNull(rig.state.pendingAction)
        rig.management.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("f-1" to "USD"), rig.management.currencyChanges)
    }

    @Test
    fun siUnIntegrantePasaAPropietarioConLaPantallaAbierta_seCargaLaMonedaYLosCorreos() = runTest {
        val rig = rig(familyId = "f-2") { it.emails = mapOf("u-1" to "v@example.com") }
        assertEquals(0, rig.management.settingsCalls)
        assertEquals(0, rig.members.refreshesWithEmails)

        rig.header.updateFamily("f-2") { it.copy(role = FamilyRole.OWNER) }
        advanceUntilIdle()

        assertEquals(1, rig.management.settingsCalls)
        assertEquals(1, rig.members.refreshesWithEmails)
        assertTrue(rig.currency() is CurrencySection.Content)
    }

    @Test
    fun unPropietarioQueYaLoEra_noRepiteLaCargaPorElSoloHechoDeActualizarseLaCabecera() = runTest {
        val rig = rig()

        rig.header.updateFamily("f-1") { it.copy(memberCount = 10) }
        advanceUntilIdle()

        assertEquals(1, rig.management.settingsCalls)
    }

    // --- Invitaciones enviadas (T-25) ---

    private fun inv(id: String, email: String, expiresInDays: Long, role: FamilyRole = FamilyRole.MEMBER) =
        SentInvitation(id, email, role, clock.plus(Duration.ofDays(expiresInDays)))

    private fun Rig.invitations() = state.invitations as InvitationsSection.Content

    private fun TestScope.owner(vararg invitations: SentInvitation, configure: (ControllableMembers) -> Unit = {}) =
        rig(configure = configure, backend = { it.sentList += invitations })

    @Test
    fun unIntegrante_noPideInvitaciones_ni_puedeInvitar() = runTest {
        val rig = rig(familyId = "f-2")

        assertEquals(0, rig.management.sentCalls)
        assertEquals(InvitationsSection.Hidden, rig.state.invitations)
        assertFalse(rig.state.canInvite)
        rig.vm.openInviteForm()
        advanceUntilIdle()
        assertNull(rig.state.inviteForm)
    }

    @Test
    fun lasVigentesVanPorVencimiento_yLasVencidasAlFinal() = runTest {
        val rig = owner(
            inv("late", "late@example.com", 6),
            inv("old", "old@example.com", -3),
            inv("soon", "soon@example.com", 1),
            inv("older", "older@example.com", -10),
        )

        assertEquals(listOf("soon", "late", "older", "old"), rig.invitations().rows.map { it.id })
        assertEquals(listOf(false, false, true, true), rig.invitations().rows.map { it.isExpired })
        assertEquals(2, rig.invitations().activeCount)
    }

    @Test
    fun unaVencida_seOcultaSiEseCorreoTieneOtraVigente_sinDistinguirMayusculas() = runTest {
        val rig = owner(inv("old", "Hermano@Example.com", -2), inv("new", "hermano@example.com", 5))

        assertEquals(listOf("new"), rig.invitations().rows.map { it.id })
    }

    @Test
    fun unaVencida_seOcultaSiEseCorreoYaEsDeUnMiembro_siSeConocenLosCorreos() = runTest {
        val rig = owner(inv("old", "S@Example.com", -2), inv("other", "otro@example.com", -2)) {
            it.emails = mapOf("u-3" to "s@example.com")
        }

        assertEquals(listOf("other"), rig.invitations().rows.map { it.id })
    }

    @Test
    fun sinCorreosDeLosMiembros_unaVencidaNoSeOcultaPorSerDeUnMiembro() = runTest {
        val rig = rig(online = false, configure = { it.emails = mapOf("u-3" to "s@example.com") }, backend = {
            it.sentList += inv("old", "s@example.com", -2)
        })

        assertEquals(listOf("old"), rig.invitations().rows.map { it.id })
    }

    @Test
    fun unaVencidaDeCorreoUnico_sigueVisible() = runTest {
        val rig = owner(inv("old", "solo@example.com", -2))

        assertEquals(listOf("old"), rig.invitations().rows.map { it.id })
        assertEquals(0, rig.invitations().activeCount)
    }

    @Test
    fun sinInvitaciones_laSeccionEstaVacia_yPermiteInvitar() = runTest {
        val rig = owner()

        assertEquals(InvitationsSection.Content(emptyList(), 0), rig.state.invitations)
        assertTrue(rig.state.canInvite)
    }

    @Test
    fun mientrasCargan_noSePuedeInvitar() = runTest {
        val rig = rig(backend = { it.sentGate = CompletableDeferred() })

        assertEquals(InvitationsSection.Loading, rig.state.invitations)
        assertFalse(rig.state.canInvite)
        rig.vm.openInviteForm()
        advanceUntilIdle()
        assertNull(rig.state.inviteForm)
    }

    @Test
    fun siNoSePuedenCargar_hayErrorConSuMotivo_yReintentarFunciona() = runTest {
        val rig = rig(backend = { it.sentFailure = FamilyAccessFailure.Offline })
        assertEquals(InvitationsSection.Error(FamilyAccessFailure.Offline), rig.state.invitations)
        assertFalse(rig.state.canInvite)
        assertEquals(4, rig.rows().size)

        rig.management.sentFailure = null
        rig.vm.retryInvitations()
        advanceUntilIdle()

        assertTrue(rig.state.invitations is InvitationsSection.Content)
    }

    @Test
    fun siUnaNuevaLecturaFalla_seConservaLaListaCargada() = runTest {
        val rig = owner(inv("a", "a@example.com", 3))
        rig.management.sentFailure = FamilyAccessFailure.Network

        rig.vm.onStart()
        advanceUntilIdle()

        assertEquals(listOf("a"), rig.invitations().rows.map { it.id })
    }

    // --- Formulario ---

    @Test
    fun invitar_abreElFormularioVacio_conIntegranteElegido() = runTest {
        val rig = owner()

        rig.vm.openInviteForm()
        advanceUntilIdle()

        assertEquals(InviteFormState(email = "", role = FamilyRole.MEMBER), rig.state.inviteForm)
    }

    @Test
    fun invitar_noSeAbreConUnaAccionPendienteOEnCurso() = runTest {
        val rig = owner()
        rig.vm.requestRemove("u-3")
        rig.vm.openInviteForm()
        advanceUntilIdle()
        assertNull(rig.state.inviteForm)
    }

    @Test
    fun elCorreoNoPasaDe255_yEditarQuitaElError() = runTest {
        val rig = owner()
        rig.vm.openInviteForm()
        rig.vm.submitInvite()
        advanceUntilIdle()
        assertEquals(InviteFormError.Required, rig.state.inviteForm?.error)

        rig.vm.onInviteEmailChange("a".repeat(300))
        advanceUntilIdle()

        assertEquals(255, rig.state.inviteForm?.email?.length)
        assertNull(rig.state.inviteForm?.error)
    }

    @Test
    fun elRolSePuedeElegir() = runTest {
        val rig = owner()
        rig.vm.openInviteForm()

        rig.vm.onInviteRoleChange(FamilyRole.OWNER)
        advanceUntilIdle()

        assertEquals(FamilyRole.OWNER, rig.state.inviteForm?.role)
    }

    private fun TestScope.submit(rig: Rig, email: String) {
        rig.vm.openInviteForm()
        rig.vm.onInviteEmailChange(email)
        rig.vm.submitInvite()
        advanceUntilIdle()
    }

    @Test
    fun correoInvalido_seAvisaJuntoAlCampo_sinLlamarAlBackend_yConservaLoEscrito() = runTest {
        listOf(
            "" to InviteFormError.Required,
            "   " to InviteFormError.Required,
            "sin-arroba" to InviteFormError.InvalidFormat,
            "a b@c.cl" to InviteFormError.InvalidFormat,
        ).forEach { (text, error) ->
            val rig = owner()
            submit(rig, text)

            assertEquals(text, error, rig.state.inviteForm?.error)
            assertEquals(text, rig.state.inviteForm?.email)
            assertEquals(emptyList<Triple<String, String, FamilyRole>>(), rig.management.invites)
        }
    }

    @Test
    fun conInvitacionVigente_seAvisaDeQueHayQueRevocarla_tambienConOtrasMayusculas() = runTest {
        val rig = owner(inv("a", "Hermano@Example.com", 3))

        submit(rig, "  hermano@example.COM ")

        assertEquals(InviteFormError.AlreadyInvited, rig.state.inviteForm?.error)
        assertEquals(emptyList<Triple<String, String, FamilyRole>>(), rig.management.invites)
    }

    @Test
    fun conInvitacionVencida_sePuedeInvitarDeNuevo() = runTest {
        val rig = owner(inv("old", "hermano@example.com", -3))

        submit(rig, "hermano@example.com")

        assertEquals(1, rig.management.invites.size)
        assertNull(rig.state.inviteForm)
    }

    @Test
    fun correoDeUnMiembro_seDetectaSinLlamarAlBackend_cuandoSeConocenLosCorreos() = runTest {
        val rig = owner { it.emails = mapOf("u-3" to "s@example.com") }

        submit(rig, "S@example.com")

        assertEquals(InviteFormError.AlreadyMember, rig.state.inviteForm?.error)
        assertEquals(emptyList<Triple<String, String, FamilyRole>>(), rig.management.invites)
    }

    @Test
    fun siElBackendDiceQueYaEsMiembro_seAvisaJuntoAlCampo() = runTest {
        val rig = owner()
        rig.management.failure = FamilyAccessFailure.AlreadyMember

        submit(rig, "nuevo@example.com")

        assertEquals(InviteFormError.AlreadyMember, rig.state.inviteForm?.error)
        assertEquals("nuevo@example.com", rig.state.inviteForm?.email)
    }

    @Test
    fun invitar_conExito_cierraElFormulario_laAnadeALaLista_yAvisaDeComoLaVera() = runTest {
        val rig = owner(inv("a", "a@example.com", 3))
        rig.vm.openInviteForm()
        rig.vm.onInviteEmailChange("  nuevo@example.com ")
        rig.vm.onInviteRoleChange(FamilyRole.OWNER)

        rig.vm.submitInvite()
        advanceUntilIdle()

        assertEquals(listOf(Triple("f-1", "nuevo@example.com", FamilyRole.OWNER)), rig.management.invites)
        assertNull(rig.state.inviteForm)
        assertEquals(FamilyManagementEvent.InvitationSent("nuevo@example.com"), rig.state.event)
        assertEquals(listOf("a@example.com", "nuevo@example.com"), rig.invitations().rows.map { it.email })
        assertEquals(2, rig.invitations().activeCount)
        assertFalse(rig.state.isBusy)
    }

    @Test
    fun sinConexionOConOtroFallo_alInvitar_seInforma_yLoEscritoSeConserva() = runTest {
        listOf(FamilyAccessFailure.Offline, FamilyAccessFailure.InvalidEmail, FamilyAccessFailure.Unknown).forEach { failure ->
            val rig = owner()
            rig.management.failure = failure

            submit(rig, "nuevo@example.com")

            assertEquals(InviteFormError.Failure(failure), rig.state.inviteForm?.error)
            assertEquals("nuevo@example.com", rig.state.inviteForm?.email)
            assertFalse(rig.state.inviteForm?.isSaving == true)
            assertNull(rig.state.event)
        }
    }

    @Test
    fun rolInsuficienteAlInvitar_avisaParaConsultarLaCabecera() = runTest {
        val rig = owner()
        rig.management.failure = FamilyAccessFailure.InsufficientRole

        submit(rig, "nuevo@example.com")

        assertEquals(1, rig.insufficientRole)
        assertEquals(InviteFormError.Failure(FamilyAccessFailure.InsufficientRole), rig.state.inviteForm?.error)
    }

    // --- Fallo de red incierto al invitar ---

    @Test
    fun trasUnFalloDeRed_siLaInvitacionYaSeCreo_elReintentoNoLaDuplica() = runTest {
        val rig = owner()
        rig.management.failure = FamilyAccessFailure.Network
        rig.management.inviteAppliesBeforeFailing = true
        submit(rig, "nuevo@example.com")
        assertEquals(InviteFormError.Failure(FamilyAccessFailure.Network), rig.state.inviteForm?.error)
        val callsBefore = rig.management.sentCalls

        rig.vm.submitInvite()
        advanceUntilIdle()

        assertEquals(1, rig.management.invites.size)
        assertEquals(callsBefore + 1, rig.management.sentCalls)
        assertNull(rig.state.inviteForm)
        assertEquals(FamilyManagementEvent.InvitationSent("nuevo@example.com"), rig.state.event)
        assertEquals(listOf("nuevo@example.com"), rig.invitations().rows.map { it.email })
    }

    @Test
    fun trasUnFalloDeRed_siLaInvitacionNoSeCreo_elReintentoLaEnviaDespuesDeComprobar() = runTest {
        val rig = owner()
        rig.management.failure = FamilyAccessFailure.Network
        submit(rig, "nuevo@example.com")
        rig.management.failure = null

        rig.vm.submitInvite()
        advanceUntilIdle()

        assertEquals(2, rig.management.invites.size)
        assertNull(rig.state.inviteForm)
    }

    @Test
    fun trasUnFalloDeRed_siNoSePuedeComprobar_noSeEnvia_yLoEscritoSeConserva() = runTest {
        val rig = owner()
        rig.management.failure = FamilyAccessFailure.Network
        submit(rig, "nuevo@example.com")
        rig.management.sentFailure = FamilyAccessFailure.Network

        rig.vm.submitInvite()
        advanceUntilIdle()

        assertEquals(1, rig.management.invites.size)
        assertEquals(InviteFormError.Failure(FamilyAccessFailure.Network), rig.state.inviteForm?.error)
        assertEquals("nuevo@example.com", rig.state.inviteForm?.email)
    }

    @Test
    fun trasUnFalloQueNoEsDeRed_noHaceFaltaComprobarNada() = runTest {
        val rig = owner()
        rig.management.failure = FamilyAccessFailure.Offline
        submit(rig, "nuevo@example.com")
        rig.management.failure = null
        val callsBefore = rig.management.sentCalls

        rig.vm.submitInvite()
        advanceUntilIdle()

        assertEquals(callsBefore, rig.management.sentCalls)
        assertEquals(2, rig.management.invites.size)
    }

    // --- Una operación a la vez y descarte ---

    @Test
    fun dobleEnvio_unaSolaLlamada_yMientrasTantoSeEstaGuardando() = runTest {
        val rig = owner()
        rig.management.gate = CompletableDeferred()
        rig.vm.openInviteForm()
        rig.vm.onInviteEmailChange("nuevo@example.com")

        rig.vm.submitInvite()
        rig.vm.submitInvite()
        advanceUntilIdle()
        assertTrue(rig.state.inviteForm?.isSaving == true)
        assertTrue(rig.state.isBusy)
        rig.vm.onInviteEmailChange("otro@example.com")
        rig.vm.requestCloseInvite()
        rig.management.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, rig.management.invites.size)
        assertEquals("nuevo@example.com", rig.management.invites.single().second)
    }

    @Test
    fun cerrarElFormularioSinCambios_cierraDirectamente() = runTest {
        val rig = owner()
        rig.vm.openInviteForm()

        rig.vm.requestCloseInvite()
        advanceUntilIdle()

        assertNull(rig.state.inviteForm)
    }

    @Test
    fun cerrarConCambios_pideConfirmacion_ySeguirConservaLoEscrito() = runTest {
        val rig = owner()
        rig.vm.openInviteForm()
        rig.vm.onInviteEmailChange("nuevo@example.com")

        rig.vm.requestCloseInvite()
        advanceUntilIdle()
        assertEquals(true, rig.state.inviteForm?.confirmingDiscard)

        rig.vm.onInviteEmailChange("otro")
        rig.vm.submitInvite()
        rig.vm.dismissDiscardInvite()
        advanceUntilIdle()
        assertEquals(false, rig.state.inviteForm?.confirmingDiscard)
        assertEquals("nuevo@example.com", rig.state.inviteForm?.email)

        rig.vm.requestCloseInvite()
        rig.vm.confirmDiscardInvite()
        advanceUntilIdle()
        assertNull(rig.state.inviteForm)
        assertEquals(emptyList<Triple<String, String, FamilyRole>>(), rig.management.invites)
    }

    @Test
    fun confirmarElDescarteSinHaberPedidoSalir_noCierra() = runTest {
        val rig = owner()
        rig.vm.openInviteForm()
        rig.vm.onInviteEmailChange("a@b.cl")

        rig.vm.confirmDiscardInvite()
        advanceUntilIdle()

        assertTrue(rig.state.inviteForm != null)
    }

    // --- Volver a invitar y quitar ---

    @Test
    fun volverAInvitar_abreElFormularioConElCorreoYElRolDeLaVencida_ySalirSinCambiosCierraDirecto() = runTest {
        val rig = owner(inv("old", "hermano@example.com", -3, FamilyRole.OWNER))

        rig.vm.reinvite("old")
        advanceUntilIdle()
        assertEquals("hermano@example.com", rig.state.inviteForm?.email)
        assertEquals(FamilyRole.OWNER, rig.state.inviteForm?.role)

        rig.vm.requestCloseInvite()
        advanceUntilIdle()
        assertNull(rig.state.inviteForm)
    }

    @Test
    fun alVolverAInvitar_soloSeVeLaNueva() = runTest {
        val rig = owner(inv("old", "hermano@example.com", -3))

        rig.vm.reinvite("old")
        rig.vm.submitInvite()
        advanceUntilIdle()

        assertEquals(listOf("hermano@example.com"), rig.invitations().rows.map { it.email })
        assertEquals(listOf(false), rig.invitations().rows.map { it.isExpired })
    }

    @Test
    fun volverAInvitarDeUnaIdDesconocida_noHaceNada() = runTest {
        val rig = owner(inv("a", "a@example.com", 3))

        rig.vm.reinvite("no-existe")
        advanceUntilIdle()

        assertNull(rig.state.inviteForm)
    }

    @Test
    fun revocarUnaVigente_pideConfirmacion_yAlConfirmarSaleDeLaLista() = runTest {
        val rig = owner(inv("a", "a@example.com", 3), inv("b", "b@example.com", 4))

        rig.vm.requestRevoke("a")
        advanceUntilIdle()
        assertEquals(FamilyPendingAction.RevokeInvitation("a", "a@example.com"), rig.state.pendingAction)
        rig.vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(listOf("a"), rig.management.revokes)
        assertEquals(listOf("b"), rig.invitations().rows.map { it.id })
    }

    @Test
    fun revocar_cancelar_noCambiaNada_yUnaVencidaNoSeRevocaConConfirmacion() = runTest {
        val rig = owner(inv("a", "a@example.com", 3), inv("old", "o@example.com", -3))
        rig.vm.requestRevoke("a")
        rig.vm.dismissPendingAction()
        rig.vm.requestRevoke("old")
        advanceUntilIdle()

        assertNull(rig.state.pendingAction)
        assertEquals(emptyList<String>(), rig.management.revokes)
    }

    @Test
    fun revocarUnaQueYaNoEstaPendiente_seInformaYSeVuelveALeerLaLista() = runTest {
        listOf(FamilyAccessFailure.InvitationNotPending, FamilyAccessFailure.InvitationNotFound).forEach { failure ->
            val rig = owner(inv("a", "a@example.com", 3))
            rig.management.failure = failure
            val before = rig.management.sentCalls

            rig.vm.requestRevoke("a")
            rig.vm.confirmPendingAction()
            advanceUntilIdle()

            assertEquals(FamilyManagementEvent.Failed(failure), rig.state.event)
            assertEquals(before + 1, rig.management.sentCalls)
        }
    }

    @Test
    fun quitarUnaVencida_laRevocaSinConfirmacion_ySaleDeLaLista() = runTest {
        val rig = owner(inv("old", "o@example.com", -3), inv("a", "a@example.com", 3))

        rig.vm.removeExpired("old")
        advanceUntilIdle()

        assertEquals(listOf("old"), rig.management.revokes)
        assertEquals(listOf("a"), rig.invitations().rows.map { it.id })
        assertNull(rig.state.pendingAction)
        assertFalse(rig.state.isBusy)
    }

    @Test
    fun quitar_unaVigenteODesconocida_seIgnora_yUnFalloSoloSeInforma() = runTest {
        val rig = owner(inv("a", "a@example.com", 3), inv("old", "o@example.com", -3))
        rig.vm.removeExpired("a")
        rig.vm.removeExpired("no-existe")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), rig.management.revokes)

        rig.management.failure = FamilyAccessFailure.Network
        rig.vm.removeExpired("old")
        advanceUntilIdle()
        assertEquals(FamilyManagementEvent.Failed(FamilyAccessFailure.Network), rig.state.event)
        assertEquals(2, rig.invitations().rows.size)
    }

    @Test
    fun siUnIntegrantePasaAPropietario_seCarganLasInvitaciones() = runTest {
        val rig = rig(familyId = "f-2") { it.emails = emptyMap() }
        assertEquals(0, rig.management.sentCalls)

        rig.header.updateFamily("f-2") { it.copy(role = FamilyRole.OWNER) }
        advanceUntilIdle()

        assertEquals(1, rig.management.sentCalls)
        assertTrue(rig.state.invitations is InvitationsSection.Content)
    }
}
