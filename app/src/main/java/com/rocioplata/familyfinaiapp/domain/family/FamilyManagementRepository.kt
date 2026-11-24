package com.rocioplata.familyfinaiapp.domain.family

import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole

/**
 * Operaciones sobre las familias del usuario y sobre la activa (RF-02, RF-05, RF-09 a RF-17). Los cambios requieren
 * conexión: sin ella devuelven `Offline` sin llamar al backend. Con éxito se aplican a la cabecera y a la lista de
 * miembros sin esperar a un refresco; un fallo no cambia nada. No guardan datos propios (RF-25).
 */
interface FamilyManagementRepository {
    /** Crea una familia en la que el usuario es Propietario; queda al final de la lista. */
    suspend fun createFamily(name: String): FamilyAccessResult<Family>

    /** Guarda el orden de todas las familias del usuario; la primera es la favorita. */
    suspend fun reorderFamilies(orderedFamilyIds: List<String>): FamilyAccessResult<Unit>

    suspend fun settings(familyId: String): FamilyAccessResult<FamilySettings>

    suspend fun changeCurrency(familyId: String, currency: String): FamilyAccessResult<Unit>

    suspend fun changeMemberRole(familyId: String, memberId: String, newRole: FamilyRole): FamilyAccessResult<Unit>

    suspend fun removeMember(familyId: String, memberId: String): FamilyAccessResult<Unit>

    /** El propio usuario sale de la familia (RF-12); desaparece de la cabecera. */
    suspend fun leaveFamily(familyId: String): FamilyAccessResult<Unit>

    suspend fun sentInvitations(familyId: String): FamilyAccessResult<List<SentInvitation>>

    suspend fun invite(familyId: String, email: String, role: FamilyRole): FamilyAccessResult<SentInvitation>

    /** Revoca una invitación vigente o quita una vencida (RF-15, RF-16). */
    suspend fun revokeInvitation(invitationId: String): FamilyAccessResult<Unit>
}
