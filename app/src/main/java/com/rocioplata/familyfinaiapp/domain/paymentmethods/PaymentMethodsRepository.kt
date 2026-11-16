package com.rocioplata.familyfinaiapp.domain.paymentmethods

import kotlinx.coroutines.flow.Flow

interface PaymentMethodsRepository {
    /** Datos vigentes de la familia: caché válida y, tras cada refresco o cambio correcto, los del backend. */
    fun paymentMethods(familyId: String): Flow<PaymentMethodsData>

    /** Descarga la lista completa (con archivados); nunca lanza por fallos de red o de servidor. */
    suspend fun refresh(familyId: String): PaymentMethodResult<Unit>

    suspend fun createPaymentMethod(familyId: String, name: String): PaymentMethodResult<PaymentMethod>

    suspend fun renamePaymentMethod(
        familyId: String,
        paymentMethodId: String,
        name: String,
    ): PaymentMethodResult<Unit>

    suspend fun deprecatePaymentMethod(familyId: String, paymentMethodId: String): PaymentMethodResult<Unit>

    suspend fun deletePaymentMethod(familyId: String, paymentMethodId: String): PaymentMethodResult<Unit>

    /** Borra los datos guardados y en memoria al terminar la sesión; no espera a la red. */
    suspend fun clear()
}
