package com.rocioplata.familyfinaiapp.data.family

import com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilySettings
import com.rocioplata.familyfinaiapp.domain.family.ReceivedInvitation
import com.rocioplata.familyfinaiapp.domain.family.SentInvitation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.money.Money
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Llamadas de Family & Access que usa la gestión de familias (contrato §3.3; los detalles que el contrato no da, en
 * `BACKEND_GAPS.md`, BG-06). [client] es el cliente autenticado: añade el Bearer y renueva la sesión por su cuenta.
 * Nunca lanza por red o servidor; relanza la cancelación. De los cuerpos de error solo se lee `error`.
 */
class FamilyAccessRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /** Crea la familia: el usuario es su Propietario y es la única persona; la moneda es la que devuelve el backend. */
    suspend fun createFamily(name: String): FamilyAccessResult<Family> {
        val body = buildJsonObject { put("name", name) }
        return send(HttpStatusCode.Created, request = { client.post { path("families"); jsonBody(body) } }) { dto: CreatedFamilyDto ->
            // Una moneda desconocida se trata como ausente: la app no puede registrar movimientos sin ella.
            val currency = dto.defaultCurrency?.takeIf { Money.isKnown(it) }
            FamilyAccessResult.Success(Family(dto.id, dto.name, FamilyRole.OWNER, currency, memberCount = 1))
        }
    }

    suspend fun reorderFamilies(orderedFamilyIds: List<String>): FamilyAccessResult<Unit> {
        val body = buildJsonObject { put("orderedFamilyIds", buildJsonArray { orderedFamilyIds.forEach { add(JsonPrimitive(it)) } }) }
        return sendDiscardingBody(HttpStatusCode.NoContent) { client.put { path("me", "families", "order"); jsonBody(body) } }
    }

    suspend fun settings(familyId: String): FamilyAccessResult<FamilySettings> =
        send(HttpStatusCode.OK, request = { client.get { path("families", familyId, "settings"); applyTimeouts() } }) { dto: SettingsDto ->
            if (!Money.isKnown(dto.defaultCurrency)) return@send FamilyAccessResult.Failure(FamilyAccessFailure.InvalidData)
            // Un motivo que la app no conoce se ignora, pero un bloqueo sigue siendo un bloqueo.
            val reasons = dto.currencyLockReasons.mapNotNull {
                when (it) {
                    "ITEMS_EXIST" -> CurrencyLockReason.MOVEMENTS
                    "ACTIVE_BUDGETS_EXIST" -> CurrencyLockReason.BUDGETS
                    else -> null
                }
            }.toSet()
            FamilyAccessResult.Success(FamilySettings(dto.defaultCurrency, dto.currencyLocked, reasons))
        }

    suspend fun changeCurrency(familyId: String, currency: String): FamilyAccessResult<Unit> {
        val body = buildJsonObject { put("newCurrency", currency) }
        return sendDiscardingBody(HttpStatusCode.NoContent) {
            client.patch { path("families", familyId, "settings", "currency"); jsonBody(body) }
        }
    }

    suspend fun changeMemberRole(familyId: String, memberId: String, newRole: FamilyRole): FamilyAccessResult<Unit> {
        val body = buildJsonObject { put("newRole", newRole.name) }
        return sendDiscardingBody(HttpStatusCode.NoContent) {
            client.patch { path("families", familyId, "members", memberId, "role"); jsonBody(body) }
        }
    }

    /** Quita a [memberId]; si es el propio usuario, sale de la familia (BG-01). */
    suspend fun removeMember(familyId: String, memberId: String): FamilyAccessResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) {
            client.delete { path("families", familyId, "members", memberId); applyTimeouts() }
        }

    /** Invitaciones pendientes de la familia, vencidas incluidas (BG-05), en el orden que devuelve el backend. */
    suspend fun sentInvitations(familyId: String): FamilyAccessResult<List<SentInvitation>> =
        send(HttpStatusCode.OK, request = { client.get { path("families", familyId, "invitations"); applyTimeouts() } }) { dtos: List<SentInvitationDto> ->
            val invitations = dtos.filter { it.status == PENDING }.map {
                it.toDomain() ?: return@send FamilyAccessResult.Failure(FamilyAccessFailure.InvalidData)
            }
            FamilyAccessResult.Success(invitations)
        }

    suspend fun invite(familyId: String, email: String, role: FamilyRole): FamilyAccessResult<SentInvitation> {
        val body = buildJsonObject {
            put("email", email)
            put("role", role.name)
        }
        return send(HttpStatusCode.Created, request = { client.post { path("families", familyId, "invitations"); jsonBody(body) } }) { dto: SentInvitationDto ->
            dto.toDomain()?.let { FamilyAccessResult.Success(it) } ?: FamilyAccessResult.Failure(FamilyAccessFailure.InvalidData)
        }
    }

    /** Revoca una invitación vigente o quita una vencida: el backend no distingue (BG-05). */
    suspend fun revokeInvitation(invitationId: String): FamilyAccessResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) { client.delete { path("invitations", invitationId); applyTimeouts() } }

    suspend fun receivedInvitations(): FamilyAccessResult<List<ReceivedInvitation>> =
        send(HttpStatusCode.OK, request = { client.get { path("me", "invitations"); applyTimeouts() } }) { dtos: List<ReceivedInvitationDto> ->
            val invitations = dtos.map {
                it.toDomain() ?: return@send FamilyAccessResult.Failure(FamilyAccessFailure.InvalidData)
            }
            FamilyAccessResult.Success(invitations)
        }

    // Sin cuerpo ni `Content-Type`: el backend responde `500` a un POST con la cabecera y el cuerpo vacío (BG-08 de la 006).
    suspend fun acceptInvitation(invitationId: String): FamilyAccessResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) { client.post { path("invitations", invitationId, "accept"); applyTimeouts() } }

    suspend fun declineInvitation(invitationId: String): FamilyAccessResult<Unit> =
        sendDiscardingBody(HttpStatusCode.NoContent) { client.post { path("invitations", invitationId, "decline"); applyTimeouts() } }

    private fun HttpRequestBuilder.jsonBody(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body)
        applyTimeouts()
    }

    private fun HttpRequestBuilder.path(vararg segments: String) {
        url {
            takeFrom(baseUrl.trimEnd('/'))
            appendPathSegments(*segments, encodeSlash = true)
        }
    }

    private fun HttpRequestBuilder.applyTimeouts() {
        timeout {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    private suspend inline fun <reified D, T> send(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
        map: (D) -> FamilyAccessResult<T>,
    ): FamilyAccessResult<T> {
        val response = when (val call = call(expected, request)) {
            is FamilyAccessResult.Failure -> return call
            is FamilyAccessResult.Success -> call.value
        }
        return try {
            map(response.body<D>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FamilyAccessResult.Failure(FamilyAccessFailure.InvalidData)
        }
    }

    private suspend inline fun sendDiscardingBody(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
    ): FamilyAccessResult<Unit> = when (val call = call(expected, request)) {
        is FamilyAccessResult.Failure -> call
        is FamilyAccessResult.Success -> FamilyAccessResult.Success(Unit)
    }

    private suspend inline fun call(
        expected: HttpStatusCode,
        request: () -> HttpResponse,
    ): FamilyAccessResult<HttpResponse> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return FamilyAccessResult.Failure(FamilyAccessFailure.Network)
        } catch (e: Exception) {
            return FamilyAccessResult.Failure(FamilyAccessFailure.Unknown)
        }
        return if (response.status == expected) {
            FamilyAccessResult.Success(response)
        } else {
            FamilyAccessResult.Failure(failureOf(response))
        }
    }

    // Solo se interpreta `error`; `message` no se lee ni se registra.
    private suspend fun failureOf(response: HttpResponse): FamilyAccessFailure {
        val code = try {
            response.body<ErrorDto>().error
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return when {
            code == "FAMILY_ACCESS.INVALID_FAMILY_NAME" -> FamilyAccessFailure.InvalidName
            code == "FAMILY_ACCESS.INVALID_FAMILY_ORDER" -> FamilyAccessFailure.InvalidOrder
            code == "FAMILY_ACCESS.ALREADY_MEMBER" -> FamilyAccessFailure.AlreadyMember
            code == "SHARED.INVALID_EMAIL" -> FamilyAccessFailure.InvalidEmail
            code == "FAMILY_ACCESS.CANNOT_REMOVE_LAST_OWNER" -> FamilyAccessFailure.LastOwner
            code == "FAMILY_ACCESS.MEMBER_NOT_FOUND" -> FamilyAccessFailure.MemberNotFound
            code == "FAMILY_ACCESS.INVITATION_NOT_FOUND" -> FamilyAccessFailure.InvitationNotFound
            code == "FAMILY_ACCESS.INVITATION_EXPIRED" -> FamilyAccessFailure.InvitationExpired
            code == "FAMILY_ACCESS.INVITATION_NOT_PENDING" -> FamilyAccessFailure.InvitationNotPending
            code == "FAMILY.INVITATION_NOT_FOR_USER" -> FamilyAccessFailure.InvitationNotForUser
            code == "FAMILY.CURRENCY_LOCKED" -> FamilyAccessFailure.CurrencyLocked
            code == "FAMILY.INSUFFICIENT_ROLE" -> FamilyAccessFailure.InsufficientRole
            code == "FAMILY.NOT_A_MEMBER" || code == "FAMILY_ACCESS.FAMILY_NOT_FOUND" -> FamilyAccessFailure.NotAMember
            response.status == HttpStatusCode.Unauthorized -> FamilyAccessFailure.Unauthorized
            else -> FamilyAccessFailure.Unknown
        }
    }

    private fun SentInvitationDto.toDomain(): SentInvitation? {
        val parsedRole = FamilyRole.entries.firstOrNull { it.name == role } ?: return null
        val expires = instantOrNull(expiresAt) ?: return null
        return SentInvitation(id, invitedEmail, parsedRole, expires)
    }

    private fun ReceivedInvitationDto.toDomain(): ReceivedInvitation? {
        val parsedRole = FamilyRole.entries.firstOrNull { it.name == role } ?: return null
        val expires = instantOrNull(expiresAt) ?: return null
        return ReceivedInvitation(id, familyId, familyName, invitedBy?.displayName, parsedRole, expires)
    }

    private fun instantOrNull(text: String): Instant? = try {
        Instant.parse(text)
    } catch (e: DateTimeParseException) {
        null
    }

    @Serializable
    private data class CreatedFamilyDto(val id: String, val name: String, val defaultCurrency: String? = null)

    @Serializable
    private data class SettingsDto(
        val defaultCurrency: String,
        val currencyLocked: Boolean,
        val currencyLockReasons: List<String> = emptyList(),
    )

    @Serializable
    private data class SentInvitationDto(
        val id: String,
        val invitedEmail: String,
        val role: String,
        val expiresAt: String,
        val status: String = PENDING,
    )

    @Serializable
    private data class InviterDto(val displayName: String? = null)

    @Serializable
    private data class ReceivedInvitationDto(
        val id: String,
        val familyId: String,
        val familyName: String,
        val role: String,
        val expiresAt: String,
        val invitedBy: InviterDto? = null,
    )

    @Serializable
    private data class ErrorDto(val error: String? = null)

    private companion object {
        const val PENDING = "PENDING"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
