package com.rocioplata.familyfinaiapp.domain.categories

import java.time.Instant

enum class CategoryType { EXPENSE, INCOME }

/** `DEPRECATED` es el archivado del backend; se deshace reactivando (BG-01 de la 005, RF-01 de la 010). */
enum class CategoryStatus { ACTIVE, DEPRECATED }

enum class TagStatus { ACTIVE, DEPRECATED }

data class Tag(
    val id: String,
    val name: String,
    val status: TagStatus,
    val displayOrder: Int,
)

data class Category(
    val id: String,
    val type: CategoryType,
    val name: String,
    /** Clave estable del catálogo de iconos; nula o desconocida se muestra con el icono genérico (RF-03). */
    val icon: String?,
    val status: CategoryStatus,
    /** Todos los tags de la categoría, también los archivados, tal como los devuelve el backend. */
    val tags: List<Tag>,
    /** Clave de la paleta de colores de la app; nula o desconocida usa el color de respaldo (RF-14 de la 008). */
    val color: String? = null,
    /**
     * Posición dentro de su tipo (BG-04 de la 005). Las anteriores a la migración del backend comparten 0: se muestran
     * en el orden que las devolvió (ver [inDisplayOrder]).
     */
    val displayOrder: Int = 0,
    /** Cuándo se archivó; nula si está activa o si se archivó antes de que el backend la guardara (RF-09 de la 010). */
    val deprecatedAt: Instant? = null,
)

/**
 * Orden en que se muestran las categorías en toda la app (RF-10 y RF-11 de la 010): por `displayOrder`, de forma estable,
 * así que las que comparten posición (anteriores a la migración del backend) conservan el orden recibido. Las archivadas no
 * se llevan al final aquí: cada pantalla las separa, y así archivar no cambia la posición (ni el color de respaldo) de las
 * demás.
 */
fun List<Category>.inDisplayOrder(): List<Category> = sortedBy { it.displayOrder }

/** Categorías de una familia; `categories` es nulo mientras no haya valor válido (ni caché ni backend). */
data class CategoriesData(
    val categories: List<Category>? = null,
    /** La última descarga completa tiene más de 24 h (RF-20). */
    val isStale: Boolean = false,
)
