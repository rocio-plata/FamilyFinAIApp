package com.rocioplata.familyfinaiapp.ui.root

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocioplata.familyfinaiapp.FamilyFinApp
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import com.rocioplata.familyfinaiapp.ui.auth.AuthScreen
import com.rocioplata.familyfinaiapp.ui.main.MainScreen

@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: RootViewModel = viewModel(
        factory = viewModelFactory { initializer { RootViewModel(container.sessionManager) } },
    )
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val lastGeneration by viewModel.lastGeneration.collectAsStateWithLifecycle()

    AppRootContent(
        state = state,
        signedOut = { reason -> AuthScreen(endReason = reason, viewModelKey = "auth-$lastGeneration") },
        // El ViewModel de la pantalla principal se asocia a la generación para no reutilizar
        // el estado de una sesión anterior (RF-09).
        signedIn = { MainScreen(modifier = Modifier.fillMaxSize(), viewModelKey = "main-${it.generation}") },
        modifier = modifier,
    )
}

/**
 * Elige qué mostrar según la sesión. Sin pila de navegación: Atrás en la pantalla principal sale de
 * la app y nunca vuelve al acceso (CA-19). Mientras se lee la sesión solo se ve el fondo del tema,
 * para que el acceso no aparezca ni un instante (CA-01).
 */
@Composable
fun AppRootContent(
    state: SessionState,
    signedOut: @Composable (reason: SessionEndReason?) -> Unit,
    signedIn: @Composable (SessionState.SignedIn) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        when (state) {
            SessionState.Loading -> Unit
            is SessionState.SignedOut -> signedOut(state.reason)
            is SessionState.SignedIn -> key(state.generation) { signedIn(state) }
        }
    }
}
