package com.rocioplata.familyfinaiapp.domain.members

import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import kotlinx.coroutines.flow.Flow

sealed interface FamilyMembersFailure {
    data object Network : FamilyMembersFailure

    /** El backend respondió `403`: el usuario ya no es miembro de la familia. */
    data object NotAllowed : FamilyMembersFailure
    data object Unauthorized : FamilyMembersFailure
    data object InvalidData : FamilyMembersFailure
    data object Unknown : FamilyMembersFailure
}

sealed interface FamilyMembersResult<out T> {
    data class Success<out T>(val value: T) : FamilyMembersResult<T>
    data class Failure(val failure: FamilyMembersFailure) : FamilyMembersResult<Nothing>
}

interface FamilyMembersRepository {
    /** Datos vigentes de la familia: caché válida y, tras cada refresco correcto, los del backend. */
    fun members(familyId: String): Flow<FamilyMembersData>

    /** Descarga la lista de miembros; nunca lanza por fallos de red o de servidor. */
    suspend fun refresh(familyId: String): FamilyMembersResult<Unit>

    /**
     * Como [refresh] y devuelve además el correo de cada miembro por su `userId` (RF-07). El backend solo los envía a un
     * Propietario, así que el mapa puede venir vacío. Los correos no se guardan en memoria compartida ni en disco: quien
     * los pide los conserva solo mientras los muestra.
     */
    suspend fun refreshWithEmails(familyId: String): FamilyMembersResult<Map<String, String>>

    /**
     * Aplica un cambio de rol ya confirmado por el backend (RF-09) a memoria y caché, conservando el `savedAt` de la última
     * descarga. Sin lista cargada de la familia o sin sesión no hace nada.
     */
    suspend fun applyRole(familyId: String, userId: String, role: FamilyRole)

    /** Quita a un miembro ya eliminado en el backend (RF-10); mismas reglas que [applyRole]. */
    suspend fun applyRemoval(familyId: String, userId: String)

    /** Borra los datos guardados y en memoria al terminar la sesión; no espera a la red. */
    suspend fun clear()

    /**
     * Olvida lo guardado y en memoria de las familias que no estén en [familyIds] (RF-23 de la 009) y no guarda nada
     * más de ellas hasta que vuelvan a estarlo; las demás no se tocan. No espera a la red.
     */
    suspend fun retainFamilies(familyIds: Set<String>)
}
