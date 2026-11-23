package com.rocioplata.familyfinaiapp.domain.header

import kotlinx.coroutines.flow.Flow

interface HeaderRepository {
    /** Datos vigentes del usuario actual: caché válida y, tras cada refresco correcto, los del backend. */
    val header: Flow<HeaderData>

    /** Consulta los tres recursos de forma independiente; nunca lanza por fallos de red o de servidor. */
    suspend fun refresh(): HeaderRefreshResult

    /** Borra los datos guardados y en memoria al terminar la sesión (RF-09 de 003-auth). */
    suspend fun clear()

    // Cambios ya confirmados por el backend (RF-05, RF-19, RF-20 de la 009): se aplican a memoria y caché sin esperar
    // a un refresco y conservando el `savedAt` de la última descarga. Los de familias no hacen nada si todavía no hay
    // lista cargada (la descarga siguiente traerá el cambio) ni sin sesión.

    /** Añade [family] al final de la lista o, si ya está, la sustituye. */
    suspend fun addFamily(family: Family)

    suspend fun removeFamily(familyId: String)

    /** Fija el orden; las familias que no estén en [orderedIds] quedan al final y los identificadores desconocidos se ignoran. */
    suspend fun setFamilyOrder(orderedIds: List<String>)

    /** Cambia rol, moneda o número de miembros de una familia; sin ella en la lista no hace nada. */
    suspend fun updateFamily(familyId: String, change: (Family) -> Family)

    /** Fija el número de invitaciones pendientes recibidas, que acaba de conocerse. */
    suspend fun setPendingInvitations(count: Int)
}
