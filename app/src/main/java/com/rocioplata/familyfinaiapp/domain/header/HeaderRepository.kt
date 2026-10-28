package com.rocioplata.familyfinaiapp.domain.header

import kotlinx.coroutines.flow.Flow

interface HeaderRepository {
    /** Datos vigentes del usuario actual: caché válida y, tras cada refresco correcto, los del backend. */
    val header: Flow<HeaderData>

    /** Consulta los tres recursos de forma independiente; nunca lanza por fallos de red o de servidor. */
    suspend fun refresh(): HeaderRefreshResult
}
