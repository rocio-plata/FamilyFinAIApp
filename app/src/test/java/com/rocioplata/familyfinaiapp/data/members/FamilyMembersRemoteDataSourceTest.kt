package com.rocioplata.familyfinaiapp.data.members

import com.rocioplata.familyfinaiapp.data.network.SessionAuth
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class FamilyMembersRemoteDataSourceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun source(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): FamilyMembersRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
            install(SessionAuth) { accessToken = { "tok" } }
        }
        return FamilyMembersRemoteDataSource(client, "http://localhost:3000")
    }

    private fun reply(
        status: HttpStatusCode,
        body: String = "",
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(body, status, jsonHeaders) }

    private fun FamilyMembersResult<List<FamilyMember>>.failure() = (this as FamilyMembersResult.Failure).failure
    private fun FamilyMembersResult<List<FamilyMember>>.value() = (this as FamilyMembersResult.Success).value

    @Test
    fun pideLosMiembrosDeLaFamiliaYEnviaBearer() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var auth: String? = null
        val source = source {
            method = it.method
            path = it.url.encodedPath
            auth = it.headers[HttpHeaders.Authorization]
            respond("[]", HttpStatusCode.OK, jsonHeaders)
        }

        source.members("f-1")

        assertEquals(HttpMethod.Get, method)
        assertEquals("/families/f-1/members", path)
        assertEquals("Bearer tok", auth)
    }

    @Test
    fun mapeaMiembrosConYSinNombre_conservandoElOrden() = runTest {
        val body = """[
            {"userId":"u-1","role":"OWNER","displayName":"Valentina Plata","joinedAt":"2026-03-01T10:00:00.000Z"},
            {"userId":"u-2","role":"MEMBER","joinedAt":"2026-03-05T15:20:00.000Z"},
            {"userId":"u-3","role":"MEMBER","displayName":null},
            {"userId":"u-4","role":"MEMBER","displayName":""}
        ]"""

        val result = source(reply(HttpStatusCode.OK, body)).members("f-1")

        assertEquals(
            listOf(
                FamilyMember("u-1", "Valentina Plata", FamilyRole.OWNER),
                FamilyMember("u-2", null, FamilyRole.MEMBER),
                FamilyMember("u-3", null, FamilyRole.MEMBER),
                FamilyMember("u-4", "", FamilyRole.MEMBER),
            ),
            result.value(),
        )
    }

    @Test
    fun elCorreoNoSeLee_ElDominioNoTieneDondeGuardarlo() = runTest {
        val body = """[{"userId":"u-1","role":"OWNER","displayName":"A","email":"a@example.com"}]"""

        val member = source(reply(HttpStatusCode.OK, body)).members("f-1").value().single()

        assertEquals(FamilyMember("u-1", "A", FamilyRole.OWNER), member)
        assertEquals(false, member.toString().contains("a@example.com"))
    }

    @Test
    fun listaVacia_esExito() = runTest {
        assertEquals(emptyList<FamilyMember>(), source(reply(HttpStatusCode.OK, "[]")).members("f-1").value())
    }

    @Test
    fun rolDesconocido_esInvalidData() = runTest {
        val body = """[{"userId":"u-1","role":"ADMIN","displayName":"A"}]"""

        assertEquals(FamilyMembersFailure.InvalidData, source(reply(HttpStatusCode.OK, body)).members("f-1").failure())
    }

    @Test
    fun jsonIlegibleOFormaErronea_esInvalidData() = runTest {
        assertEquals(
            FamilyMembersFailure.InvalidData,
            source(reply(HttpStatusCode.OK, "no es json")).members("f-1").failure(),
        )
        assertEquals(
            FamilyMembersFailure.InvalidData,
            source(reply(HttpStatusCode.OK, """{"userId":"u-1"}""")).members("f-1").failure(),
        )
        assertEquals(
            FamilyMembersFailure.InvalidData,
            source(reply(HttpStatusCode.OK, """[{"role":"OWNER"}]""")).members("f-1").failure(),
        )
    }

    @Test
    fun prohibido_esNotAllowed_cualquieraSeaElCodigo() = runTest {
        for (code in listOf("FAMILY.NOT_A_MEMBER", "FAMILY.INSUFFICIENT_ROLE")) {
            val result = source(reply(HttpStatusCode.Forbidden, """{"error":"$code","message":"no se lee"}""")).members("f-1")

            assertEquals(code, FamilyMembersFailure.NotAllowed, result.failure())
        }
    }

    @Test
    fun noAutorizado_esUnauthorized() = runTest {
        assertEquals(
            FamilyMembersFailure.Unauthorized,
            source(reply(HttpStatusCode.Unauthorized, """{"error":"AUTH.INVALID_TOKEN"}""")).members("f-1").failure(),
        )
    }

    @Test
    fun errorDelServidorOCodigoDesconocido_esUnknown() = runTest {
        assertEquals(
            FamilyMembersFailure.Unknown,
            source(reply(HttpStatusCode.InternalServerError, """{"error":"INTERNAL.UNEXPECTED_ERROR"}""")).members("f-1").failure(),
        )
        assertEquals(FamilyMembersFailure.Unknown, source(reply(HttpStatusCode.NotFound, "")).members("f-1").failure())
        assertEquals(FamilyMembersFailure.Unknown, source(reply(HttpStatusCode.InternalServerError, "no json")).members("f-1").failure())
    }

    @Test
    fun fallosDeRed_sonNetwork_tambienLaEsperaExcesiva() = runTest {
        assertEquals(FamilyMembersFailure.Network, source { throw IOException("sin red") }.members("f-1").failure())
        assertEquals(
            FamilyMembersFailure.Network,
            source { throw HttpRequestTimeoutException("http://x", 1L) }.members("f-1").failure(),
        )
    }

    @Test
    fun excepcionInesperada_esUnknown() = runTest {
        assertEquals(FamilyMembersFailure.Unknown, source { throw IllegalStateException("x") }.members("f-1").failure())
    }

    @Test
    fun laCancelacion_seRelanza() = runTest {
        val source = source { throw CancellationException("cancelado") }

        assertThrows(CancellationException::class.java) { runBlocking { source.members("f-1") } }
    }

    @Test
    fun conCorreos_devuelveElCorreoDeCadaMiembroQueLoTrae_recortadoYSinVacios() = runTest {
        val body = """[{"userId":"u-1","role":"OWNER","displayName":"A","email":" a@example.com "},""" +
            """{"userId":"u-2","role":"MEMBER","displayName":"B","email":null},""" +
            """{"userId":"u-3","role":"MEMBER","email":"  "},""" +
            """{"userId":"u-4","role":"MEMBER"}]"""

        val payload = (source(reply(HttpStatusCode.OK, body)).membersWithEmails("f-1") as FamilyMembersResult.Success).value

        assertEquals(mapOf("u-1" to "a@example.com"), payload.emails)
        assertEquals(listOf("u-1", "u-2", "u-3", "u-4"), payload.members.map { it.userId })
    }

    @Test
    fun conCorreos_unIntegrante_noRecibeCorreos() = runTest {
        val body = """[{"userId":"u-1","role":"OWNER","displayName":"A"}]"""

        val payload = (source(reply(HttpStatusCode.OK, body)).membersWithEmails("f-1") as FamilyMembersResult.Success).value

        assertEquals(emptyMap<String, String>(), payload.emails)
    }

    @Test
    fun conCorreos_losMismosFallosQueSinCorreos() = runTest {
        assertEquals(
            FamilyMembersResult.Failure(FamilyMembersFailure.NotAllowed),
            source(reply(HttpStatusCode.Forbidden)).membersWithEmails("f-1"),
        )
        assertEquals(
            FamilyMembersResult.Failure(FamilyMembersFailure.InvalidData),
            source(reply(HttpStatusCode.OK, """[{"userId":"u-1","role":"JEFE"}]""")).membersWithEmails("f-1"),
        )
        assertEquals(
            FamilyMembersResult.Failure(FamilyMembersFailure.Network),
            source { throw IOException("sin red") }.membersWithEmails("f-1"),
        )
    }

    @Test
    fun conCorreos_elTextoDelResultadoNoLlevaCorreos() = runTest {
        val body = """[{"userId":"u-1","role":"OWNER","displayName":"Ana","email":"a@example.com"}]"""

        val payload = (source(reply(HttpStatusCode.OK, body)).membersWithEmails("f-1") as FamilyMembersResult.Success).value

        assertEquals(false, payload.toString().contains("example.com"))
    }
}
