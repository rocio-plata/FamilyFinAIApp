package com.rocioplata.familyfinaiapp

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rocioplata.familyfinaiapp.domain.auth.AuthResult
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integración del cableado de `AppContainer` contra un backend real (003-auth, T-12). Necesita el
 * backend en `BuildConfig.BACKEND_BASE_URL` y una cuenta existente, que llegan como argumentos de
 * instrumentación `email` y `password`; sin ellos el test se omite. Usa el almacenamiento real de
 * la app (DataStore `session` y `header_cache`, Keystore) y lo deja vacío al terminar. No llama a
 * `logout`, por lo que no invalida las sesiones del usuario en el backend.
 */
@RunWith(AndroidJUnit4::class)
class AuthWiringTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var email: String
    private lateinit var password: String

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        email = args.getString("email").orEmpty()
        password = args.getString("password").orEmpty()
        assumeTrue("Faltan los argumentos email y password", email.isNotBlank() && password.isNotBlank())
        runBlocking { resetStorage() }
    }

    @After
    fun tearDown() {
        if (email.isBlank() || password.isBlank()) return
        runBlocking { resetStorage() }
    }

    @Test
    fun login_cabeceraConBearer_sesionPersisteYSeBorra() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            val first = AppContainer(context)
            first.sessionManager.load()
            assertEquals(SessionState.SignedOut(null), first.sessionManager.state.value)

            // Login real: la sesión queda guardada y activa.
            assertEquals(AuthResult.Success, first.authRepository.login(email, password))
            val signedIn = first.sessionManager.state.value
            assertTrue("Se esperaba SignedIn y fue $signedIn", signedIn is SessionState.SignedIn)

            // Las tres llamadas de la cabecera salen por apiClient: sin Bearer el backend respondería 401.
            val refresh = first.headerRepository.refresh()
            assertNull("families", refresh.families)
            assertNull("pendingInvitations", refresh.pendingInvitations)
            assertNull("profile", refresh.profile)
            val header = first.headerRepository.header.first()
            assertTrue("Sin familias", header.families?.value.orEmpty().isNotEmpty())
            assertNotNull("Sin nombre", header.displayName)

            // Contenedor nuevo (arranque en frío de la sesión): se recupera del Keystore sin volver a iniciar sesión.
            val second = AppContainer(context)
            assertEquals(SessionState.Loading, second.sessionManager.state.value)
            second.sessionManager.load()
            val restored = second.sessionManager.state.value
            assertTrue("Se esperaba SignedIn y fue $restored", restored is SessionState.SignedIn)
            assertEquals((signedIn as SessionState.SignedIn).userId, (restored as SessionState.SignedIn).userId)
            val afterRestart = second.headerRepository.refresh()
            assertNull("families tras reinicio", afterRestart.families)

            // Terminar la sesión borra la sesión guardada y la caché de la cabecera (RF-09).
            second.sessionManager.endSession(SessionEndReason.LoggedOut)
            assertEquals(SessionState.SignedOut(SessionEndReason.LoggedOut), second.sessionManager.state.value)
            assertNull(second.headerRepository.header.first().families)
            assertTrue("Caché de cabecera no vacía", second.headerCacheStore.data.first().isEmpty())

            val third = AppContainer(context)
            third.sessionManager.load()
            assertEquals(SessionState.SignedOut(null), third.sessionManager.state.value)
        }
    }

    // Parte de un estado limpio y no deja sesión ni caché de la ejecución.
    private suspend fun resetStorage() {
        val container = AppContainer(context)
        container.sessionManager.load()
        container.sessionManager.endSession(SessionEndReason.LoggedOut)
        container.headerRepository.clear()
    }

    private fun Preferences.isEmpty() = asMap().isEmpty()

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
