package com.rocioplata.familyfinaiapp.domain.categories

enum class CategoryType { EXPENSE, INCOME }

/** `DEPRECATED` es el archivado del backend: no tiene operación inversa (BG-01). */
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
)

/** Categorías de una familia; `categories` es nulo mientras no haya valor válido (ni caché ni backend). */
data class CategoriesData(
    val categories: List<Category>? = null,
    /** La última descarga completa tiene más de 24 h (RF-20). */
    val isStale: Boolean = false,
)
