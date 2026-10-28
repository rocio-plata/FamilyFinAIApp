package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val OWNER_SELECTOR = "Selector de familia: Familia Plata, Propietario"

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setHost(state: MainUiState, onRetry: () -> Unit = {}) {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { TestMainHost(initial = state, onRetry = onRetry) }
        }
    }

    private val severalFamilies = MainUiState(
        families = contentState(),
        pendingInvitations = 3,
        displayName = "rocío",
    )

    @Test
    fun homeShowsHeaderFabAndAllTabs() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Notificaciones, 3 invitaciones pendientes")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Perfil de usuario, inicial R").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Agregar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inicio").assertIsSelected()
        listOf("Inicio", "Movimientos", "Reportes", "Presupuestos", "Asistente").forEach {
            composeTestRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeTestRule.onNodeWithText("Inicio · Familia Plata").assertIsDisplayed()
    }

    @Test
    fun selectorListsFamiliesAndSelectingOneUpdatesHeaderAndHome() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).performClick()
        composeTestRule.onNodeWithText("Elegir familia").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Familia Plata, Propietario, seleccionada")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Familia Gómez, Integrante").performClick()

        composeTestRule.onAllNodesWithText("Elegir familia").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Selector de familia: Familia Gómez, Integrante")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Inicio · Familia Gómez").assertIsDisplayed()
    }

    @Test
    fun backClosesSelectorWithoutChangingFamily() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).performClick()
        composeTestRule.onNodeWithText("Elegir familia").assertIsDisplayed()
        pressBack()

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Elegir familia").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).assertIsDisplayed()
        composeTestRule.onNodeWithText("Inicio · Familia Plata").assertIsDisplayed()
    }

    @Test
    fun singleFamilyHasNoSelector() {
        setHost(MainUiState(families = contentState(listOf(OwnerFamily)), displayName = "rocío"))

        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).performClick()

        composeTestRule.onAllNodesWithText("Elegir familia").assertCountEquals(0)
        composeTestRule.onNodeWithText("Inicio · Familia Plata").assertIsDisplayed()
    }

    @Test
    fun noInvitationsShowsNoCounter() {
        setHost(MainUiState(families = contentState(), pendingInvitations = 0))

        composeTestRule.onNodeWithContentDescription("Notificaciones").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("0").assertCountEquals(0)
    }

    @Test
    fun singleInvitationUsesSingularDescription() {
        setHost(MainUiState(families = contentState(), pendingInvitations = 1))

        composeTestRule.onNodeWithContentDescription("Notificaciones, 1 invitación pendiente")
            .assertIsDisplayed()
    }

    @Test
    fun localChangesIconOnlyWhenFlagged() {
        setHost(MainUiState(families = contentState()))
        composeTestRule.onAllNodesWithContentDescription("Cambios solo en este dispositivo")
            .assertCountEquals(0)
    }

    @Test
    fun localChangesIconShownWhenFlagged() {
        setHost(MainUiState(families = contentState(), hasLocalChanges = true))
        composeTestRule.onNodeWithContentDescription("Cambios solo en este dispositivo")
            .assertIsDisplayed()
    }

    @Test
    fun errorStateOffersRetry() {
        var retries = 0
        setHost(MainUiState(families = FamiliesState.Error), onRetry = { retries++ })

        composeTestRule.onNodeWithContentDescription(
            "No se pudieron cargar las familias. Toca para reintentar",
        ).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun staleBannerOnlyWhenStale() {
        setHost(MainUiState(families = contentState(isStale = true)))
        composeTestRule.onNodeWithText(
            "Los datos podrían estar desactualizados. Conéctate para actualizarlos.",
        ).assertIsDisplayed()
    }

    @Test
    fun everyTabShowsTitleWithActiveFamilyAndNoFab() {
        setHost(severalFamilies)

        listOf(
            MainDestination.Movements,
            MainDestination.Reports,
            MainDestination.Budgets,
            MainDestination.Assistant,
        ).forEach { destination ->
            composeTestRule.onNodeWithText(context.getString(destination.labelRes)).performClick()
            composeTestRule.onNodeWithTag("tab_placeholder_${destination.name}").assertIsDisplayed()
            composeTestRule.onNodeWithText("${context.getString(destination.labelRes)} · Familia Plata")
                .assertIsDisplayed()
            composeTestRule.onAllNodesWithContentDescription("Agregar").assertCountEquals(0)
        }
    }

    @Test
    fun changingFamilyUpdatesActiveTab() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithText("Reportes").performClick()
        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).performClick()
        composeTestRule.onNodeWithContentDescription("Familia Gómez, Integrante").performClick()

        composeTestRule.onNodeWithText("Reportes · Familia Gómez").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reportes").assertIsSelected()
    }
}
