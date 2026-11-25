package com.rocioplata.familyfinaiapp.domain.categories

import kotlinx.coroutines.flow.Flow

/** Cambio del icono en una edición; `Remove` envía `null` explícito al backend. */
sealed interface IconUpdate {
    data object Unchanged : IconUpdate
    data object Remove : IconUpdate
    data class Set(val key: String) : IconUpdate
}

/** Cambio del color en una edición; `Remove` envía `null` explícito al backend. */
sealed interface ColorUpdate {
    data object Unchanged : ColorUpdate
    data object Remove : ColorUpdate
    data class Set(val key: String) : ColorUpdate
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
        color: String? = null,
    ): CategoryResult<Category>

    /** Solo se envían los campos modificados: `newName` nulo y `Unchanged` los omiten. */
    suspend fun updateCategory(
        familyId: String,
        categoryId: String,
        newName: String?,
        icon: IconUpdate,
        color: ColorUpdate = ColorUpdate.Unchanged,
    ): CategoryResult<Unit>

    suspend fun deprecateCategory(familyId: String, categoryId: String): CategoryResult<Unit>

    suspend fun deleteCategory(familyId: String, categoryId: String): CategoryResult<Unit>

    /**
     * Reactiva una categoría archivada y la deja como la última activa de su tipo (RF-01, RF-11 de la 010). Si no se
     * puede guardar ese orden, la reactivación se mantiene y se devuelve `Partial`.
     */
    suspend fun reactivateCategory(familyId: String, categoryId: String): ReactivationResult

    /**
     * Guarda el orden de las categorías activas de [type] (RF-10 de la 010). Las demás de ese tipo (las archivadas)
     * quedan después en su orden previo, como hace el backend.
     */
    suspend fun reorderCategories(
        familyId: String,
        type: CategoryType,
        activeCategoryIdsInOrder: List<String>,
    ): CategoryResult<Unit>

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

    /** Reactiva un tag archivado y lo deja como el último activo de su categoría (RF-04, RF-11 de la 010). */
    suspend fun reactivateTag(familyId: String, categoryId: String, tagId: String): ReactivationResult

    /** Borra los datos guardados y en memoria al terminar la sesión; no espera a la red. */
    suspend fun clear()

    /**
     * Olvida lo guardado y en memoria de las familias que no estén en [familyIds] (RF-23 de la 009) y no guarda nada
     * más de ellas hasta que vuelvan a estarlo; las demás no se tocan. No espera a la red.
     */
    suspend fun retainFamilies(familyIds: Set<String>)
}
