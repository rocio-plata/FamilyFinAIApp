package com.rocioplata.familyfinaiapp.data.members

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.data.header.Cached
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Caché de solo lectura offline (RF-13): un JSON por familia con `schemaVersion`, `userId` y `savedAt`.
 * Una versión desconocida, un rol desconocido o un JSON ilegible se tratan como "sin datos". No guarda el
 * correo de los miembros.
 */
class FamilyMembersLocalDataSource(
    private val store: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun read(familyId: String): Cached<List<FamilyMember>>? {
        val raw = store.data.first()[keyOf(familyId)] ?: return null
        return try {
            val envelope = json.decodeFromString(Envelope.serializer(), raw)
            if (envelope.schemaVersion != SCHEMA_VERSION) return null
            val members = envelope.value.map { it.toDomainOrNull() ?: return null }
            Cached(envelope.userId, envelope.savedAtMillis, members)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun save(familyId: String, userId: String, savedAtMillis: Long, members: List<FamilyMember>) {
        val envelope = Envelope(SCHEMA_VERSION, userId, savedAtMillis, members.map(MemberJson::from))
        val raw = json.encodeToString(Envelope.serializer(), envelope)
        store.edit { it[keyOf(familyId)] = raw }
    }

    /** Descarta solo la caché de esa familia (por ejemplo, al caducar a los 7 días). */
    suspend fun remove(familyId: String) {
        store.edit { it.remove(keyOf(familyId)) }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    /** Borra la caché de las familias que no estén en [familyIds] (RF-23 de la 009); las demás no se tocan. */
    suspend fun retainFamilies(familyIds: Set<String>) {
        store.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(KEY_PREFIX) && it.name.removePrefix(KEY_PREFIX) !in familyIds }
                .forEach { prefs.remove(stringPreferencesKey(it.name)) }
        }
    }

    private fun keyOf(familyId: String) = stringPreferencesKey("$KEY_PREFIX$familyId")

    @Serializable
    private data class Envelope(
        val schemaVersion: Int,
        val userId: String,
        val savedAtMillis: Long,
        val value: List<MemberJson>,
    )

    @Serializable
    private data class MemberJson(val userId: String, val role: String, val displayName: String? = null) {
        fun toDomainOrNull(): FamilyMember? {
            val parsed = FamilyRole.entries.firstOrNull { it.name == role } ?: return null
            return FamilyMember(userId, displayName, parsed)
        }

        companion object {
            fun from(member: FamilyMember) = MemberJson(member.userId, member.role.name, member.displayName)
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val KEY_PREFIX = "members_"
    }
}
