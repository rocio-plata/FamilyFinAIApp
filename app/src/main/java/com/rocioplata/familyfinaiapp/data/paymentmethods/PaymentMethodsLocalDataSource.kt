package com.rocioplata.familyfinaiapp.data.paymentmethods

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.data.header.Cached
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Caché de solo lectura offline (RF-11): un JSON por familia con `schemaVersion`, `userId` y `savedAt`.
 * Una versión desconocida, un valor desconocido o un JSON ilegible se tratan como "sin datos".
 */
class PaymentMethodsLocalDataSource(
    private val store: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun read(familyId: String): Cached<List<PaymentMethod>>? {
        val raw = store.data.first()[keyOf(familyId)] ?: return null
        return try {
            val envelope = json.decodeFromString(Envelope.serializer(), raw)
            if (envelope.schemaVersion != SCHEMA_VERSION) return null
            val methods = envelope.value.map { it.toDomainOrNull() ?: return null }
            Cached(envelope.userId, envelope.savedAtMillis, methods)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun save(familyId: String, userId: String, savedAtMillis: Long, paymentMethods: List<PaymentMethod>) {
        val envelope = Envelope(SCHEMA_VERSION, userId, savedAtMillis, paymentMethods.map(PaymentMethodJson::from))
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
        val value: List<PaymentMethodJson>,
    )

    @Serializable
    private data class PaymentMethodJson(val id: String, val name: String, val status: String) {
        fun toDomainOrNull(): PaymentMethod? {
            val methodStatus = PaymentMethodStatus.entries.firstOrNull { it.name == status } ?: return null
            return PaymentMethod(id, name, methodStatus)
        }

        companion object {
            fun from(method: PaymentMethod) = PaymentMethodJson(method.id, method.name, method.status.name)
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val KEY_PREFIX = "payment_methods_"
    }
}
