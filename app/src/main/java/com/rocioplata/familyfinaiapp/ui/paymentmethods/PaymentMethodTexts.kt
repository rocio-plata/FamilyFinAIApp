package com.rocioplata.familyfinaiapp.ui.paymentmethods

import androidx.annotation.StringRes
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure

/**
 * Texto de un fallo de medio de pago (RF-09, RF-10, RF-12). `Unauthorized` lo resuelve la sesión (003) y
 * cae en el mensaje genérico. `HasAssociatedItems` es el aviso de un medio archivado; en un activo la
 * interfaz ofrece archivarlo en su lugar (RF-09).
 */
@StringRes
fun PaymentMethodFailure.messageRes(): Int = when (this) {
    PaymentMethodFailure.Offline -> R.string.payment_methods_error_offline
    PaymentMethodFailure.Network -> R.string.payment_methods_error_network
    PaymentMethodFailure.InsufficientRole -> R.string.payment_methods_error_insufficient_role
    PaymentMethodFailure.DuplicateName -> R.string.payment_methods_error_duplicate
    PaymentMethodFailure.InvalidName -> R.string.payment_methods_error_invalid_name
    PaymentMethodFailure.IsSomeonesDefault -> R.string.payment_methods_error_is_someones_default
    PaymentMethodFailure.HasAssociatedItems -> R.string.payment_methods_error_has_associated_items
    PaymentMethodFailure.NotFound -> R.string.payment_methods_error_not_found
    PaymentMethodFailure.InvalidData -> R.string.payment_methods_error_invalid_data
    PaymentMethodFailure.Unauthorized,
    PaymentMethodFailure.Unknown,
    -> R.string.payment_methods_error_generic
}

/** Texto del error junto al campo del nombre (RF-13, CA-08, CA-09). */
@StringRes
fun PaymentMethodFormError.messageRes(): Int = when (this) {
    PaymentMethodFormError.Required -> R.string.payment_methods_error_name_required
    PaymentMethodFormError.TooLong -> R.string.payment_methods_error_name_too_long
    PaymentMethodFormError.Duplicate -> PaymentMethodFailure.DuplicateName.messageRes()
    is PaymentMethodFormError.Failure -> failure.messageRes()
}

/**
 * Texto del aviso de una acción del menú (archivar o eliminar). Se distingue el de sin conexión porque
 * no hay nada escrito que conservar: el del formulario ([messageRes]) sí lo menciona (RF-12).
 */
@StringRes
fun PaymentMethodsEvent.messageRes(): Int = when (this) {
    is PaymentMethodsEvent.Failed ->
        if (failure == PaymentMethodFailure.Offline) R.string.payment_methods_error_offline_action
        else failure.messageRes()
}
