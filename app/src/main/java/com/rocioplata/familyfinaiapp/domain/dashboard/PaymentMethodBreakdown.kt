package com.rocioplata.familyfinaiapp.domain.dashboard

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus

/** Lo que un medio de pago suma en el mes mostrado, para su tarjeta de la cuadrícula (RF-16). */
data class PaymentMethodAmount(
    /** El medio de pago; nulo en la tarjeta «Sin medio de pago» (movimientos sin medio, RF-18). */
    val method: PaymentMethod?,
    /** Suma de los movimientos del mes del tipo activo con este medio; cero si no tiene ninguno (RF-17). */
    val amount: Money,
) {
    val isUnassigned: Boolean get() = method == null
    val isArchived: Boolean get() = method?.status == PaymentMethodStatus.DEPRECATED
    val hasAmount: Boolean get() = amount.amountMinor > 0
}

/** Reparto del mes por medio de pago para el tipo activo (RF-16 a RF-18): lo que muestra la cuadrícula. */
data class PaymentMethodBreakdown(
    /** Total del tipo activo en el mes, con todos sus movimientos, también los de medios que no están en la lista. */
    val total: Money,
    /** Tarjetas de la cuadrícula, ya en su orden (RF-17, RF-18). */
    val entries: List<PaymentMethodAmount>,
) {
    /** Cuántas tarjetas se muestran (el contador de la sección, RF-16). */
    val count: Int get() = entries.size
}
