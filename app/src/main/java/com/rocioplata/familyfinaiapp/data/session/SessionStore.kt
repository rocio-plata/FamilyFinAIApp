package com.rocioplata.familyfinaiapp.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64

/** Sesión completa tal como se guarda en el teléfono. */
data class StoredSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
) {
    // Evita que los tokens acaben en logs por un toString accidental.
    override fun toString(): String = "StoredSession(userId=$userId)"
}

interface SessionStore {
    /** Sesión guardada, o null si no hay ninguna o no se puede leer (en ese caso se borra). */
    suspend fun read(): StoredSession?

    suspend fun save(session: StoredSession)

    suspend fun clear()
}

/**
 * Guarda la sesión en DataStore como un único valor cifrado (RF-05): Base64 de
 * [SessionCipher.encrypt] sobre un JSON con `schemaVersion`. Un valor ilegible, de otra versión
 * o cifrado con una clave que ya no existe (p. ej. tras restaurar el teléfono) equivale a no
 * tener sesión: se elimina para que se pida iniciar sesión de nuevo.
 */
class EncryptedSessionStore(
    private val store: DataStore<Preferences>,
    private val cipher: SessionCipher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionStore {

    override suspend fun read(): StoredSession? {
        val raw = store.data.first()[SESSION_KEY] ?: return null
        val session = decode(raw)
        if (session == null) clear()
        return session
    }

    override suspend fun save(session: StoredSession) {
        val payload = SessionJson(SCHEMA_VERSION, session.userId, session.accessToken, session.refreshToken)
        val encrypted = cipher.encrypt(json.encodeToString(SessionJson.serializer(), payload).encodeToByteArray())
        val encoded = Base64.getEncoder().encodeToString(encrypted)
        store.edit { it[SESSION_KEY] = encoded }
    }

    override suspend fun clear() {
        store.edit { it.remove(SESSION_KEY) }
    }

    // IllegalArgumentException cubre Base64 inválido y SerializationException.
    private fun decode(raw: String): StoredSession? = try {
        val plain = cipher.decrypt(Base64.getDecoder().decode(raw)).decodeToString()
        val payload = json.decodeFromString(SessionJson.serializer(), plain)
        if (payload.schemaVersion == SCHEMA_VERSION) {
            StoredSession(payload.userId, payload.accessToken, payload.refreshToken)
        } else {
            null
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: ProviderException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    @Serializable
    private data class SessionJson(
        val schemaVersion: Int,
        val userId: String,
        val accessToken: String,
        val refreshToken: String,
    ) {
        override fun toString() = "SessionJson(schemaVersion=$schemaVersion, userId=$userId)"
    }

    companion object {
        const val SCHEMA_VERSION = 1
        internal val SESSION_KEY = stringPreferencesKey("session")
    }
}
