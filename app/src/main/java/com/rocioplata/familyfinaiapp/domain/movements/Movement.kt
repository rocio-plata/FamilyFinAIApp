package com.rocioplata.familyfinaiapp.domain.movements

import com.rocioplata.familyfinaiapp.domain.money.Money
import java.time.Instant
import java.time.LocalDate

/** El tipo lo decide la categoría del movimiento (RF-05): nunca se elige por separado en el backend. */
enum class MovementType { EXPENSE, INCOME }

/**
 * Un movimiento de la familia (RF-02, RF-08). Categoría, etiqueta y medio de pago se guardan por id: nombres e
 * iconos se resuelven con los repositorios de categorías y de medios de pago, que incluyen los archivados.
 */
data class Movement(
    val id: String,
    val type: MovementType,
    val amount: Money,
    val categoryId: String,
    val tagId: String?,
    /** Nulo si el backend no lo devuelve (BG-04 de la 007). */
    val paymentMethodId: String?,
    /** El "concepto" de la interfaz. */
    val title: String,
    /** Las "observaciones" de la interfaz. */
    val note: String?,
    /** Solo el día, en la zona del dispositivo (RF-16). */
    val date: LocalDate,
    /** Identificador del miembro que lo registró; el nombre se resuelve con [FamilyMembersRepository] (RF-15). */
    val recordedBy: String,
    /** Desempata el orden dentro de un mismo día: el registrado más recientemente primero (RF-02). */
    val createdAt: Instant,
)

/**
 * Movimientos de un mes de una familia, ya ordenados (fecha descendente y, dentro del día, `createdAt`
 * descendente); `movements` es nulo mientras no haya valor válido (ni caché ni backend).
 */
data class MovementsData(
    val movements: List<Movement>? = null,
    /** La última descarga completa del mes tiene más de 24 h (RF-13). */
    val isStale: Boolean = false,
)

/** Datos de un alta (RF-05). El tipo y la moneda del movimiento los fijan la categoría y la familia. */
data class NewMovement(
    val amount: Money,
    val categoryId: String,
    val tagId: String?,
    /** Obligatorio: la app nunca delega en el predeterminado del backend (RF-07). */
    val paymentMethodId: String,
    val title: String,
    val note: String?,
    val date: LocalDate,
)

/** Cambio de categoría y etiqueta de un movimiento; la etiqueta puede cambiar con la misma categoría (RF-10). */
data class CategoryChange(val categoryId: String, val tagId: String?)

/** Cambio de las observaciones en una edición; `Remove` envía `null` explícito al backend. */
sealed interface NoteUpdate {
    data object Unchanged : NoteUpdate
    data object Remove : NoteUpdate
    data class Set(val text: String) : NoteUpdate
}

/**
 * Solo lo que el usuario modificó en una edición (RF-09, RF-10): lo que es nulo o `Unchanged` no se envía.
 * El tipo no se puede cambiar, así que no aparece.
 */
data class MovementChanges(
    val amount: Money? = null,
    val title: String? = null,
    val note: NoteUpdate = NoteUpdate.Unchanged,
    /** Solo se informa si cambió el día, para no alterar el instante guardado. */
    val date: LocalDate? = null,
    val paymentMethodId: String? = null,
    val category: CategoryChange? = null,
) {
    /** Hay algo que guardar en el propio movimiento (monto, concepto, observaciones, fecha o medio de pago). */
    val hasDataChanges: Boolean
        get() = amount != null || title != null || note != NoteUpdate.Unchanged ||
            date != null || paymentMethodId != null

    val isEmpty: Boolean get() = !hasDataChanges && category == null
}
