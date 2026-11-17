package com.rocioplata.familyfinaiapp.ui.movements

import androidx.annotation.StringRes
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure

/**
 * Texto de un fallo de movimiento (RF-10, RF-14, CA-17, CA-22). `Unauthorized` lo resuelve la sesión (003) y
 * cae en el mensaje genérico, igual que `Unknown`. `Offline` sirve para registrar, editar y eliminar: lo
 * escrito se conserva en el formulario y eliminar no tiene nada que perder.
 */
@StringRes
fun MovementFailure.messageRes(): Int = when (this) {
    MovementFailure.Offline -> R.string.movements_error_offline
    MovementFailure.Network -> R.string.movements_error_network
    MovementFailure.FutureDate -> R.string.movements_error_future_date
    MovementFailure.CategoryUnavailable -> R.string.movements_error_category_unavailable
    MovementFailure.TagUnavailable -> R.string.movements_error_tag_unavailable
    MovementFailure.PaymentMethodUnavailable -> R.string.movements_error_payment_method_unavailable
    MovementFailure.TypeMismatch -> R.string.movements_error_type_mismatch
    MovementFailure.CurrencyMismatch -> R.string.movements_error_currency_mismatch
    MovementFailure.NotFound -> R.string.movements_error_not_found
    MovementFailure.InvalidData -> R.string.movements_error_invalid_data
    MovementFailure.NotAllowed -> R.string.movements_error_not_allowed
    MovementFailure.Unauthorized,
    MovementFailure.Unknown,
    -> R.string.movements_error_generic
}
