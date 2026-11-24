package com.rocioplata.familyfinaiapp.data.family

import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilySettings
import com.rocioplata.familyfinaiapp.domain.family.ReceivedInvitation
import com.rocioplata.familyfinaiapp.domain.family.SentInvitation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.time.Instant

class FamilyAccessRemoteDataSourceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun source(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): FamilyAccessRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
            install(SessionAuth) { accessToken = { "tok" } }
        }
        return FamilyAccessRemoteDataSource(client, "http://localhost:3000")
    }

    private fun reply(status: HttpStatusCode, body: String = ""): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(body, status, jsonHeaders) }

    private fun errorBody(code: String) = """{"error":"$code","message":"texto que no se lee"}"""

    private fun <T> FamilyAccessResult<T>.failure(): FamilyAccessFailure = (this as FamilyAccessResult.Failure).failure

    private fun <T> FamilyAccessResult<T>.value(): T = (this as FamilyAccessResult.Success).value

    private fun HttpRequestData.json(): JsonObject = Json.parseToJsonElement((body as TextContent).text) as JsonObject

    private val expires = "2026-11-29T12:00:00.000Z"

    private fun sentJson(id: String, email: String = "hermano@example.com", role: String = "MEMBER", status: String = "PENDING", at: String = expires) =
        """{"id":"$id","familyId":"f-1","invitedEmail":"$email","invitedBy":"u-1","role":"$role","status":"$status","expiresAt":"$at"}"""

    private fun receivedJson(id: String, name: String? = "Carolina", role: String = "MEMBER", at: String = expires): String {
        val inviter = """{"userId":"u-9","displayName":${if (name == null) "null" else "\"$name\""}}"""
        return """{"id":"$id","familyId":"f-9","familyName":"Familia Gómez","invitedEmail":"yo@example.com","invitedBy":$inviter,"role":"$role","status":"PENDING","expiresAt":"$at"}"""
    }

    /** Captura método, ruta, cuerpo y cabeceras de la única llamada que hace [call]. */
    private class Captured(
        var method: HttpMethod? = null,
        var path: String? = null,
        var body: JsonObject? = null,
        var auth: String? = null,
        var contentType: String? = null,
        var hadBody: Boolean = false,
    )

    private suspend fun capture(status: HttpStatusCode, body: String = "", call: suspend (FamilyAccessRemoteDataSource) -> Any?): Captured {
        val seen = Captured()
        val source = source {
            seen.method = it.method
            seen.path = it.url.encodedPath
            seen.auth = it.headers[HttpHeaders.Authorization]
            seen.contentType = it.body.contentType?.toString()
            seen.hadBody = it.body is TextContent
            if (it.body is TextContent) seen.body = it.json()
            respond(body, status, jsonHeaders)
        }
        call(source)
        return seen
    }

    // --- Crear familia ---

    @Test
    fun crear_envia_POST_families_con_el_nombre_y_el_Bearer() = runTest {
        val seen = capture(HttpStatusCode.Created, """{"id":"f-5","name":"Nueva","defaultCurrency":"CLP"}""") { it.createFamily("Nueva") }

        assertEquals(HttpMethod.Post, seen.method)
        assertEquals("/families", seen.path)
        assertEquals(JsonPrimitive("Nueva"), seen.body?.get("name"))
        assertEquals("Bearer tok", seen.auth)
    }

    @Test
    fun crear_devuelveLaFamiliaComoPropietariaYConUnMiembro() = runTest {
        val result = source(reply(HttpStatusCode.Created, """{"id":"f-5","name":"Nueva","defaultCurrency":"CLP"}""")).createFamily("Nueva")

        assertEquals(Family("f-5", "Nueva", FamilyRole.OWNER, "CLP", memberCount = 1), result.value())
    }

    @Test
    fun crear_conMonedaDesconocidaOAusente_quedaSinMoneda() = runTest {
        assertNull(source(reply(HttpStatusCode.Created, """{"id":"f","name":"N","defaultCurrency":"ZZZZ"}""")).createFamily("N").value().currency)
        assertNull(source(reply(HttpStatusCode.Created, """{"id":"f","name":"N"}""")).createFamily("N").value().currency)
    }

    @Test
    fun crear_cuerpoIlegible_esInvalidData() = runTest {
        assertEquals(FamilyAccessFailure.InvalidData, source(reply(HttpStatusCode.Created, """{"nombre":1}""")).createFamily("N").failure())
    }

    // --- Reordenar ---

    @Test
    fun reordenar_envia_PUT_con_los_ids_en_orden() = runTest {
        val seen = capture(HttpStatusCode.NoContent) { it.reorderFamilies(listOf("f-2", "f-1")) }

        assertEquals(HttpMethod.Put, seen.method)
        assertEquals("/me/families/order", seen.path)
        assertEquals(JsonArray(listOf(JsonPrimitive("f-2"), JsonPrimitive("f-1"))), seen.body?.get("orderedFamilyIds"))
    }

    @Test
    fun reordenar_exito_204() = runTest {
        assertEquals(Unit, source(reply(HttpStatusCode.NoContent)).reorderFamilies(listOf("f-1")).value())
    }

    // --- Ajustes ---

    @Test
    fun ajustes_pideLaConfiguracionDeLaFamilia() = runTest {
        val seen = capture(HttpStatusCode.OK, """{"defaultCurrency":"CLP","currencyLocked":false,"currencyLockReasons":[]}""") { it.settings("f-1") }

        assertEquals(HttpMethod.Get, seen.method)
        assertEquals("/families/f-1/settings", seen.path)
    }

    @Test
    fun ajustes_desbloqueada() = runTest {
        val result = source(reply(HttpStatusCode.OK, """{"defaultCurrency":"USD","currencyLocked":false,"currencyLockReasons":[]}""")).settings("f-1")

        assertEquals(FamilySettings("USD", isLocked = false, lockReasons = emptySet()), result.value())
    }

    @Test
    fun ajustes_bloqueadaPorMovimientosYPresupuestos() = runTest {
        val body = """{"defaultCurrency":"CLP","currencyLocked":true,"currencyLockReasons":["ITEMS_EXIST","ACTIVE_BUDGETS_EXIST"]}"""

        val result = source(reply(HttpStatusCode.OK, body)).settings("f-1")

        assertEquals(FamilySettings("CLP", true, setOf(CurrencyLockReason.MOVEMENTS, CurrencyLockReason.BUDGETS)), result.value())
    }

    @Test
    fun ajustes_unMotivoDesconocido_siguiendoBloqueada_sinMotivosConocidos() = runTest {
        val body = """{"defaultCurrency":"CLP","currencyLocked":true,"currencyLockReasons":["MOTIVO_NUEVO"]}"""

        val result = source(reply(HttpStatusCode.OK, body)).settings("f-1")

        assertEquals(FamilySettings("CLP", isLocked = true, lockReasons = emptySet()), result.value())
    }

    @Test
    fun ajustes_monedaDesconocidaOCuerpoIlegible_esInvalidData() = runTest {
        val unknownCurrency = """{"defaultCurrency":"ZZZZ","currencyLocked":false,"currencyLockReasons":[]}"""
        assertEquals(FamilyAccessFailure.InvalidData, source(reply(HttpStatusCode.OK, unknownCurrency)).settings("f-1").failure())
        assertEquals(FamilyAccessFailure.InvalidData, source(reply(HttpStatusCode.OK, "no es json")).settings("f-1").failure())
    }

    // --- Moneda ---

    @Test
    fun moneda_envia_PATCH_con_newCurrency() = runTest {
        val seen = capture(HttpStatusCode.NoContent) { it.changeCurrency("f-1", "USD") }

        assertEquals(HttpMethod.Patch, seen.method)
        assertEquals("/families/f-1/settings/currency", seen.path)
        assertEquals(JsonPrimitive("USD"), seen.body?.get("newCurrency"))
    }

    // --- Miembros ---

    @Test
    fun rol_envia_PATCH_con_newRole() = runTest {
        val seen = capture(HttpStatusCode.NoContent) { it.changeMemberRole("f-1", "u-2", FamilyRole.OWNER) }

        assertEquals(HttpMethod.Patch, seen.method)
        assertEquals("/families/f-1/members/u-2/role", seen.path)
        assertEquals(JsonPrimitive("OWNER"), seen.body?.get("newRole"))
    }

    @Test
    fun quitar_envia_DELETE_sinCuerpoNiContentType() = runTest {
        val seen = capture(HttpStatusCode.NoContent) { it.removeMember("f-1", "u-2") }

        assertEquals(HttpMethod.Delete, seen.method)
        assertEquals("/families/f-1/members/u-2", seen.path)
        assertEquals(false, seen.hadBody)
        assertNull(seen.contentType)
    }

    // --- Invitaciones enviadas ---

    @Test
    fun enviadas_pideLasInvitacionesDeLaFamilia() = runTest {
        val seen = capture(HttpStatusCode.OK, "[]") { it.sentInvitations("f-1") }

        assertEquals(HttpMethod.Get, seen.method)
        assertEquals("/families/f-1/invitations", seen.path)
    }

    @Test
    fun enviadas_conservanElOrdenYMapeanRolYVencimiento() = runTest {
        val body = "[" + listOf(sentJson("i-2", "b@example.com", "OWNER"), sentJson("i-1", "a@example.com")).joinToString(",") + "]"

        val result = source(reply(HttpStatusCode.OK, body)).sentInvitations("f-1")

        assertEquals(
            listOf(
                SentInvitation("i-2", "b@example.com", FamilyRole.OWNER, Instant.parse(expires)),
                SentInvitation("i-1", "a@example.com", FamilyRole.MEMBER, Instant.parse(expires)),
            ),
            result.value(),
        )
    }

    @Test
    fun enviadas_unaInvitacionVencida_seDevuelveIgual() = runTest {
        val body = "[" + sentJson("i-1", at = "2020-01-01T00:00:00.000Z") + "]"

        assertEquals(Instant.parse("2020-01-01T00:00:00Z"), source(reply(HttpStatusCode.OK, body)).sentInvitations("f-1").value().single().expiresAt)
    }

    @Test
    fun enviadas_ignoraLasQueNoEstanPendientes() = runTest {
        val body = "[" + listOf(sentJson("i-1"), sentJson("i-2", status = "ACCEPTED"), sentJson("i-3", status = "REVOKED")).joinToString(",") + "]"

        assertEquals(listOf("i-1"), source(reply(HttpStatusCode.OK, body)).sentInvitations("f-1").value().map { it.id })
    }

    @Test
    fun enviadas_rolDesconocidoOFechaIlegible_esInvalidData() = runTest {
        assertEquals(
            FamilyAccessFailure.InvalidData,
            source(reply(HttpStatusCode.OK, "[" + sentJson("i-1", role = "ADMIN") + "]")).sentInvitations("f-1").failure(),
        )
        assertEquals(
            FamilyAccessFailure.InvalidData,
            source(reply(HttpStatusCode.OK, "[" + sentJson("i-1", at = "mañana") + "]")).sentInvitations("f-1").failure(),
        )
    }

    // --- Invitar, revocar ---

    @Test
    fun invitar_envia_POST_con_correoYRol() = runTest {
        val seen = capture(HttpStatusCode.Created, sentJson("i-7")) { it.invite("f-1", "hermano@example.com", FamilyRole.MEMBER) }

        assertEquals(HttpMethod.Post, seen.method)
        assertEquals("/families/f-1/invitations", seen.path)
        assertEquals(JsonPrimitive("hermano@example.com"), seen.body?.get("email"))
        assertEquals(JsonPrimitive("MEMBER"), seen.body?.get("role"))
    }

    @Test
    fun invitar_devuelveLaInvitacionCreada() = runTest {
        val result = source(reply(HttpStatusCode.Created, sentJson("i-7", "hermano@example.com", "OWNER"))).invite("f-1", "hermano@example.com", FamilyRole.OWNER)

        assertEquals(SentInvitation("i-7", "hermano@example.com", FamilyRole.OWNER, Instant.parse(expires)), result.value())
    }

    @Test
    fun revocar_envia_DELETE_sinCuerpoNiContentType() = runTest {
        val seen = capture(HttpStatusCode.NoContent) { it.revokeInvitation("i-1") }

        assertEquals(HttpMethod.Delete, seen.method)
        assertEquals("/invitations/i-1", seen.path)
        assertEquals(false, seen.hadBody)
        assertNull(seen.contentType)
    }

    // --- Recibidas ---

    @Test
    fun recibidas_pideLasInvitacionesDelUsuario() = runTest {
        val seen = capture(HttpStatusCode.OK, "[]") { it.receivedInvitations() }

        assertEquals(HttpMethod.Get, seen.method)
        assertEquals("/me/invitations", seen.path)
    }

    @Test
    fun recibidas_mapeanFamiliaQuienInvitaRolYVencimiento() = runTest {
        val body = "[" + listOf(receivedJson("i-1"), receivedJson("i-2", role = "OWNER")).joinToString(",") + "]"

        val result = source(reply(HttpStatusCode.OK, body)).receivedInvitations()

        assertEquals(
            listOf(
                ReceivedInvitation("i-1", "f-9", "Familia Gómez", "Carolina", FamilyRole.MEMBER, Instant.parse(expires)),
                ReceivedInvitation("i-2", "f-9", "Familia Gómez", "Carolina", FamilyRole.OWNER, Instant.parse(expires)),
            ),
            result.value(),
        )
    }

    @Test
    fun recibidas_quienInvitaSinNombre_quedaNulo() = runTest {
        val body = "[" + receivedJson("i-1", name = null) + "]"

        assertNull(source(reply(HttpStatusCode.OK, body)).receivedInvitations().value().single().invitedByName)
    }

    @Test
    fun recibidas_sinDatosDeQuienInvita_quedaNulo() = runTest {
        val body = """[{"id":"i-1","familyId":"f-9","familyName":"F","role":"MEMBER","expiresAt":"$expires"}]"""

        assertNull(source(reply(HttpStatusCode.OK, body)).receivedInvitations().value().single().invitedByName)
    }

    @Test
    fun recibidas_vacias_esExito() = runTest {
        assertEquals(emptyList<ReceivedInvitation>(), source(reply(HttpStatusCode.OK, "[]")).receivedInvitations().value())
    }

    @Test
    fun recibidas_fechaIlegibleORolDesconocido_esInvalidData() = runTest {
        assertEquals(
            FamilyAccessFailure.InvalidData,
            source(reply(HttpStatusCode.OK, "[" + receivedJson("i-1", at = "pronto") + "]")).receivedInvitations().failure(),
        )
        assertEquals(
            FamilyAccessFailure.InvalidData,
            source(reply(HttpStatusCode.OK, "[" + receivedJson("i-1", role = "ADMIN") + "]")).receivedInvitations().failure(),
        )
    }

    // --- Aceptar y rechazar ---

    @Test
    fun aceptar_envia_POST_sinCuerpoNiContentType() = runTest {
        val seen = capture(HttpStatusCode.NoContent) { it.acceptInvitation("i-1") }

        assertEquals(HttpMethod.Post, seen.method)
        assertEquals("/invitations/i-1/accept", seen.path)
        assertEquals(false, seen.hadBody)
        assertNull(seen.contentType)
    }

    @Test
    fun rechazar_envia_POST_sinCuerpoNiContentType() = runTest {
        val seen = capture(HttpStatusCode.NoContent) { it.declineInvitation("i-1") }

        assertEquals(HttpMethod.Post, seen.method)
        assertEquals("/invitations/i-1/decline", seen.path)
        assertEquals(false, seen.hadBody)
        assertNull(seen.contentType)
    }

    // --- Tabla de errores ---

    private val errorTable = listOf(
        "FAMILY_ACCESS.INVALID_FAMILY_NAME" to FamilyAccessFailure.InvalidName,
        "FAMILY_ACCESS.INVALID_FAMILY_ORDER" to FamilyAccessFailure.InvalidOrder,
        "FAMILY_ACCESS.ALREADY_MEMBER" to FamilyAccessFailure.AlreadyMember,
        "FAMILY_ACCESS.INVITATION_ALREADY_PENDING" to FamilyAccessFailure.InvitationAlreadyPending,
        "SHARED.INVALID_EMAIL" to FamilyAccessFailure.InvalidEmail,
        "FAMILY_ACCESS.CANNOT_REMOVE_LAST_OWNER" to FamilyAccessFailure.LastOwner,
        "FAMILY_ACCESS.MEMBER_NOT_FOUND" to FamilyAccessFailure.MemberNotFound,
        "FAMILY_ACCESS.INVITATION_NOT_FOUND" to FamilyAccessFailure.InvitationNotFound,
        "FAMILY_ACCESS.INVITATION_EXPIRED" to FamilyAccessFailure.InvitationExpired,
        "FAMILY_ACCESS.INVITATION_NOT_PENDING" to FamilyAccessFailure.InvitationNotPending,
        "FAMILY.INVITATION_NOT_FOR_USER" to FamilyAccessFailure.InvitationNotForUser,
        "FAMILY.CURRENCY_LOCKED" to FamilyAccessFailure.CurrencyLocked,
        "FAMILY.INSUFFICIENT_ROLE" to FamilyAccessFailure.InsufficientRole,
        "FAMILY.NOT_A_MEMBER" to FamilyAccessFailure.NotAMember,
        "FAMILY_ACCESS.FAMILY_NOT_FOUND" to FamilyAccessFailure.NotAMember,
    )

    /** Una operación de cada forma de respuesta: con cuerpo, sin cuerpo y listados. */
    private val operations: List<Pair<String, suspend (FamilyAccessRemoteDataSource) -> FamilyAccessResult<*>>> = listOf(
        "crear" to { it.createFamily("N") },
        "reordenar" to { it.reorderFamilies(listOf("f-1")) },
        "ajustes" to { it.settings("f-1") },
        "moneda" to { it.changeCurrency("f-1", "USD") },
        "rol" to { it.changeMemberRole("f-1", "u-1", FamilyRole.MEMBER) },
        "quitar" to { it.removeMember("f-1", "u-1") },
        "enviadas" to { it.sentInvitations("f-1") },
        "invitar" to { it.invite("f-1", "a@b.cl", FamilyRole.MEMBER) },
        "revocar" to { it.revokeInvitation("i-1") },
        "recibidas" to { it.receivedInvitations() },
        "aceptar" to { it.acceptInvitation("i-1") },
        "rechazar" to { it.declineInvitation("i-1") },
    )

    @Test
    fun cadaCodigoDeError_seTraduceIgualEnTodasLasOperaciones() = runTest {
        errorTable.forEach { (code, expected) ->
            operations.forEach { (name, call) ->
                val status = if (code.endsWith("NOT_FOUND")) HttpStatusCode.NotFound else HttpStatusCode.BadRequest
                val result = call(source(reply(status, errorBody(code))))
                assertEquals("$name con $code", FamilyAccessResult.Failure(expected), result)
            }
        }
    }

    @Test
    fun errorSinCodigoOConUnoDesconocido_esUnknown_y401EsUnauthorized() = runTest {
        operations.forEach { (name, call) ->
            assertEquals(name, FamilyAccessResult.Failure(FamilyAccessFailure.Unknown), call(source(reply(HttpStatusCode.InternalServerError, errorBody("INTERNAL.UNEXPECTED_ERROR")))))
            assertEquals(name, FamilyAccessResult.Failure(FamilyAccessFailure.Unknown), call(source(reply(HttpStatusCode.BadRequest, "no es json"))))
            assertEquals(name, FamilyAccessResult.Failure(FamilyAccessFailure.Unauthorized), call(source(reply(HttpStatusCode.Unauthorized, errorBody("AUTH.INVALID_TOKEN")))))
        }
    }

    @Test
    fun unEstadoDistintoDelEsperado_conCuerpoValido_noEsExito() = runTest {
        // El contrato da 204 al aceptar: un 200 no se toma como éxito silencioso.
        assertEquals(FamilyAccessResult.Failure(FamilyAccessFailure.Unknown), source(reply(HttpStatusCode.OK)).acceptInvitation("i-1"))
    }

    @Test
    fun fallosDeRedYEsperaExcesiva_sonNetwork() = runTest {
        operations.forEach { (name, call) ->
            assertEquals(name, FamilyAccessResult.Failure(FamilyAccessFailure.Network), call(source { throw IOException("sin red") }))
            assertEquals(
                name,
                FamilyAccessResult.Failure(FamilyAccessFailure.Network),
                call(source { throw HttpRequestTimeoutException("http://x", 15_000) }),
            )
        }
    }

    @Test
    fun unaExcepcionInesperada_esUnknown() = runTest {
        assertEquals(FamilyAccessResult.Failure(FamilyAccessFailure.Unknown), source { throw IllegalStateException("x") }.acceptInvitation("i-1"))
    }

    @Test
    fun cancelacion_seRelanzaEnTodasLasOperaciones() {
        val source = source { throw CancellationException("cancelada") }
        operations.forEach { (name, call) ->
            assertThrows(name, CancellationException::class.java) { runBlocking { call(source) } }
        }
    }

    @Test
    fun losIdentificadoresSeCodificanEnLaRuta() = runTest {
        val seen = capture(HttpStatusCode.NoContent) { it.removeMember("f/1", "u 2") }

        assertEquals("/families/f%2F1/members/u%202", seen.path)
    }
}
