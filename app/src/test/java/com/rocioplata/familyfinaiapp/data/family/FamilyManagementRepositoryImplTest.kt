package com.rocioplata.familyfinaiapp.data.family

import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilySettings
import com.rocioplata.familyfinaiapp.domain.family.SentInvitation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.session.SessionFailure
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FamilyManagementRepositoryImplTest {
    private val plata = Family("f-1", "Plata", FamilyRole.OWNER, "CLP", memberCount = 3)
    private val depto = Family("f-2", "Depto", FamilyRole.MEMBER, "CLP", memberCount = 2)

    private class Env(
        val repository: FamilyManagementRepositoryImpl,
        val header: FakeHeaderRepository,
        val members: RecordingMembersRepository,
        val backend: FakeBackend,
        val session: FixedSession,
        val connectivity: FakeConnectivity,
    )

    private fun env(families: List<Family>? = listOf(plata, depto)): Env {
        val backend = FakeBackend()
        val header = FakeHeaderRepository(families)
        val members = RecordingMembersRepository()
        val session = FixedSession()
        val connectivity = FakeConnectivity()
        val repository = FamilyManagementRepositoryImpl(
            FamilyAccessRemoteDataSource(backend.client(), "http://x"), header, members, session, connectivity,
        )
        return Env(repository, header, members, backend, session, connectivity)
    }

    private fun error(code: String) = """{"error":"$code"}"""

    private fun <T> FamilyAccessResult<T>.value() = (this as FamilyAccessResult.Success).value

    private fun <T> FamilyAccessResult<T>.failure() = (this as FamilyAccessResult.Failure).failure

    // --- Crear ---

    @Test
    fun crear_laAnadeAlFinalDeLaCabeceraComoPropietariaYLaDevuelve() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Post, "/families", HttpStatusCode.Created, """{"id":"f-3","name":"Nueva","defaultCurrency":"CLP"}""")

        val result = env.repository.createFamily("Nueva")

        val created = Family("f-3", "Nueva", FamilyRole.OWNER, "CLP", memberCount = 1)
        assertEquals(created, result.value())
        assertEquals(listOf(plata, depto, created), env.header.families)
    }

    @Test
    fun crear_conFallo_noCambiaLaCabecera() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Post, "/families", HttpStatusCode.BadRequest, error("FAMILY_ACCESS.INVALID_FAMILY_NAME"))

        assertEquals(FamilyAccessFailure.InvalidName, env.repository.createFamily("x").failure())
        assertEquals(listOf(plata, depto), env.header.families)
    }

    // --- Reordenar ---

    @Test
    fun reordenar_fijaElOrdenEnLaCabecera() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Put, "/me/families/order", HttpStatusCode.NoContent)

        env.repository.reorderFamilies(listOf("f-2", "f-1")).value()

        assertEquals(listOf(depto, plata), env.header.families)
    }

    @Test
    fun reordenar_conFallo_dejaElOrdenAnterior() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Put, "/me/families/order", HttpStatusCode.BadRequest, error("FAMILY_ACCESS.INVALID_FAMILY_ORDER"))

        assertEquals(FamilyAccessFailure.InvalidOrder, env.repository.reorderFamilies(listOf("f-2", "f-1")).failure())
        assertEquals(listOf(plata, depto), env.header.families)
    }

    // --- Ajustes y moneda ---

    @Test
    fun ajustes_losDevuelveSinTocarLaCabecera() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Get, "/families/f-1/settings", HttpStatusCode.OK, """{"defaultCurrency":"CLP","currencyLocked":false,"currencyLockReasons":[]}""")

        assertEquals(FamilySettings("CLP", false, emptySet()), env.repository.settings("f-1").value())
        assertEquals(listOf(plata, depto), env.header.families)
    }

    @Test
    fun moneda_cambiaLaDeEsaFamiliaEnLaCabecera() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Patch, "/families/f-1/settings/currency", HttpStatusCode.NoContent)

        env.repository.changeCurrency("f-1", "USD").value()

        assertEquals(listOf(plata.copy(currency = "USD"), depto), env.header.families)
    }

    @Test
    fun moneda_bloqueada_noCambiaNada() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Patch, "/families/f-1/settings/currency", HttpStatusCode.Conflict, error("FAMILY.CURRENCY_LOCKED"))

        assertEquals(FamilyAccessFailure.CurrencyLocked, env.repository.changeCurrency("f-1", "USD").failure())
        assertEquals(listOf(plata, depto), env.header.families)
    }

    // --- Roles y miembros ---

    @Test
    fun rolDeOtroMiembro_seAplicaALaListaDeMiembros_yNoCambiaElRolPropio() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Patch, "/families/f-1/members/u-2/role", HttpStatusCode.NoContent)

        env.repository.changeMemberRole("f-1", "u-2", FamilyRole.OWNER).value()

        assertEquals(listOf(Triple("f-1", "u-2", FamilyRole.OWNER)), env.members.roles)
        assertEquals(listOf(plata, depto), env.header.families)
    }

    @Test
    fun rolPropio_tambienCambiaElDeLaCabecera() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Patch, "/families/f-1/members/u-1/role", HttpStatusCode.NoContent)

        env.repository.changeMemberRole("f-1", "u-1", FamilyRole.MEMBER).value()

        assertEquals(listOf(plata.copy(role = FamilyRole.MEMBER), depto), env.header.families)
        assertEquals(listOf(Triple("f-1", "u-1", FamilyRole.MEMBER)), env.members.roles)
    }

    @Test
    fun rol_conFallo_noCambiaNada() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Patch, "/families/f-1/members/u-1/role", HttpStatusCode.BadRequest, error("FAMILY_ACCESS.CANNOT_REMOVE_LAST_OWNER"))

        assertEquals(FamilyAccessFailure.LastOwner, env.repository.changeMemberRole("f-1", "u-1", FamilyRole.MEMBER).failure())
        assertEquals(listOf(plata, depto), env.header.families)
        assertEquals(emptyList<Triple<String, String, FamilyRole>>(), env.members.roles)
    }

    @Test
    fun rol_sinSesion_noLlamaAlBackend() = runTest {
        val env = env()
        env.session.failure = SessionFailure.NOT_SIGNED_IN

        assertEquals(FamilyAccessFailure.Unauthorized, env.repository.changeMemberRole("f-1", "u-2", FamilyRole.OWNER).failure())
        assertEquals(0, env.backend.calls.get())
    }

    @Test
    fun quitar_loSacaDeLaListaYBajaElNumeroDeMiembros() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Delete, "/families/f-1/members/u-2", HttpStatusCode.NoContent)

        env.repository.removeMember("f-1", "u-2").value()

        assertEquals(listOf("f-1" to "u-2"), env.members.removals)
        assertEquals(listOf(plata.copy(memberCount = 2), depto), env.header.families)
    }

    @Test
    fun quitar_sinNumeroDeMiembrosConocido_loDejaSinEl_yNuncaBajaDeUno() = runTest {
        val env = env(listOf(plata.copy(memberCount = null), depto.copy(memberCount = 1)))
        env.backend.on(HttpMethod.Delete, "/families/f-1/members/u-2", HttpStatusCode.NoContent)
        env.backend.on(HttpMethod.Delete, "/families/f-2/members/u-3", HttpStatusCode.NoContent)

        env.repository.removeMember("f-1", "u-2").value()
        env.repository.removeMember("f-2", "u-3").value()

        assertNull(env.header.families!![0].memberCount)
        assertEquals(1, env.header.families!![1].memberCount)
    }

    @Test
    fun quitar_conFallo_noCambiaNada() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Delete, "/families/f-1/members/u-2", HttpStatusCode.NotFound, error("FAMILY_ACCESS.MEMBER_NOT_FOUND"))

        assertEquals(FamilyAccessFailure.MemberNotFound, env.repository.removeMember("f-1", "u-2").failure())
        assertEquals(listOf(plata, depto), env.header.families)
        assertEquals(emptyList<Pair<String, String>>(), env.members.removals)
    }

    // --- Salir ---

    @Test
    fun salir_quitaAlPropioUsuario_yLaFamiliaDesapareceDeLaCabecera() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Delete, "/families/f-2/members/u-1", HttpStatusCode.NoContent)

        env.repository.leaveFamily("f-2").value()

        assertEquals(listOf(HttpMethod.Delete to "/families/f-2/members/u-1"), env.backend.requests)
        assertEquals(listOf(plata), env.header.families)
    }

    @Test
    fun salir_siSeriaElUnicoPropietario_nadaCambia() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Delete, "/families/f-1/members/u-1", HttpStatusCode.BadRequest, error("FAMILY_ACCESS.CANNOT_REMOVE_LAST_OWNER"))

        assertEquals(FamilyAccessFailure.LastOwner, env.repository.leaveFamily("f-1").failure())
        assertEquals(listOf(plata, depto), env.header.families)
    }

    @Test
    fun salir_siElBackendNoLoPermiteAUnIntegrante_seInformaDelRol() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Delete, "/families/f-2/members/u-1", HttpStatusCode.Forbidden, error("FAMILY.INSUFFICIENT_ROLE"))

        assertEquals(FamilyAccessFailure.InsufficientRole, env.repository.leaveFamily("f-2").failure())
        assertEquals(listOf(plata, depto), env.header.families)
    }

    @Test
    fun salir_sinSesion_noLlamaAlBackend() = runTest {
        val env = env()
        env.session.failure = SessionFailure.UNKNOWN

        assertEquals(FamilyAccessFailure.Unknown, env.repository.leaveFamily("f-2").failure())
        assertEquals(0, env.backend.calls.get())
    }

    // --- Invitaciones enviadas ---

    private val expires = "2026-11-29T12:00:00.000Z"
    private val invitationJson = """{"id":"i-1","familyId":"f-1","invitedEmail":"a@b.cl","invitedBy":"u-1","role":"MEMBER","status":"PENDING","expiresAt":"$expires"}"""
    private val invitation = SentInvitation("i-1", "a@b.cl", FamilyRole.MEMBER, Instant.parse(expires))

    @Test
    fun invitaciones_listarInvitarYRevocar_noTocanLaCabeceraNiLosMiembros() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Get, "/families/f-1/invitations", HttpStatusCode.OK, "[$invitationJson]")
        env.backend.on(HttpMethod.Post, "/families/f-1/invitations", HttpStatusCode.Created, invitationJson)
        env.backend.on(HttpMethod.Delete, "/invitations/i-1", HttpStatusCode.NoContent)

        assertEquals(listOf(invitation), env.repository.sentInvitations("f-1").value())
        assertEquals(invitation, env.repository.invite("f-1", "a@b.cl", FamilyRole.MEMBER).value())
        env.repository.revokeInvitation("i-1").value()

        assertEquals(listOf(plata, depto), env.header.families)
        assertEquals(emptyList<Pair<String, String>>(), env.members.removals)
    }

    @Test
    fun invitar_aUnMiembro_informaDeQueYaLoEs() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Post, "/families/f-1/invitations", HttpStatusCode.Conflict, error("FAMILY_ACCESS.ALREADY_MEMBER"))

        assertEquals(FamilyAccessFailure.AlreadyMember, env.repository.invite("f-1", "a@b.cl", FamilyRole.MEMBER).failure())
    }

    // --- Sin conexión ---

    @Test
    fun sinConexion_ningunaOperacionLlamaAlBackend_yNadaCambia() = runTest {
        val env = env()
        env.connectivity.online = false
        val offline = FamilyAccessResult.Failure(FamilyAccessFailure.Offline)

        assertEquals(offline, env.repository.createFamily("N"))
        assertEquals(offline, env.repository.reorderFamilies(listOf("f-2", "f-1")))
        assertEquals(offline, env.repository.settings("f-1"))
        assertEquals(offline, env.repository.changeCurrency("f-1", "USD"))
        assertEquals(offline, env.repository.changeMemberRole("f-1", "u-1", FamilyRole.MEMBER))
        assertEquals(offline, env.repository.removeMember("f-1", "u-2"))
        assertEquals(offline, env.repository.leaveFamily("f-2"))
        assertEquals(offline, env.repository.sentInvitations("f-1"))
        assertEquals(offline, env.repository.invite("f-1", "a@b.cl", FamilyRole.MEMBER))
        assertEquals(offline, env.repository.revokeInvitation("i-1"))

        assertEquals(0, env.backend.calls.get())
        assertEquals(listOf(plata, depto), env.header.families)
    }

    @Test
    fun fallosDeRed_sonNetwork_yNoCambianNada() = runTest {
        val env = env()
        env.backend.offline = true

        assertEquals(FamilyAccessFailure.Network, env.repository.createFamily("N").failure())
        assertEquals(FamilyAccessFailure.Network, env.repository.leaveFamily("f-2").failure())
        assertEquals(listOf(plata, depto), env.header.families)
    }
}
