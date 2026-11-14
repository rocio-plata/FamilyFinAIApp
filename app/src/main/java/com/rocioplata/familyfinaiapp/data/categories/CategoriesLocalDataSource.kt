package com.rocioplata.familyfinaiapp.data.categories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rocioplata.familyfinaiapp.data.header.Cached
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Caché de solo lectura offline (RF-20): un JSON por familia con `schemaVersion`, `userId` y `savedAt`.
 * Una versión desconocida, un valor desconocido o un JSON ilegible se tratan como "sin datos".
 */
class CategoriesLocalDataSource(
    private val store: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun read(familyId: String): Cached<List<Category>>? {
        val raw = store.data.first()[keyOf(familyId)] ?: return null
        return try {
            val envelope = json.decodeFromString(Envelope.serializer(), raw)
            if (envelope.schemaVersion != SCHEMA_VERSION) return null
            val categories = envelope.value.map { it.toDomainOrNull() ?: return null }
            Cached(envelope.userId, envelope.savedAtMillis, categories)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun save(familyId: String, userId: String, savedAtMillis: Long, categories: List<Category>) {
        val envelope = Envelope(SCHEMA_VERSION, userId, savedAtMillis, categories.map(CategoryJson::from))
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

    private fun keyOf(familyId: String) = stringPreferencesKey("$KEY_PREFIX$familyId")

    @Serializable
    private data class Envelope(
        val schemaVersion: Int,
        val userId: String,
        val savedAtMillis: Long,
        val value: List<CategoryJson>,
    )

    @Serializable
    private data class CategoryJson(
        val id: String,
        val type: String,
        val name: String,
        val icon: String? = null,
        val status: String,
        val tags: List<TagJson>,
    ) {
        fun toDomainOrNull(): Category? {
            val categoryType = CategoryType.entries.firstOrNull { it.name == type } ?: return null
            val categoryStatus = CategoryStatus.entries.firstOrNull { it.name == status } ?: return null
            val domainTags = tags.map { it.toDomainOrNull() ?: return null }
            return Category(id, categoryType, name, icon, categoryStatus, domainTags)
        }

        companion object {
            fun from(category: Category) = CategoryJson(
                category.id, category.type.name, category.name, category.icon, category.status.name,
                category.tags.map(TagJson::from),
            )
        }
    }

    @Serializable
    private data class TagJson(val id: String, val name: String, val status: String, val displayOrder: Int) {
        fun toDomainOrNull(): Tag? {
            val tagStatus = TagStatus.entries.firstOrNull { it.name == status } ?: return null
            return Tag(id, name, tagStatus, displayOrder)
        }

        companion object {
            fun from(tag: Tag) = TagJson(tag.id, tag.name, tag.status.name, tag.displayOrder)
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val KEY_PREFIX = "categories_"
    }
}
