package com.rocioplata.familyfinaiapp.session

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.IOException

/**
 * Temporal hasta la spec de sesión: inicia sesión con credenciales de desarrollo
 * (local.properties) y conserva la sesión en memoria, sin refresco.
 */
class StaticSessionProvider(
    private val client: HttpClient,
    private val baseUrl: String,
    private val email: String,
    private val password: String,
) : SessionProvider {
    private val mutex = Mutex()
    private var session: Session? = null

    override suspend fun current(): Session = mutex.withLock {
        session ?: login().also { session = it }
    }

    private suspend fun login(): Session {
        if (baseUrl.isBlank() || email.isBlank() || password.isBlank()) {
            throw SessionUnavailableException(SessionFailure.NOT_CONFIGURED)
        }
        val response: HttpResponse = try {
            client.post("${baseUrl.trimEnd('/')}/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw SessionUnavailableException(SessionFailure.NETWORK)
        } catch (e: Exception) {
            throw SessionUnavailableException(SessionFailure.UNKNOWN)
        }
        return when (response.status) {
            HttpStatusCode.OK -> parse(response)
            HttpStatusCode.Unauthorized -> throw SessionUnavailableException(SessionFailure.INVALID_CREDENTIALS)
            else -> throw SessionUnavailableException(SessionFailure.UNKNOWN)
        }
    }

    private suspend fun parse(response: HttpResponse): Session = try {
        val body: LoginResponse = response.body()
        Session(body.userId, body.accessToken)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SessionUnavailableException(SessionFailure.UNKNOWN)
    }

    @Serializable
    private data class LoginRequest(val email: String, val password: String) {
        override fun toString() = "LoginRequest"
    }

    @Serializable
    private data class LoginResponse(val userId: String, val accessToken: String) {
        override fun toString() = "LoginResponse"
    }
}
