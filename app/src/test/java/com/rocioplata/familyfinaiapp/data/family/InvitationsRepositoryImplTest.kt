package com.rocioplata.familyfinaiapp.data.family

import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.ReceivedInvitation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class InvitationsRepositoryImplTest {
    private val plata = Family("f-1", "Plata", FamilyRole.OWNER, "CLP", memberCount = 3)
    private val expires = "2026-11-29T12:00:00.000Z"
    private val gomez = ReceivedInvitation("i-1", "f-9", "Familia Gómez", "Carolina", FamilyRole.MEMBER, Instant.parse(expires))

    private fun json(id: String, familyId: String = "f-9", familyName: String = "Familia Gómez") =
        """{"id":"$id","familyId":"$familyId","familyName":"$familyName","invitedEmail":"yo@example.com","invitedBy":{"userId":"u-9","displayName":"Carolina"},"role":"MEMBER","status":"PENDING","expiresAt":"$expires"}"""

    private class Env(
        val repository: InvitationsRepositoryImpl,
        val header: FakeHeaderRepository,
        val backend: FakeBackend,
        val connectivity: FakeConnectivity,
    )

    private fun env(families: List<Family>? = listOf(plata), pending: Int? = 2): Env {
        val backend = FakeBackend()
        val header = FakeHeaderRepository(families, pending)
        val connectivity = FakeConnectivity()
        val repository = InvitationsRepositoryImpl(
            FamilyAccessRemoteDataSource(backend.client(), "http://x"), header, connectivity,
        )
        return Env(repository, header, backend, connectivity)
    }

    private fun <T> FamilyAccessResult<T>.value() = (this as FamilyAccessResult.Success).value

    private fun <T> FamilyAccessResult<T>.failure() = (this as FamilyAccessResult.Failure).failure

    @Test
    fun recibidas_fijanElContadorConLaListaRecibida() = runTest {
        val env = env(pending = 7)
        env.backend.on(HttpMethod.Get, "/me/invitations", HttpStatusCode.OK, "[${json("i-1")},${json("i-2")}]")

        val result = env.repository.received().value()

        assertEquals(listOf("i-1", "i-2"), result.map { it.id })
        assertEquals(2, env.header.pending)
    }

    @Test
    fun recibidas_sinInvitaciones_dejaElContadorEnCero() = runTest {
        val env = env(pending = 3)
        env.backend.on(HttpMethod.Get, "/me/invitations", HttpStatusCode.OK, "[]")

        assertEquals(emptyList<ReceivedInvitation>(), env.repository.received().value())
        assertEquals(0, env.header.pending)
    }

    @Test
    fun recibidas_conFallo_noCambiaElContador() = runTest {
        val env = env(pending = 3)
        env.backend.offline = true

        assertEquals(FamilyAccessFailure.Network, env.repository.received().failure())
        assertEquals(3, env.header.pending)
    }

    @Test
    fun aceptar_anadeLaFamiliaConLoQueDiceLaInvitacion_bajaElContadorYRefrescaLaCabecera() = runTest {
        val env = env(pending = 2)
        env.backend.on(HttpMethod.Post, "/invitations/i-1/accept", HttpStatusCode.NoContent)

        env.repository.accept(gomez).value()

        assertEquals(listOf(plata, Family("f-9", "Familia Gómez", FamilyRole.MEMBER)), env.header.families)
        assertNull(env.header.families!!.last().currency)
        assertEquals(1, env.header.pending)
        assertEquals(1, env.header.refreshes)
    }

    @Test
    fun aceptar_conElContadorSinConocer_noLoInventa() = runTest {
        val env = env(pending = null)
        env.backend.on(HttpMethod.Post, "/invitations/i-1/accept", HttpStatusCode.NoContent)

        env.repository.accept(gomez).value()

        assertNull(env.header.pending)
    }

    @Test
    fun aceptar_elContadorNuncaBajaDeCero() = runTest {
        val env = env(pending = 0)
        env.backend.on(HttpMethod.Post, "/invitations/i-1/accept", HttpStatusCode.NoContent)

        env.repository.accept(gomez).value()

        assertEquals(0, env.header.pending)
    }

    @Test
    fun aceptar_laFamiliaYaEstaEnLaListaCuandoSeRefresca_porSiElRefrescoFalla() = runTest {
        val env = env()
        env.backend.on(HttpMethod.Post, "/invitations/i-1/accept", HttpStatusCode.NoContent)
        var seenAtRefresh: List<String>? = null
        env.header.onRefresh = { seenAtRefresh = env.header.families?.map { it.id } }

        env.repository.accept(gomez).value()

        assertEquals(listOf("f-1", "f-9"), seenAtRefresh)
    }

    @Test
    fun aceptar_conFallo_noCambiaNada_yNoRefresca() = runTest {
        val env = env(pending = 2)
        env.backend.on(HttpMethod.Post, "/invitations/i-1/accept", HttpStatusCode.BadRequest, """{"error":"FAMILY_ACCESS.INVITATION_EXPIRED"}""")

        assertEquals(FamilyAccessFailure.InvitationExpired, env.repository.accept(gomez).failure())
        assertEquals(listOf(plata), env.header.families)
        assertEquals(2, env.header.pending)
        assertEquals(0, env.header.refreshes)
    }

    @Test
    fun rechazar_bajaElContador_sinTocarLasFamilias() = runTest {
        val env = env(pending = 2)
        env.backend.on(HttpMethod.Post, "/invitations/i-1/decline", HttpStatusCode.NoContent)

        env.repository.decline("i-1").value()

        assertEquals(1, env.header.pending)
        assertEquals(listOf(plata), env.header.families)
        assertEquals(0, env.header.refreshes)
    }

    @Test
    fun rechazar_conFallo_noBajaElContador() = runTest {
        val env = env(pending = 2)
        env.backend.on(HttpMethod.Post, "/invitations/i-1/decline", HttpStatusCode.Forbidden, """{"error":"FAMILY.INVITATION_NOT_FOR_USER"}""")

        assertEquals(FamilyAccessFailure.InvitationNotForUser, env.repository.decline("i-1").failure())
        assertEquals(2, env.header.pending)
    }

    @Test
    fun sinConexion_nadaLlamaAlBackend_yNadaCambia() = runTest {
        val env = env(pending = 2)
        env.connectivity.online = false
        val offline = FamilyAccessResult.Failure(FamilyAccessFailure.Offline)

        assertEquals(offline, env.repository.received())
        assertEquals(offline, env.repository.accept(gomez))
        assertEquals(offline, env.repository.decline("i-1"))

        assertEquals(0, env.backend.calls.get())
        assertEquals(2, env.header.pending)
        assertEquals(listOf(plata), env.header.families)
    }
}
