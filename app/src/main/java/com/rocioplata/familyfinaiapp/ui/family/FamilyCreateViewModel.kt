package com.rocioplata.familyfinaiapp.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilyManagementRepository
import com.rocioplata.familyfinaiapp.domain.family.FamilyNameError
import com.rocioplata.familyfinaiapp.domain.family.FamilyNameRules
import com.rocioplata.familyfinaiapp.domain.family.FamilyNameValidation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del formulario de «Crear nueva familia» (RF-04, RF-05, RF-25). Se crea con una clave por apertura de la ruta,
 * así que cada «Crear nueva familia» empieza con un formulario vacío.
 *
 * [onCreated] recibe la familia creada (o la que ya existía tras un fallo incierto); [onClose] cierra la pantalla.
 */
class FamilyCreateViewModel(
    private val management: FamilyManagementRepository,
    private val header: HeaderRepository,
    private val onCreated: (Family) -> Unit,
    private val onClose: () -> Unit,
) : ViewModel() {
    private val state = MutableStateFlow(FamilyCreateUiState())
    val uiState: StateFlow<FamilyCreateUiState> = state.asStateFlow()

    // Un intento terminó en un fallo de red: pudo llegar al backend. Antes de enviar otro se comprueba si la familia ya
    // está, para no crearla dos veces (nombre y familias que había antes del intento).
    private var uncertain: Uncertain? = null

    fun onNameChange(text: String) {
        state.update { current ->
            if (current.isSaving || current.confirmingDiscard) {
                current
            } else {
                current.copy(text = text.take(FamilyNameRules.MAX_NAME_LENGTH), error = null)
            }
        }
    }

    /**
     * Valida y crea la familia. Una sola operación a la vez: `isSaving` se fija antes de lanzar la corrutina (CA-30).
     * Sin nombre válido no se llama al backend.
     */
    fun save() {
        val current = state.value
        if (current.isSaving || current.confirmingDiscard) return
        state.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val outcome = try {
                create(current.text)
            } finally {
                state.update { it.copy(isSaving = false) }
            }
            when (outcome) {
                is Outcome.Created -> onCreated(outcome.family)
                is Outcome.Error -> state.update { it.copy(error = outcome.error) }
            }
        }
    }

    /** Atrás, la flecha y el gesto de atrás: con el nombre escrito piden confirmación (CA-31); durante la creación no. */
    fun requestClose() {
        val current = state.value
        if (current.isSaving || current.confirmingDiscard) return
        if (current.hasChanges) state.value = current.copy(confirmingDiscard = true) else onClose()
    }

    fun confirmDiscard() {
        if (state.value.confirmingDiscard) onClose()
    }

    /** Seguir en el formulario con lo escrito. */
    fun dismissDiscard() {
        state.update { it.copy(confirmingDiscard = false) }
    }

    private suspend fun create(text: String): Outcome {
        // Tras un fallo de red incierto manda lo que haya en el backend, aunque el texto haya cambiado desde entonces.
        uncertain?.let { previous ->
            val existing = alreadyCreated(previous)
            if (existing is Checked.Found) {
                uncertain = null
                return Outcome.Created(existing.family)
            }
            if (existing is Checked.Failed) return Outcome.Error(FamilyCreateError.Failure(FamilyAccessFailure.Network))
            uncertain = null
        }
        val before = knownFamilies()
        val name = when (val validation = FamilyNameRules.validate(text, before.map { it.name })) {
            is FamilyNameValidation.Valid -> validation.name
            is FamilyNameValidation.Invalid -> return Outcome.Error(validation.error.toCreateError())
        }
        return when (val result = management.createFamily(name)) {
            is FamilyAccessResult.Success -> Outcome.Created(result.value)
            is FamilyAccessResult.Failure -> {
                // Con un fallo de red no se sabe si el backend llegó a crearla: se recuerdan las familias de antes del
                // intento (no las de ahora, que ya podrían incluirla).
                if (result.failure == FamilyAccessFailure.Network) uncertain = Uncertain(name, before.map { it.id }.toSet())
                Outcome.Error(FamilyCreateError.Failure(result.failure))
            }
        }
    }

    private suspend fun knownFamilies(): List<Family> = header.header.first().families?.value.orEmpty()

    private suspend fun alreadyCreated(previous: Uncertain): Checked {
        val refresh = header.refresh()
        if (refresh.families != null) return Checked.Failed
        val found = knownFamilies().firstOrNull { it.name == previous.name && it.id !in previous.idsBefore }
        return if (found != null) Checked.Found(found) else Checked.NotFound
    }

    private class Uncertain(val name: String, val idsBefore: Set<String>)

    private sealed interface Checked {
        data class Found(val family: Family) : Checked
        data object NotFound : Checked
        data object Failed : Checked
    }

    private sealed interface Outcome {
        data class Created(val family: Family) : Outcome
        data class Error(val error: FamilyCreateError) : Outcome
    }

    private fun FamilyNameError.toCreateError(): FamilyCreateError = when (this) {
        FamilyNameError.REQUIRED -> FamilyCreateError.Required
        FamilyNameError.TOO_LONG -> FamilyCreateError.TooLong
        FamilyNameError.EMOJI -> FamilyCreateError.Emoji
        FamilyNameError.DUPLICATE -> FamilyCreateError.Duplicate
    }
}
