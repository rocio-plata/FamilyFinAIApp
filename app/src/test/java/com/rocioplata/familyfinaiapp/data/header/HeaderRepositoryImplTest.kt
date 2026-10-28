package com.rocioplata.familyfinaiapp.data.header

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
import com.rocioplata.familyfinaiapp.session.Session
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class HeaderRepositoryImplTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_000_000_000_000L
    private val plata = Family("f-1", "Plata", FamilyRole.OWNER)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    /** Rutas que fallan por red; el resto responde con datos válidos. */
    private class Backend(var offline: Set<String> = emptySet())

    private class FixedSession(var userId: String = "u-1", var failure: SessionFailure? = null) : SessionProvider {
        override suspend fun current(): Session {
            failure?.let { throw SessionUnavailableException(it) }
            return Session(userId, "tok")
        }
    }

    private class Env(
        val repository: HeaderRepositoryImpl,
        val local: HeaderLocalDataSource,
        val backend: Backend,
        val session: FixedSession,
        var clock: Long,
    )

    private fun TestScope.env(): Env {
        val backend = Backend()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path in backend.offline) throw IOException("sin red")
            val body = when (path) {
                "/me/families" -> """[{"familyId":"f-1","name":"Plata","role":"OWNER"}]"""
                "/me/invitations" -> """[{"id":"a"},{"id":"b"}]"""
                else -> """{"displayName":"rocío"}"""
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "c.preferences_pb") }
        val local = HeaderLocalDataSource(store)
        val session = FixedSession()
        lateinit var env: Env
        val repository = HeaderRepositoryImpl(HeaderRemoteDataSource(client, "http://x"), local, session) { env.clock }
        env = Env(repository, local, backend, session, now)
        return env
    }

    private suspend fun Env.seed(ageMillis: Long, userId: String = "u-1") {
        val savedAt = clock - ageMillis
        local.saveFamilies(userId, savedAt, listOf(plata))
        local.savePendingInvitations(userId, savedAt, 7)
        local.saveDisplayName(userId, savedAt, "guardado")
    }

    private val allPaths = setOf("/me/families", "/me/invitations", "/me/profile")

    @Test
    fun conRed_sinCache_cargaLosTresRecursosYLosGuarda() = runTest {
        val env = env()

        val result = env.repository.refresh()

        val data = env.repository.header.first()
        assertTrue(result.isSuccessful)
        assertEquals(listOf(plata), data.families?.value)
        assertEquals(2, data.pendingInvitations?.value)
        assertEquals("rocío", data.displayName?.value)
        assertFalse(data.isStale)
        assertEquals(listOf(plata), env.local.readFamilies()?.value)
    }

    @Test
    fun cacheDe1Hora_sinRed_semuestraSinAlerta() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        env.backend.offline = allPaths

        val result = env.repository.refresh()

        val data = env.repository.header.first()
        assertEquals(HeaderFailure.Network, result.families)
        assertEquals("guardado", data.displayName?.value)
        assertEquals(7, data.pendingInvitations?.value)
        assertFalse(data.isStale)
    }

    @Test
    fun cacheDe30Horas_sinRed_semuestraConAlerta() = runTest {
        val env = env()
        env.seed(30.hours.inWholeMilliseconds)
        env.backend.offline = allPaths

        env.repository.refresh()

        val data = env.repository.header.first()
        assertEquals(listOf(plata), data.families?.value)
        assertTrue(data.isStale)
    }

    @Test
    fun cacheDe30Horas_unRefrescoCorrectoQuitaLaAlerta() = runTest {
        val env = env()
        env.seed(30.hours.inWholeMilliseconds)

        env.repository.refresh()

        assertFalse(env.repository.header.first().isStale)
    }

    @Test
    fun cacheDe8Dias_sinRed_seEliminaYNoSeMuestra() = runTest {
        val env = env()
        env.seed(8.days.inWholeMilliseconds)
        env.backend.offline = allPaths

        val result = env.repository.refresh()

        val data = env.repository.header.first()
        assertNull(data.families)
        assertNull(data.displayName)
        assertEquals(HeaderFailure.Network, result.families)
        assertNull(env.local.readFamilies())
        assertNull(env.local.readDisplayName())
    }

    @Test
    fun cacheDeOtroUsuario_seEliminaYNuncaSeMuestra() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds, userId = "otro")
        env.backend.offline = allPaths

        env.repository.refresh()

        assertNull(env.repository.header.first().families)
        assertNull(env.local.readDisplayName())
    }

    @Test
    fun cacheDeOtroUsuario_conRed_quedaSoloLoDelUsuarioActual() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds, userId = "otro")

        env.repository.refresh()

        assertEquals("rocío", env.repository.header.first().displayName?.value)
        assertEquals("u-1", env.local.readDisplayName()?.userId)
    }

    @Test
    fun sinRedYSinCache_devuelveErrorYNoHayDatos() = runTest {
        val env = env()
        env.backend.offline = allPaths

        val result = env.repository.refresh()

        assertFalse(result.isSuccessful)
        assertNull(env.repository.header.first().families)
    }

    @Test
    fun fallaSoloInvitaciones_lasFamiliasSeCargan_yElContadorNoSeMuestra() = runTest {
        val env = env()
        env.backend.offline = setOf("/me/invitations")

        val result = env.repository.refresh()

        val data = env.repository.header.first()
        assertNull(result.families)
        assertEquals(HeaderFailure.Network, result.pendingInvitations)
        assertEquals(listOf(plata), data.families?.value)
        assertNull(data.pendingInvitations)
    }

    @Test
    fun fallaSoloInvitacionesConCache_conservaElContadorGuardado() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        env.backend.offline = setOf("/me/invitations")

        env.repository.refresh()

        assertEquals(7, env.repository.header.first().pendingInvitations?.value)
    }

    @Test
    fun sesionNoDisponible_informaElFalloYNoTocaLaCache() = runTest {
        val env = env()
        env.seed(1.hours.inWholeMilliseconds)
        env.session.failure = SessionFailure.INVALID_CREDENTIALS

        val result = env.repository.refresh()

        assertEquals(HeaderFailure.Unauthorized, result.families)
        assertEquals(listOf(plata), env.local.readFamilies()?.value)
    }
}
