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

/**
 * `GET /families/:familyId/members` (contrato §3.3). [client] es el cliente autenticado: añade el Bearer y
 * renueva la sesión por su cuenta. Solo se leen `userId`, `displayName` y `role`: el correo, que el backend
 * entrega solo a los Propietarios, no se lee ni se guarda (RF-15). Nunca lanza por red o servidor; relanza
 * la cancelación.
 */
class FamilyMembersRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /** Miembros actuales de la familia, en el orden que devuelve el backend. */
    suspend fun members(familyId: String): FamilyMembersResult<List<FamilyMember>> {
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
            val members = response.body<List<MemberDto>>().map {
                it.toDomain() ?: return FamilyMembersResult.Failure(FamilyMembersFailure.InvalidData)
            }
            FamilyMembersResult.Success(members)
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

    // El correo (`email`) y `joinedAt` llegan en el JSON pero no se declaran: `ignoreUnknownKeys` los descarta.
    @Serializable
    private data class MemberDto(
        val userId: String,
        val role: String,
        val displayName: String? = null,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
