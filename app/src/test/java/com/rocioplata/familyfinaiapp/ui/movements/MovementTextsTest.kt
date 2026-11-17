package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MovementTextsTest {
    @Test
    fun cadaFalloConMensajePropioTieneSuTexto() {
        assertEquals(R.string.movements_error_offline, MovementFailure.Offline.messageRes())
        assertEquals(R.string.movements_error_network, MovementFailure.Network.messageRes())
        assertEquals(R.string.movements_error_future_date, MovementFailure.FutureDate.messageRes())
        assertEquals(R.string.movements_error_category_unavailable, MovementFailure.CategoryUnavailable.messageRes())
        assertEquals(R.string.movements_error_tag_unavailable, MovementFailure.TagUnavailable.messageRes())
        assertEquals(
            R.string.movements_error_payment_method_unavailable,
            MovementFailure.PaymentMethodUnavailable.messageRes(),
        )
        assertEquals(R.string.movements_error_type_mismatch, MovementFailure.TypeMismatch.messageRes())
        assertEquals(R.string.movements_error_currency_mismatch, MovementFailure.CurrencyMismatch.messageRes())
        assertEquals(R.string.movements_error_not_found, MovementFailure.NotFound.messageRes())
        assertEquals(R.string.movements_error_invalid_data, MovementFailure.InvalidData.messageRes())
        assertEquals(R.string.movements_error_not_allowed, MovementFailure.NotAllowed.messageRes())
    }

    @Test
    fun losFallosSinMensajePropioUsanElGenerico() {
        listOf(MovementFailure.Unauthorized, MovementFailure.Unknown).forEach {
            assertEquals(it.toString(), R.string.movements_error_generic, it.messageRes())
        }
    }

    @Test
    fun losFallosConMensajePropioNoSeConfundenConElGenerico() {
        listOf(
            MovementFailure.Offline, MovementFailure.Network, MovementFailure.FutureDate,
            MovementFailure.CategoryUnavailable, MovementFailure.TagUnavailable,
            MovementFailure.PaymentMethodUnavailable, MovementFailure.TypeMismatch,
            MovementFailure.CurrencyMismatch, MovementFailure.NotFound, MovementFailure.InvalidData,
            MovementFailure.NotAllowed,
        ).forEach { assertNotEquals(it.toString(), R.string.movements_error_generic, it.messageRes()) }
    }
}
