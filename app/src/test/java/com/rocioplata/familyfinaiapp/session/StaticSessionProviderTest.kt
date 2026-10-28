package com.rocioplata.familyfinaiapp.session

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StaticSessionProviderTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun provider(
        status: HttpStatusCode,
        body: String,
        email: String = "a@test.com",
        onRequest: () -> Unit = {},
    ): StaticSessionProvider {
        val engine = MockEngine {
            onRequest()
            respond(body, status, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return StaticSessionProvider(client, "http://10.0.2.2:3000", email, "secret")
    }

    @Test
    fun loginCorrecto_devuelveUserIdYTokenYLoReutiliza() = runTest {
        var calls = 0
        val provider = provider(
            HttpStatusCode.OK,
            """{"userId":"u-1","email":"a@test.com","displayName":"A","accessToken":"tok","refreshToken":"r"}""",
        ) { calls++ }

        val first = provider.current()
        val second = provider.current()

        assertEquals(Session("u-1", "tok"), first)
        assertEquals(first, second)
        assertEquals(1, calls)
    }

    @Test
    fun credencialesInvalidas_lanzaInvalidCredentials() = runTest {
        val provider = provider(
            HttpStatusCode.Unauthorized,
            """{"error":"IDENTITY.INVALID_CREDENTIALS","message":"x","details":null}""",
        )

        val error = assertThrows(SessionUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking { provider.current() }
        }

        assertEquals(SessionFailure.INVALID_CREDENTIALS, error.failure)
    }

    @Test
    fun sinCredenciales_lanzaNotConfiguredSinLlamarALaRed() = runTest {
        var calls = 0
        val provider = provider(HttpStatusCode.OK, "{}", email = "") { calls++ }

        val error = assertThrows(SessionUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking { provider.current() }
        }

        assertEquals(SessionFailure.NOT_CONFIGURED, error.failure)
        assertEquals(0, calls)
    }

    @Test
    fun toString_noExponeElToken() {
        assertEquals("Session(userId=u-1)", Session("u-1", "tok").toString())
    }
}
