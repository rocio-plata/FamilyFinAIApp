package com.rocioplata.familyfinaiapp.data.header

import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.IOException

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val failure: HeaderFailure) : RemoteResult<Nothing>
}

/**
 * Los tres GET de la cabecera. [client] es el cliente autenticado: añade el Bearer y renueva la
 * sesión por su cuenta. Nunca lanza por red o servidor; relanza la cancelación.
 */
class HeaderRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun families(): RemoteResult<List<Family>> =
        get("/me/families") { dtos: List<FamilyDto> ->
            val families = dtos.map { dto ->
                val role = FamilyRole.entries.firstOrNull { it.name == dto.role }
                    ?: return@get RemoteResult.Failure(HeaderFailure.InvalidData)
                Family(dto.familyId, dto.name, role)
            }
            RemoteResult.Success(families)
        }

    suspend fun pendingInvitations(): RemoteResult<Int> =
        get("/me/invitations") { items: List<JsonElement> -> RemoteResult.Success(items.size) }

    suspend fun displayName(): RemoteResult<String> =
        get("/me/profile") { profile: ProfileDto -> RemoteResult.Success(profile.displayName) }

    private suspend inline fun <reified D, T> get(
        path: String,
        map: (D) -> RemoteResult<T>,
    ): RemoteResult<T> {
        val response = try {
            client.get("${baseUrl.trimEnd('/')}$path") {
                timeout {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return RemoteResult.Failure(HeaderFailure.Network)
        } catch (e: Exception) {
            return RemoteResult.Failure(HeaderFailure.Unknown)
        }
        if (response.status != HttpStatusCode.OK) return RemoteResult.Failure(failureOf(response))
        return try {
            map(response.body<D>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RemoteResult.Failure(HeaderFailure.InvalidData)
        }
    }

    // Solo se interpreta `error`; `message` no se lee ni se registra.
    private suspend fun failureOf(response: HttpResponse): HeaderFailure {
        val code = try {
            response.body<ErrorDto>().error
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return when {
            code == INSUFFICIENT_ROLE -> HeaderFailure.InsufficientRole
            response.status == HttpStatusCode.Unauthorized -> HeaderFailure.Unauthorized
            else -> HeaderFailure.Unknown
        }
    }

    @Serializable
    private data class FamilyDto(val familyId: String, val name: String, val role: String)

    @Serializable
    private data class ProfileDto(val displayName: String)

    @Serializable
    private data class ErrorDto(val error: String? = null)

    private companion object {
        const val INSUFFICIENT_ROLE = "FAMILY.INSUFFICIENT_ROLE"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
