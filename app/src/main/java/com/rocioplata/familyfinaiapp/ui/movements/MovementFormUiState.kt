package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.movements.AmountError
import com.rocioplata.familyfinaiapp.domain.movements.DateError
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.NoteError
import com.rocioplata.familyfinaiapp.domain.movements.TitleError
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import java.time.LocalDate

/** Avisos junto a cada campo; vacío mientras el usuario no intente guardar (CA-06). */
data class MovementFormErrors(
    val amount: AmountError? = null,
    val title: TitleError? = null,
    val category: Boolean = false,
    val paymentMethod: Boolean = false,
    val note: NoteError? = null,
    val date: DateError? = null,
) {
    val isEmpty: Boolean get() = this == MovementFormErrors()
}

/** Cómo van las opciones del formulario (categorías y medios de pago de la familia). */
enum class FormOptionsState {
    /** Aún no hay categorías o medios de pago (ni caché ni backend). */
    Loading,

    /** No se pudieron obtener y no hay copia: el formulario ofrece reintentar. */
    Error,
    Ready,
}

/**
 * Estado del formulario de un movimiento (RF-05 a RF-07, RF-16). Vive en el ViewModel y sobrevive a la
 * recreación. [categories], [tags] y [paymentMethods] ya vienen filtrados: solo activos, y las categorías
 * solo del tipo elegido (RF-06, RF-07).
 */
data class MovementFormUiState(
    val type: MovementType,
    /** Texto del monto tal como lo escribe el usuario, ya filtrado según la moneda (RF-05). */
    val amountText: String,
    /** Moneda de la familia; nula si no se conoce o no es válida: no se puede guardar (CA-25). */
    val currency: String?,
    val title: String,
    val categories: List<Category>,
    val categoryId: String?,
    val tags: List<Tag>,
    val tagId: String?,
    val paymentMethods: List<PaymentMethod>,
    val paymentMethodId: String?,
    val date: LocalDate,
    /** Hoy en la zona del dispositivo; los accesos rápidos y el selector no pasan de aquí (RF-16). */
    val today: LocalDate,
    val note: String,
    val errors: MovementFormErrors,
    val options: FormOptionsState,
    /** Sin categorías activas del tipo elegido: se explica que hay que crearlas (CA-23). */
    val noCategories: Boolean,
    /** Sin medios de pago activos: se explica que hay que crearlos (CA-23). */
    val noPaymentMethods: Boolean,
    val isSaving: Boolean,
    /** Se pidió cerrar con cambios sin guardar: se muestra la confirmación de descarte (CA-19). */
    val confirmingDiscard: Boolean,
    /** Último fallo al guardar, para avisar; [MovementFormViewModel.consumeNotice] lo quita. */
    val notice: MovementFailure?,
) {
    val currencyMissing: Boolean get() = currency == null
}
