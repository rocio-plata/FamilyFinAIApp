package com.rocioplata.familyfinaiapp.data.members

import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.io.IOException

/** Los miembros y, por `userId`, los correos que el backend envió (vacío si quien pide no es Propietario). */
class MembersWithEmails(val members: List<FamilyMember>, val emails: Map<String, String>) {
    // Sin correos en el texto: podría acabar en un log.
    override fun toString() = "MembersWithEmails(members=${members.size}, emails=${emails.size})"
}

/**
 * `GET /families/:familyId/members` (contrato §3.3). [client] es el cliente autenticado: añade el Bearer y
 * renueva la sesión por su cuenta. `members` solo lee `userId`, `displayName` y `role`: el correo, que el backend
 * entrega solo a los Propietarios, no se lee ni se guarda (RF-15 de la 007); `membersWithEmails` lo devuelve aparte
 * para «Mi familia» (RF-07 de la 009), sin que llegue a ninguna caché. Nunca lanza por red o servidor; relanza
 * la cancelación.
 */
class FamilyMembersRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /** Miembros actuales de la familia, en el orden que devuelve el backend. */
    suspend fun members(familyId: String): FamilyMembersResult<List<FamilyMember>> =
        when (val result = load(familyId)) {
            is FamilyMembersResult.Failure -> result
            is FamilyMembersResult.Success -> FamilyMembersResult.Success(result.value.members)
        }

    /** Como [members], con el correo de cada miembro cuyo correo envió el backend (solo a un Propietario). */
    suspend fun membersWithEmails(familyId: String): FamilyMembersResult<MembersWithEmails> = load(familyId)

    private suspend fun load(familyId: String): FamilyMembersResult<MembersWithEmails> {
        val response = try {
            client.get {
                url {
                    takeFrom(baseUrl.trimEnd('/'))
                    appendPathSegments("families", familyId, "members", encodeSlash = true)
                }
                timeout {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return FamilyMembersResult.Failure(FamilyMembersFailure.Network)
        } catch (e: Exception) {
            return FamilyMembersResult.Failure(FamilyMembersFailure.Unknown)
        }
        if (response.status != HttpStatusCode.OK) return FamilyMembersResult.Failure(failureOf(response))
        return try {
            val dtos = response.body<List<MemberDto>>()
            val members = dtos.map {
                it.toDomain() ?: return FamilyMembersResult.Failure(FamilyMembersFailure.InvalidData)
            }
            val emails = dtos.mapNotNull { dto -> dto.email?.trim()?.takeIf { it.isNotEmpty() }?.let { dto.userId to it } }.toMap()
            FamilyMembersResult.Success(MembersWithEmails(members, emails))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FamilyMembersResult.Failure(FamilyMembersFailure.InvalidData)
        }
    }

    // Solo importa el estado: `403` es "no eres miembro" (`FAMILY.NOT_A_MEMBER`) o rol insuficiente.
    private fun failureOf(response: HttpResponse): FamilyMembersFailure = when (response.status) {
        HttpStatusCode.Forbidden -> FamilyMembersFailure.NotAllowed
        HttpStatusCode.Unauthorized -> FamilyMembersFailure.Unauthorized
        else -> FamilyMembersFailure.Unknown
    }

    private fun MemberDto.toDomain(): FamilyMember? {
        val parsed = FamilyRole.entries.firstOrNull { it.name == role } ?: return null
        return FamilyMember(userId, displayName, parsed)
    }

    // `joinedAt` llega en el JSON pero no se declara: `ignoreUnknownKeys` lo descarta. El correo solo se lee para
    // devolverlo a quien lo pide; `FamilyMember` no tiene dónde guardarlo.
    @Serializable
    private data class MemberDto(
        val userId: String,
        val role: String,
        val displayName: String? = null,
        val email: String? = null,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
