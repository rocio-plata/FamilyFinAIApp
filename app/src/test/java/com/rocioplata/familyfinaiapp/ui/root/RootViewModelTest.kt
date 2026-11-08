package com.rocioplata.familyfinaiapp.ui.root

import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    private val rocio = StoredSession("u-1", "acc-1", "ref-1")

    private class FakeStore(
        var stored: StoredSession? = null,
        var readFailure: Exception? = null,
        var clearFailure: Exception? = null,
    ) : SessionStore {
        override suspend fun read(): StoredSession? {
            readFailure?.let { throw it }
            return stored
        }

        override suspend fun save(session: StoredSession) {
            stored = session
        }

        override suspend fun clear() {
            clearFailure?.let { throw it }
            stored = null
        }
    }

    private fun manager(store: FakeStore): SessionManager {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) })
        return SessionManager(store, AuthRemoteDataSource(client, client, "http://localhost:3000")) {}
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun mientrasLeeLaSesion_esLoading() = runTest {
        val viewModel = RootViewModel(manager(FakeStore()))

        assertEquals(SessionState.Loading, viewModel.sessionState.value)
    }

    @Test
    fun sinSesionGuardada_muestraElAcceso() = runTest {
        val viewModel = RootViewModel(manager(FakeStore()))

        advanceUntilIdle()

        assertEquals(SessionState.SignedOut(null), viewModel.sessionState.value)
    }

    @Test
    fun conSesionGuardada_entraDirectoALaPantallaPrincipal() = runTest {
        val viewModel = RootViewModel(manager(FakeStore(stored = rocio)))

        advanceUntilIdle()

        assertEquals(SessionState.SignedIn("u-1", 1), viewModel.sessionState.value)
    }

    @Test
    fun lecturaImposible_seTrataComoSinSesionYNoCierraLaApp() = runTest {
        val store = FakeStore(readFailure = IllegalStateException("no se puede leer"))
        val viewModel = RootViewModel(manager(store))

        advanceUntilIdle()

        assertEquals(SessionState.SignedOut(null), viewModel.sessionState.value)
    }

    @Test
    fun falloAlBorrarLosDatos_muestraElAccesoYNoCierraLaApp() = runTest {
        val store = FakeStore(clearFailure = IllegalStateException("no se puede borrar"))
        val viewModel = RootViewModel(manager(store))

        advanceUntilIdle()

        assertEquals(SessionState.SignedOut(null), viewModel.sessionState.value)
    }

    @Test
    fun sesionTerminadaDespues_muestraElAccesoConSuMotivo() = runTest {
        val manager = manager(FakeStore(stored = rocio))
        val viewModel = RootViewModel(manager)
        advanceUntilIdle()

        manager.endSession(SessionEndReason.Expired)
        advanceUntilIdle()

        assertEquals(SessionState.SignedOut(SessionEndReason.Expired), viewModel.sessionState.value)
    }

    @Test
    fun nuevoInicioDeSesion_cambiaLaGeneracion() = runTest {
        val manager = manager(FakeStore())
        val viewModel = RootViewModel(manager)
        advanceUntilIdle()

        manager.start(rocio)
        advanceUntilIdle()
        val first = viewModel.sessionState.value
        manager.endSession(SessionEndReason.LoggedOut)
        manager.start(rocio)
        advanceUntilIdle()

        assertEquals(SessionState.SignedIn("u-1", 1), first)
        assertEquals(SessionState.SignedIn("u-1", 2), viewModel.sessionState.value)
    }

    @Test
    fun ultimaGeneracion_empiezaEnCero_ySobreviveAlCierreDeSesion() = runTest {
        val manager = manager(FakeStore())
        val viewModel = RootViewModel(manager)
        advanceUntilIdle()
        assertEquals(0L, viewModel.lastGeneration.value)

        manager.start(rocio)
        advanceUntilIdle()
        assertEquals(1L, viewModel.lastGeneration.value)

        // Cada episodio sin sesión se identifica con la última generación iniciada.
        manager.endSession(SessionEndReason.Expired)
        advanceUntilIdle()
        assertEquals(1L, viewModel.lastGeneration.value)

        manager.start(rocio)
        advanceUntilIdle()
        assertEquals(2L, viewModel.lastGeneration.value)
    }

    @Test
    fun conSesionGuardada_laGeneracionInicialEsUno() = runTest {
        val viewModel = RootViewModel(manager(FakeStore(stored = rocio)))

        advanceUntilIdle()

        assertEquals(1L, viewModel.lastGeneration.value)
    }
}
