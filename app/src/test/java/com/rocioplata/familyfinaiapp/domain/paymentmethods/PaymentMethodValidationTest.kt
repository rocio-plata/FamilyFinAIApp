package com.rocioplata.familyfinaiapp.domain.paymentmethods

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentMethodValidationTest {
    private fun valid(name: String) = PaymentMethodNameValidation.Valid(name)
    private fun invalid(error: PaymentMethodNameError) = PaymentMethodNameValidation.Invalid(error)

    @Test
    fun nombreValido_sinError() {
        assertEquals(valid("Tarjeta Santander"), PaymentMethodValidation.validateName("Tarjeta Santander"))
    }

    @Test
    fun nombreVacioOSoloEspacios_esObligatorio() {
        assertEquals(invalid(PaymentMethodNameError.REQUIRED), PaymentMethodValidation.validateName(""))
        assertEquals(invalid(PaymentMethodNameError.REQUIRED), PaymentMethodValidation.validateName("   "))
        assertEquals(invalid(PaymentMethodNameError.REQUIRED), PaymentMethodValidation.validateName(" \t\n "))
    }

    @Test
    fun nombre_seRecortaAntesDeValidarYDevolverse() {
        assertEquals(valid("Efectivo"), PaymentMethodValidation.validateName("  Efectivo  "))
        assertEquals(valid("Cuenta corriente"), PaymentMethodValidation.validateName(" Cuenta corriente"))
    }

    @Test
    fun nombre_limiteDe40() {
        assertEquals(40, PaymentMethodValidation.MAX_NAME_LENGTH)
        assertEquals(valid("a".repeat(40)), PaymentMethodValidation.validateName("a".repeat(40)))
        assertEquals(
            invalid(PaymentMethodNameError.TOO_LONG),
            PaymentMethodValidation.validateName("a".repeat(41)),
        )
    }

    @Test
    fun nombre_losEspaciosExtremosNoCuentanParaElLimite() {
        assertEquals(valid("a".repeat(40)), PaymentMethodValidation.validateName("  " + "a".repeat(40) + "  "))
    }

    @Test
    fun nombre_losEspaciosInternosSeConservan() {
        assertEquals(valid("Tarjeta  de   débito"), PaymentMethodValidation.validateName("Tarjeta  de   débito"))
    }
}
