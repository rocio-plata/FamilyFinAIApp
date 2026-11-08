package com.rocioplata.familyfinaiapp.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test
    fun emailValido_sinError() {
        assertNull(AuthValidation.validateEmail("rocio@example.com"))
    }

    @Test
    fun emailConEspaciosAlrededor_seValidaRecortado() {
        assertNull(AuthValidation.validateEmail("  rocio@example.com  "))
    }

    @Test
    fun emailVacioOEnBlanco_esObligatorio() {
        assertEquals(FieldError.REQUIRED, AuthValidation.validateEmail(""))
        assertEquals(FieldError.REQUIRED, AuthValidation.validateEmail("   "))
    }

    @Test
    fun emailConFormatoInvalido_devuelveInvalido() {
        listOf("rocio", "rocio@", "rocio@example", "@example.com", "ro cio@example.com", "rocio@@example.com")
            .forEach { assertEquals(it, FieldError.INVALID_EMAIL, AuthValidation.validateEmail(it)) }
    }

    @Test
    fun passwordDeLogin_soloExigeQueNoEsteVacia() {
        assertEquals(FieldError.REQUIRED, AuthValidation.validateLoginPassword(""))
        assertNull(AuthValidation.validateLoginPassword("a"))
        assertNull(AuthValidation.validateLoginPassword("   "))
    }

    @Test
    fun passwordNueva_limiteDe8Caracteres() {
        assertEquals(FieldError.PASSWORD_TOO_SHORT, AuthValidation.validateNewPassword("1234567"))
        assertNull(AuthValidation.validateNewPassword("12345678"))
    }

    @Test
    fun passwordNuevaVacia_esObligatoria() {
        assertEquals(FieldError.REQUIRED, AuthValidation.validateNewPassword(""))
    }

    @Test
    fun passwordNueva_noSeRecortan_losEspaciosCuentan() {
        assertNull(AuthValidation.validateNewPassword(" abcdef "))
        assertEquals(FieldError.PASSWORD_TOO_SHORT, AuthValidation.validateNewPassword(" abcde "))
    }

    @Test
    fun confirmacion_debeCoincidirExactamente() {
        assertNull(AuthValidation.validatePasswordConfirmation("secreto123", "secreto123"))
        assertEquals(
            FieldError.PASSWORDS_DO_NOT_MATCH,
            AuthValidation.validatePasswordConfirmation("secreto123", "secreto124"),
        )
        assertEquals(
            FieldError.PASSWORDS_DO_NOT_MATCH,
            AuthValidation.validatePasswordConfirmation("secreto123", "secreto123 "),
        )
    }

    @Test
    fun confirmacionVacia_esObligatoria() {
        assertEquals(FieldError.REQUIRED, AuthValidation.validatePasswordConfirmation("secreto123", ""))
    }

    @Test
    fun nombre_limitesDe1y60TrasRecortar() {
        assertNull(AuthValidation.validateDisplayName("R"))
        assertNull(AuthValidation.validateDisplayName("a".repeat(60)))
        assertNull(AuthValidation.validateDisplayName("  " + "a".repeat(60) + "  "))
        assertEquals(FieldError.NAME_TOO_LONG, AuthValidation.validateDisplayName("a".repeat(61)))
    }

    @Test
    fun nombreVacioOEnBlanco_esObligatorio() {
        assertEquals(FieldError.REQUIRED, AuthValidation.validateDisplayName(""))
        assertEquals(FieldError.REQUIRED, AuthValidation.validateDisplayName("   "))
    }
}
