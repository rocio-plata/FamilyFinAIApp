package com.rocioplata.familyfinaiapp.data.auth

import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.AuthFailure
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.io.IOException

sealed interface AuthCallResult {
    data class Success(val session: StoredSession) : AuthCallResult
    data class Failure(val failure: AuthFailure) : AuthCallResult
}

sealed interface RefreshCallResult {
    data class Success(val accessToken: String, val refreshToken: String) : RefreshCallResult {
        override fun toString() = "Success"
    }

    /** El backend ya no acepta la sesión: hay que cerrarla con [reason] (`Expired` o `Security`). */
    data class Rejected(val reason: SessionEndReason) : RefreshCallResult

    /** Fallo de red o de servidor: la sesión se conserva (RF-10). */
    data object Network : RefreshCallResult
    data object Unknown : RefreshCallResult
}

sealed interface LogoutCallResult {
    data object Success : LogoutCallResult
    data object NoConnection : LogoutCallResult

    /** 401 tras intentar renovar: la sesión ya no era válida en el backend. */
    data object Unauthorized : LogoutCallResult
    data object Unknown : LogoutCallResult
}

/**
 * Endpoints de `/auth`. Login, registro y renovación usan [publicClient] (sin el plugin de sesión);
 * el logout usa [apiClient], que añade el Bearer y renueva si hace falta. Nunca lanza por red o
 * servidor; relanza la cancelación. Solo se interpreta `error`: `message` no se lee ni se registra.
 */
class AuthRemoteDataSource(
    private val publicClient: HttpClient,
    private val apiClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun login(email: String, password: String): AuthCallResult =
        sessionCall("/auth/login", LoginRequest(email, password), HttpStatusCode.OK) { code ->
            if (code == INVALID_CREDENTIALS) AuthFailure.InvalidCredentials else AuthFailure.Unknown
        }

    suspend fun register(displayName: String, email: String, password: String): AuthCallResult =
        sessionCall("/auth/register", RegisterRequest(email, password, displayName), HttpStatusCode.Created) { code ->
            // El email ya registrado no se distingue de otros rechazos para no revelarlo.
            if (code in REGISTRATION_REJECTED) AuthFailure.RegistrationRejected else AuthFailure.Unknown
        }

    suspend fun refresh(refreshToken: String): RefreshCallResult =
        when (val outcome = send(publicClient, "/auth/refresh", RefreshRequest(refreshToken))) {
            is Outcome.Timeout, is Outcome.Error -> RefreshCallResult.Unknown
            is Outcome.Offline -> RefreshCallResult.Network
            is Outcome.Response -> when (outcome.response.status) {
                HttpStatusCode.OK -> parse<TokensResponse>(outcome.response)
                    ?.let { RefreshCallResult.Success(it.accessToken, it.refreshToken) }
                    ?: RefreshCallResult.Unknown
                HttpStatusCode.Unauthorized ->
                    if (errorCode(outcome.response) == POSSIBLE_TOKEN_THEFT) {
                        RefreshCallResult.Rejected(SessionEndReason.Security)
                    } else {
                        RefreshCallResult.Rejected(SessionEndReason.Expired)
                    }
                else -> RefreshCallResult.Unknown
            }
        }

    suspend fun logout(): LogoutCallResult =
        when (val outcome = send<Any>(apiClient, "/auth/logout", body = null)) {
            is Outcome.Offline -> LogoutCallResult.NoConnection
            is Outcome.Timeout, is Outcome.Error -> LogoutCallResult.Unknown
            is Outcome.Response -> when (outcome.response.status) {
                HttpStatusCode.NoContent -> LogoutCallResult.Success
                HttpStatusCode.Unauthorized -> LogoutCallResult.Unauthorized
                else -> LogoutCallResult.Unknown
            }
        }

    private suspend inline fun <reified B : Any> sessionCall(
        path: String,
        body: B,
        expected: HttpStatusCode,
        failureFor: (code: String?) -> AuthFailure,
    ): AuthCallResult = when (val outcome = send(publicClient, path, body)) {
        // Una espera excesiva es un error genérico con reintento, distinto de no tener conexión.
        is Outcome.Timeout, is Outcome.Error -> AuthCallResult.Failure(AuthFailure.Unknown)
        is Outcome.Offline -> AuthCallResult.Failure(AuthFailure.Network)
        is Outcome.Response ->
            if (outcome.response.status != expected) {
                AuthCallResult.Failure(failureFor(errorCode(outcome.response)))
            } else {
                parse<SessionResponse>(outcome.response)
                    ?.let { AuthCallResult.Success(StoredSession(it.userId, it.accessToken, it.refreshToken)) }
                    ?: AuthCallResult.Failure(AuthFailure.Unknown)
            }
    }

    private suspend inline fun <reified B : Any> send(client: HttpClient, path: String, body: B?): Outcome = try {
        val response = client.post("${baseUrl.trimEnd('/')}$path") {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        Outcome.Response(response)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        Outcome.Timeout
    } catch (e: ConnectTimeoutException) {
        Outcome.Timeout
    } catch (e: SocketTimeoutException) {
        Outcome.Timeout
    } catch (e: IOException) {
        Outcome.Offline
    } catch (e: Exception) {
        Outcome.Error
    }

    private suspend inline fun <reified T> parse(response: HttpResponse): T? = try {
        response.body<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun errorCode(response: HttpResponse): String? = parse<ErrorResponse>(response)?.error

    private sealed interface Outcome {
        data class Response(val response: HttpResponse) : Outcome
        data object Offline : Outcome
        data object Timeout : Outcome
        data object Error : Outcome
    }

    @Serializable
    private data class LoginRequest(val email: String, val password: String) {
        override fun toString() = "LoginRequest"
    }

    @Serializable
    private data class RegisterRequest(val email: String, val password: String, val displayName: String) {
        override fun toString() = "RegisterRequest"
    }

    @Serializable
    private data class RefreshRequest(val refreshToken: String) {
        override fun toString() = "RefreshRequest"
    }

    @Serializable
    private data class SessionResponse(val userId: String, val accessToken: String, val refreshToken: String) {
        override fun toString() = "SessionResponse(userId=$userId)"
    }

    @Serializable
    private data class TokensResponse(val accessToken: String, val refreshToken: String) {
        override fun toString() = "TokensResponse"
    }

    @Serializable
    private data class ErrorResponse(val error: String? = null)

    private companion object {
        const val INVALID_CREDENTIALS = "IDENTITY.INVALID_CREDENTIALS"
        const val POSSIBLE_TOKEN_THEFT = "AUTH.POSSIBLE_TOKEN_THEFT"
        val REGISTRATION_REJECTED = setOf(
            "IDENTITY.EMAIL_ALREADY_REGISTERED",
            "IDENTITY.WEAK_PASSWORD",
            "IDENTITY.INVALID_DISPLAY_NAME",
            "SHARED.INVALID_EMAIL",
        )
    }
}
