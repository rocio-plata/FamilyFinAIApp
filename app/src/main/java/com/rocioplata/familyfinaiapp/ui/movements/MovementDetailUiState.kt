package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import java.time.LocalDate

/**
 * Un elemento del catálogo resuelto por su id (categoría, etiqueta o medio de pago). [name] es nulo si ya no
 * se encuentra; [isArchived] indica que sigue existiendo pero archivado (CA-10).
 */
data class ResolvedItem(val name: String?, val isArchived: Boolean)

/** Estado del detalle de un movimiento (RF-08, RF-11, RF-15). */
sealed interface MovementDetailUiState {
    /** Aún no se ha leído el movimiento, o se acaba de eliminar y la pantalla se está cerrando. */
    data object Loading : MovementDetailUiState

    /**
     * El movimiento ya no existe (lo eliminaron desde otro dispositivo o el backend respondió `NotFound`): se
     * informa y se vuelve al listado, que ya se está refrescando (CA-22).
     */
    data object Gone : MovementDetailUiState

    data class Content(
        val id: String,
        val type: MovementType,
        val amount: Money,
        val title: String,
        val note: String?,
        val date: LocalDate,
        val category: ResolvedItem,
        /** Clave del icono de la categoría; nula si no se encontró o no tiene. */
        val categoryIcon: String?,
        val tag: ResolvedItem?,
        val paymentMethod: ResolvedItem?,
        /** Con su rol cuando se conoce (RF-15); nunca el correo. */
        val recordedBy: MemberLabel,
        /** Se pidió eliminar: se muestra la confirmación (RF-11). */
        val confirmingDelete: Boolean,
        val isDeleting: Boolean,
        /** Último fallo al eliminar, para avisar; [MovementDetailViewModel.consumeNotice] lo quita. */
        val notice: MovementFailure?,
    ) : MovementDetailUiState
}
