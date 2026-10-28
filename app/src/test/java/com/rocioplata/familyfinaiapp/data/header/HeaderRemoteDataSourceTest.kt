package com.rocioplata.familyfinaiapp.data.header

import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
import com.rocioplata.familyfinaiapp.session.Session
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class HeaderRemoteDataSourceTest {
    private val session = Session("u-1", "tok")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HeaderRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        return HeaderRemoteDataSource(client, "http://localhost:3000")
    }

    private fun reply(status: HttpStatusCode, body: String): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(body, status, jsonHeaders) }

    @Test
    fun familias_exito_mapeaYEnviaBearer() = runTest {
        var auth: String? = null
        var path: String? = null
        val source = source {
            auth = it.headers[HttpHeaders.Authorization]
            path = it.url.encodedPath
            respond(
                """[{"familyId":"f-1","name":"Plata","role":"OWNER","defaultCurrency":"CLP","memberCount":3},
                    {"familyId":"f-2","name":"Depto","role":"MEMBER","defaultCurrency":"CLP","memberCount":2}]""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        val result = source.families(session)

        assertEquals(
            RemoteResult.Success(listOf(Family("f-1", "Plata", FamilyRole.OWNER), Family("f-2", "Depto", FamilyRole.MEMBER))),
            result,
        )
        assertEquals("Bearer tok", auth)
        assertEquals("/me/families", path)
    }

    @Test
    fun invitaciones_exito_devuelveElTamanoDeLaLista() = runTest {
        val source = source(reply(HttpStatusCode.OK, """[{"id":"a","status":"PENDING"},{"id":"b","status":"PENDING"}]"""))

        assertEquals(RemoteResult.Success(2), source.pendingInvitations(session))
    }

    @Test
    fun perfil_exito_devuelveElNombre() = runTest {
        val source = source(reply(HttpStatusCode.OK, """{"userId":"u-1","email":"a@b.c","displayName":"rocío","createdAt":"x"}"""))

        assertEquals(RemoteResult.Success("rocío"), source.displayName(session))
    }

    @Test
    fun rolDesconocido_esErrorDeDatos() = runTest {
        val source = source(reply(HttpStatusCode.OK, """[{"familyId":"f","name":"N","role":"ADMIN"}]"""))

        assertEquals(RemoteResult.Failure(HeaderFailure.InvalidData), source.families(session))
    }

    @Test
    fun cuerpoMalformado_esErrorDeDatos() = runTest {
        val source = source(reply(HttpStatusCode.OK, """{"no":"es una lista"}"""))

        assertEquals(RemoteResult.Failure(HeaderFailure.InvalidData), source.families(session))
    }

    @Test
    fun errorHttp403ConRolInsuficiente_seIdentifica() = runTest {
        val source = source(reply(HttpStatusCode.Forbidden, """{"error":"FAMILY.INSUFFICIENT_ROLE","message":"secreto","details":null}"""))

        assertEquals(RemoteResult.Failure(HeaderFailure.InsufficientRole), source.families(session))
    }

    @Test
    fun error401_esNoAutorizado() = runTest {
        val source = source(reply(HttpStatusCode.Unauthorized, """{"error":"AUTH.INVALID_TOKEN","message":"x","details":null}"""))

        assertEquals(RemoteResult.Failure(HeaderFailure.Unauthorized), source.pendingInvitations(session))
    }

    @Test
    fun error500_esDesconocido() = runTest {
        val source = source(reply(HttpStatusCode.InternalServerError, "boom"))

        assertEquals(RemoteResult.Failure(HeaderFailure.Unknown), source.displayName(session))
    }

    @Test
    fun timeout_esErrorDeRed() = runTest {
        val source = source { throw HttpRequestTimeoutException("http://localhost:3000/me/families", 15_000L) }

        assertEquals(RemoteResult.Failure(HeaderFailure.Network), source.families(session))
    }

    @Test
    fun sinConexion_esErrorDeRed() = runTest {
        val source = source { throw IOException("sin red") }

        assertEquals(RemoteResult.Failure(HeaderFailure.Network), source.pendingInvitations(session))
    }
}
