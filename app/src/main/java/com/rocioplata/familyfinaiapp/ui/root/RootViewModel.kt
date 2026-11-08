package com.rocioplata.familyfinaiapp.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pide la carga de la sesión guardada al arrancar y expone su estado a la raíz (RF-05). */
class RootViewModel(sessionManager: SessionManager) : ViewModel() {
    private val loadFailed = MutableStateFlow(false)

    val sessionState: StateFlow<SessionState> = combine(sessionManager.state, loadFailed) { state, failed ->
        // Si la lectura falló sin llegar a decidir, se trata como sin sesión: nunca se deja la pantalla vacía.
        if (failed && state == SessionState.Loading) SessionState.SignedOut() else state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.state.value)

    /**
     * Última generación de sesión iniciada. Identifica cada episodio sin sesión para que el acceso
     * tenga su propio ViewModel y aparezca vacío tras cerrar sesión (RF-09).
     */
    val lastGeneration: StateFlow<Long> = sessionManager.state
        .filterIsInstance<SessionState.SignedIn>()
        .map { it.generation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    init {
        viewModelScope.launch {
            try {
                sessionManager.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // `load()` ya deja `SignedOut` si falla al borrar los datos; si falló antes de decidir
                // (lectura imposible), `loadFailed` lo equipara a no tener sesión. En ambos casos el
                // acceso sigue disponible y la sesión guardada, si existe, se reintenta al próximo arranque.
                loadFailed.value = true
            }
        }
    }
}
