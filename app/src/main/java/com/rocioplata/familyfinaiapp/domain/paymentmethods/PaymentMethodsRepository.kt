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

    /**
     * Olvida lo guardado y en memoria de las familias que no estén en [familyIds] (RF-23 de la 009) y no guarda nada
     * más de ellas hasta que vuelvan a estarlo; las demás no se tocan. No espera a la red.
     */
    suspend fun retainFamilies(familyIds: Set<String>)
}
