package com.rocioplata.familyfinaiapp.ui.paymentmethods

import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentMethodTextsTest {
    @Test
    fun cadaFalloConMensajePropioTieneSuTexto() {
        assertEquals(R.string.payment_methods_error_offline, PaymentMethodFailure.Offline.messageRes())
        assertEquals(R.string.payment_methods_error_network, PaymentMethodFailure.Network.messageRes())
        assertEquals(R.string.payment_methods_error_insufficient_role, PaymentMethodFailure.InsufficientRole.messageRes())
        assertEquals(R.string.payment_methods_error_duplicate, PaymentMethodFailure.DuplicateName.messageRes())
        assertEquals(R.string.payment_methods_error_invalid_name, PaymentMethodFailure.InvalidName.messageRes())
        assertEquals(R.string.payment_methods_error_is_someones_default, PaymentMethodFailure.IsSomeonesDefault.messageRes())
        assertEquals(R.string.payment_methods_error_has_associated_items, PaymentMethodFailure.HasAssociatedItems.messageRes())
        assertEquals(R.string.payment_methods_error_not_found, PaymentMethodFailure.NotFound.messageRes())
        assertEquals(R.string.payment_methods_error_invalid_data, PaymentMethodFailure.InvalidData.messageRes())
    }

    @Test
    fun losFallosSinMensajePropioUsanElGenerico() {
        listOf(PaymentMethodFailure.Unauthorized, PaymentMethodFailure.Unknown).forEach {
            assertEquals(it.toString(), R.string.payment_methods_error_generic, it.messageRes())
        }
    }

    @Test
    fun losErroresDelCampoTienenSuTexto() {
        assertEquals(R.string.payment_methods_error_name_required, PaymentMethodFormError.Required.messageRes())
        assertEquals(R.string.payment_methods_error_name_too_long, PaymentMethodFormError.TooLong.messageRes())
        assertEquals(R.string.payment_methods_error_duplicate, PaymentMethodFormError.Duplicate.messageRes())
        assertEquals(
            R.string.payment_methods_error_offline,
            PaymentMethodFormError.Failure(PaymentMethodFailure.Offline).messageRes(),
        )
    }
}
