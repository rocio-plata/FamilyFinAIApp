package com.rocioplata.familyfinaiapp.data.header

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Valor guardado junto al usuario al que pertenece y al instante en que se obtuvo (epoch ms UTC). */
data class Cached<T>(
    val userId: String,
    val savedAtMillis: Long,
    val value: T,
)

/**
 * Caché de solo lectura offline (RF-07): un JSON por recurso con `schemaVersion`, `userId` y `savedAt`.
 * Una versión desconocida o un JSON ilegible se tratan como "sin datos".
 */
class HeaderLocalDataSource(
    private val store: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun readFamilies(): Cached<List<Family>>? =
        read(FAMILIES_KEY, ListSerializer(FamilyJson.serializer()))?.let { cached ->
            val families = cached.value.map { it.toDomainOrNull() ?: return null }
            Cached(cached.userId, cached.savedAtMillis, families)
        }

    suspend fun saveFamilies(userId: String, savedAtMillis: Long, families: List<Family>) =
        save(FAMILIES_KEY, ListSerializer(FamilyJson.serializer()), userId, savedAtMillis, families.map(FamilyJson::from))

    suspend fun readPendingInvitations(): Cached<Int>? = read(INVITATIONS_KEY, Int.serializer())

    suspend fun savePendingInvitations(userId: String, savedAtMillis: Long, count: Int) =
        save(INVITATIONS_KEY, Int.serializer(), userId, savedAtMillis, count)

    suspend fun readDisplayName(): Cached<String>? = read(PROFILE_KEY, String.serializer())

    suspend fun saveDisplayName(userId: String, savedAtMillis: Long, displayName: String) =
        save(PROFILE_KEY, String.serializer(), userId, savedAtMillis, displayName)

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private suspend fun <T> read(key: Preferences.Key<String>, serializer: KSerializer<T>): Cached<T>? {
        val raw = store.data.first()[key] ?: return null
        return try {
            val envelope = json.decodeFromString(Envelope.serializer(serializer), raw)
            if (envelope.schemaVersion != SCHEMA_VERSION) {
                null
            } else {
                Cached(envelope.userId, envelope.savedAtMillis, envelope.value)
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun <T> save(
        key: Preferences.Key<String>,
        serializer: KSerializer<T>,
        userId: String,
        savedAtMillis: Long,
        value: T,
    ) {
        val raw = json.encodeToString(Envelope.serializer(serializer), Envelope(SCHEMA_VERSION, userId, savedAtMillis, value))
        store.edit { it[key] = raw }
    }

    @Serializable
    private data class Envelope<T>(
        val schemaVersion: Int,
        val userId: String,
        val savedAtMillis: Long,
        val value: T,
    )

    @Serializable
    private data class FamilyJson(val id: String, val name: String, val role: String) {
        fun toDomainOrNull(): Family? {
            val parsed = FamilyRole.entries.firstOrNull { it.name == role } ?: return null
            return Family(id, name, parsed)
        }

        companion object {
            fun from(family: Family) = FamilyJson(family.id, family.name, family.role.name)
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        val FAMILIES_KEY = stringPreferencesKey("families")
        val INVITATIONS_KEY = stringPreferencesKey("pending_invitations")
        val PROFILE_KEY = stringPreferencesKey("profile")
    }
}
