package com.rocioplata.familyfinaiapp.data.movements

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.data.header.Cached
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Caché de solo lectura offline (RF-13): un JSON por familia y mes con `schemaVersion`, `userId` y `savedAt`.
 * Una versión desconocida, un tipo o una fecha ilegibles o un JSON ilegible se tratan como "sin datos".
 * Guarda además el último medio de pago usado, con una clave por usuario y familia (RF-07).
 */
class MovementsLocalDataSource(
    private val store: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun read(familyId: String, month: YearMonth): Cached<List<Movement>>? {
        val raw = store.data.first()[monthKey(familyId, month)] ?: return null
        return try {
            val envelope = json.decodeFromString(Envelope.serializer(), raw)
            if (envelope.schemaVersion != SCHEMA_VERSION) return null
            val movements = envelope.value.map { it.toDomainOrNull() ?: return null }
            Cached(envelope.userId, envelope.savedAtMillis, movements)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun save(
        familyId: String,
        month: YearMonth,
        userId: String,
        savedAtMillis: Long,
        movements: List<Movement>,
    ) {
        val envelope = Envelope(SCHEMA_VERSION, userId, savedAtMillis, movements.map(MovementJson::from))
        val raw = json.encodeToString(Envelope.serializer(), envelope)
        store.edit { it[monthKey(familyId, month)] = raw }
    }

    /** Descarta solo la caché de ese mes de esa familia (por ejemplo, al caducar a los 7 días). */
    suspend fun remove(familyId: String, month: YearMonth) {
        store.edit { it.remove(monthKey(familyId, month)) }
    }

    suspend fun lastUsedPaymentMethodId(userId: String, familyId: String): String? =
        store.data.first()[lastPaymentMethodKey(userId, familyId)]

    suspend fun saveLastUsedPaymentMethodId(userId: String, familyId: String, paymentMethodId: String) {
        store.edit { it[lastPaymentMethodKey(userId, familyId)] = paymentMethodId }
    }

    /** Borra todo: meses y último medio de pago de todos los usuarios y familias. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    /**
     * Borra los meses y el último medio de pago de las familias que no estén en [familyIds] (RF-23 de la 009); las demás
     * no se tocan. La familia se lee de la clave, así que sus identificadores no pueden llevar `_` (son UUID).
     */
    suspend fun retainFamilies(familyIds: Set<String>) {
        store.edit { prefs ->
            prefs.asMap().keys
                .filter { key -> familyOf(key.name)?.let { it !in familyIds } == true }
                .forEach { prefs.remove(stringPreferencesKey(it.name)) }
        }
    }

    // `movements_<familia>_<yyyy-MM>` y `last_payment_method_<usuario>_<familia>`; otras claves no son de una familia.
    private fun familyOf(keyName: String): String? = when {
        keyName.startsWith(MONTH_PREFIX) -> keyName.removePrefix(MONTH_PREFIX).substringBeforeLast('_')
        keyName.startsWith(LAST_PAYMENT_METHOD_PREFIX) -> keyName.substringAfterLast('_')
        else -> null
    }

    private fun monthKey(familyId: String, month: YearMonth) =
        stringPreferencesKey("$MONTH_PREFIX${familyId}_$month")

    private fun lastPaymentMethodKey(userId: String, familyId: String) =
        stringPreferencesKey("$LAST_PAYMENT_METHOD_PREFIX${userId}_$familyId")

    @Serializable
    private data class Envelope(
        val schemaVersion: Int,
        val userId: String,
        val savedAtMillis: Long,
        val value: List<MovementJson>,
    )

    @Serializable
    private data class MovementJson(
        val id: String,
        val type: String,
        val amountMinor: Long,
        val currency: String,
        val categoryId: String,
        val tagId: String? = null,
        val paymentMethodId: String? = null,
        val title: String,
        val note: String? = null,
        val date: String,
        val recordedBy: String,
        val createdAtMillis: Long,
    ) {
        fun toDomainOrNull(): Movement? {
            val movementType = MovementType.entries.firstOrNull { it.name == type } ?: return null
            if (!Money.isKnown(currency)) return null
            val day = try {
                LocalDate.parse(date)
            } catch (_: DateTimeException) {
                return null
            }
            return Movement(
                id = id,
                type = movementType,
                amount = Money(amountMinor, currency),
                categoryId = categoryId,
                tagId = tagId,
                paymentMethodId = paymentMethodId,
                title = title,
                note = note,
                date = day,
                recordedBy = recordedBy,
                createdAt = Instant.ofEpochMilli(createdAtMillis),
            )
        }

        companion object {
            fun from(m: Movement) = MovementJson(
                id = m.id,
                type = m.type.name,
                amountMinor = m.amount.amountMinor,
                currency = m.amount.currency,
                categoryId = m.categoryId,
                tagId = m.tagId,
                paymentMethodId = m.paymentMethodId,
                title = m.title,
                note = m.note,
                date = m.date.toString(),
                recordedBy = m.recordedBy,
                createdAtMillis = m.createdAt.toEpochMilli(),
            )
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MONTH_PREFIX = "movements_"
        const val LAST_PAYMENT_METHOD_PREFIX = "last_payment_method_"
    }
}
