package com.rocioplata.familyfinaiapp.domain.categories

import kotlinx.coroutines.flow.Flow

/** Cambio del icono en una edición; `Remove` envía `null` explícito al backend. */
sealed interface IconUpdate {
    data object Unchanged : IconUpdate
    data object Remove : IconUpdate
    data class Set(val key: String) : IconUpdate
}

interface CategoriesRepository {
    /** Datos vigentes de la familia: caché válida y, tras cada refresco o cambio correcto, los del backend. */
    fun categories(familyId: String): Flow<CategoriesData>

    /** Descarga la lista completa (con archivadas); nunca lanza por fallos de red o de servidor. */
    suspend fun refresh(familyId: String): CategoryResult<Unit>

    suspend fun createCategory(
        familyId: String,
        type: CategoryType,
        name: String,
        icon: String?,
    ): CategoryResult<Category>

    /** Solo se envían los campos modificados: `newName` nulo y `Unchanged` los omiten. */
    suspend fun updateCategory(
        familyId: String,
        categoryId: String,
        newName: String?,
        icon: IconUpdate,
    ): CategoryResult<Unit>

    suspend fun deprecateCategory(familyId: String, categoryId: String): CategoryResult<Unit>

    suspend fun deleteCategory(familyId: String, categoryId: String): CategoryResult<Unit>

    suspend fun addTag(familyId: String, categoryId: String, name: String): CategoryResult<Unit>

    suspend fun renameTag(
        familyId: String,
        categoryId: String,
        tagId: String,
        name: String,
    ): CategoryResult<Unit>

    /**
     * Recibe el nuevo orden de los tags activos; el repositorio envía la lista completa
     * (activos y después archivados), que es la que exige el backend.
     */
    suspend fun reorderTags(
        familyId: String,
        categoryId: String,
        activeTagIdsInOrder: List<String>,
    ): CategoryResult<Unit>

    suspend fun deprecateTag(familyId: String, categoryId: String, tagId: String): CategoryResult<Unit>

    suspend fun deleteTag(familyId: String, categoryId: String, tagId: String): CategoryResult<Unit>

    /** Borra los datos guardados y en memoria al terminar la sesión; no espera a la red. */
    suspend fun clear()
}
