package com.rocioplata.familyfinaiapp.domain.movements

import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface MovementsRepository {
    /**
     * Datos vigentes de un mes de la familia: caché válida y, tras cada refresco o cambio correcto, los del
     * backend. Siempre ordenados por fecha descendente y `createdAt` descendente (RF-02).
     */
    fun movements(familyId: String, month: YearMonth): Flow<MovementsData>

    /**
     * Un movimiento buscado en los meses cargados (el backend no tiene consulta individual, BG-06); nulo si ya
     * no está (eliminado o desaparecido de un refresco).
     */
    fun movement(familyId: String, movementId: String): Flow<Movement?>

    /** Descarga el mes completo; nunca lanza por fallos de red o de servidor. */
    suspend fun refresh(familyId: String, month: YearMonth): MovementResult<Unit>

    /** Registra el movimiento y recuerda su medio de pago como el último usado (RF-07). */
    suspend fun createMovement(familyId: String, movement: NewMovement): MovementResult<Movement>

    /**
     * Guarda solo lo modificado: primero los datos y después la categoría (RF-10). Una edición sin cambios no
     * llama al backend.
     */
    suspend fun editMovement(familyId: String, movementId: String, changes: MovementChanges): MovementEditResult

    suspend fun deleteMovement(familyId: String, movementId: String): MovementResult<Unit>

    /** Último medio de pago que el usuario actual usó en esta familia, si lo hay (RF-07); no comprueba que siga activo. */
    suspend fun lastUsedPaymentMethodId(familyId: String): String?

    /** Borra los datos guardados y en memoria al terminar la sesión; no espera a la red. */
    suspend fun clear()
}
