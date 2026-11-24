package com.rocioplata.familyfinaiapp.ui.family

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocioplata.familyfinaiapp.FamilyFinApp
import com.rocioplata.familyfinaiapp.domain.header.Family

/**
 * Entrada con estado de «Crear nueva familia» (RF-04, RF-05). [viewModelKey] debe incluir la sesión y la apertura de la
 * ruta (`main-<generación>-family-create-<instancia>`): así cada «Crear nueva familia» empieza con un formulario vacío y
 * el nombre escrito sobrevive a recrear la pantalla. [onCreated] recibe la familia creada; [onClose] cierra la pantalla.
 */
@Composable
fun FamilyCreateScreen(
    viewModelKey: String,
    onCreated: (Family) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: FamilyCreateViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                FamilyCreateViewModel(container.familyManagementRepository, container.headerRepository, onCreated, onClose)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    FamilyCreateContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onSave = viewModel::save,
        onClose = viewModel::requestClose,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscard,
        modifier = modifier,
    )
}
