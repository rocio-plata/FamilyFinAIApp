package com.rocioplata.familyfinaiapp.domain.header

data class Family(
    val id: String,
    val name: String,
    val role: FamilyRole,
    /**
     * Moneda de la familia (código ISO 4217, por ejemplo `CLP`), en la que se registran todos sus movimientos.
     * Nula si el backend no la envió o la copia guardada es anterior a este campo.
     */
    val currency: String? = null,
)
