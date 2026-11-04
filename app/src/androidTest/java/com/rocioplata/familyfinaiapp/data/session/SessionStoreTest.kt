package com.rocioplata.familyfinaiapp.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.Base64
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keyAlias = "familyfin_session_key_test"
    private val session = StoredSession("user-1", "access-token-abc", "refresh-token-xyz")

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        file = File(context.filesDir, "datastore/session_test_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
    }

    private fun newStore() = EncryptedSessionStore(dataStore, KeystoreSessionCipher(keyAlias))

    @Test
    fun sesionGuardada_seLeeDesdeOtraInstancia() = runBlocking {
        newStore().save(session)

        assertEquals(session, newStore().read())
    }

    @Test
    fun sinSesionGuardada_devuelveNull() = runBlocking {
        assertNull(newStore().read())
    }

    @Test
    fun clear_eliminaLaSesion() = runBlocking {
        val store = newStore()
        store.save(session)

        store.clear()

        assertNull(store.read())
    }

    @Test
    fun valorCorrupto_devuelveNullYSeBorra() = runBlocking {
        dataStore.edit { it[EncryptedSessionStore.SESSION_KEY] = "no-es-un-valor-cifrado" }

        assertNull(newStore().read())
        assertNull(dataStore.data.first()[EncryptedSessionStore.SESSION_KEY])
    }

    @Test
    fun claveDelKeystorePerdida_devuelveNullYSeBorra() = runBlocking {
        newStore().save(session)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)

        assertNull(newStore().read())
        assertNull(dataStore.data.first()[EncryptedSessionStore.SESSION_KEY])
    }

    @Test
    fun versionDeEsquemaDesconocida_devuelveNullYSeBorra() = runBlocking {
        val payload = """{"schemaVersion":99,"userId":"u","accessToken":"a","refreshToken":"r"}"""
        val encrypted = KeystoreSessionCipher(keyAlias).encrypt(payload.encodeToByteArray())
        dataStore.edit { it[EncryptedSessionStore.SESSION_KEY] = Base64.getEncoder().encodeToString(encrypted) }

        assertNull(newStore().read())
        assertNull(dataStore.data.first()[EncryptedSessionStore.SESSION_KEY])
    }

    @Test
    fun archivo_noContieneLosTokensEnClaro() = runBlocking {
        newStore().save(session)

        val content = file.readBytes().decodeToString()
        assertTrue(file.length() > 0)
        assertFalse(content.contains(session.accessToken))
        assertFalse(content.contains(session.refreshToken))
        assertFalse(content.contains(session.userId))
    }

    @Test
    fun toString_noIncluyeTokens() {
        val text = session.toString()

        assertFalse(text.contains(session.accessToken))
        assertFalse(text.contains(session.refreshToken))
    }
}
