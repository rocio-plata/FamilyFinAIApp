package com.rocioplata.familyfinaiapp.domain.paymentmethods

/** `DEPRECATED` es el archivado del backend: no tiene operación inversa (BG-02). */
enum class PaymentMethodStatus { ACTIVE, DEPRECATED }

data class PaymentMethod(
    val id: String,
    val name: String,
    val status: PaymentMethodStatus,
)

/**
 * Medios de pago de una familia, en el orden que devuelve el backend; `paymentMethods` es nulo
 * mientras no haya valor válido (ni caché ni backend).
 */
data class PaymentMethodsData(
    val paymentMethods: List<PaymentMethod>? = null,
    /** La última descarga completa tiene más de 24 h (RF-11). */
    val isStale: Boolean = false,
)
