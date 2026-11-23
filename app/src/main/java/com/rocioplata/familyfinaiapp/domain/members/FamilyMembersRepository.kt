package com.rocioplata.familyfinaiapp.domain.members

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

    /** Borra los datos guardados y en memoria al terminar la sesión; no espera a la red. */
    suspend fun clear()

    /**
     * Olvida lo guardado y en memoria de las familias que no estén en [familyIds] (RF-23 de la 009) y no guarda nada
     * más de ellas hasta que vuelvan a estarlo; las demás no se tocan. No espera a la red.
     */
    suspend fun retainFamilies(familyIds: Set<String>)
}
