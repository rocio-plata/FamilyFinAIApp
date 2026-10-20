package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeShowsHeaderFabAndAllTabs() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { MainScreenContent(MainDestination.Home, {}) }
        }

        composeTestRule.onNodeWithContentDescription("Selector de familia: Familia Plata, Propietario")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Notificaciones").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Perfil de usuario").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Agregar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inicio").assertIsSelected()
        listOf("Inicio", "Movimientos", "Reportes", "Presupuestos", "Asistente").forEach {
            composeTestRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun headerAndFabAreInert() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { MainScreenContent(MainDestination.Home, {}) }
        }

        composeTestRule.onNodeWithContentDescription("Agregar").performClick()
        composeTestRule.onNodeWithContentDescription("Selector de familia: Familia Plata, Propietario")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Notificaciones").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Perfil de usuario").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Agregar").assertIsDisplayed()
    }

    @Test
    fun selectingEachNonHomeTabShowsItsPlaceholderWithoutFab() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { MainScreen() }
        }

        listOf(
            MainDestination.Movements,
            MainDestination.Reports,
            MainDestination.Budgets,
            MainDestination.Assistant,
        ).forEach { destination ->
            composeTestRule.onNodeWithText(
                context.getString(destination.labelRes),
            ).performClick()
            composeTestRule.onNodeWithTag("tab_placeholder_${destination.name}")
                .assertIsDisplayed()
            composeTestRule.onAllNodesWithText(
                context.getString(destination.labelRes),
            ).assertCountEquals(2)
            composeTestRule.onAllNodesWithContentDescription("Agregar").assertCountEquals(0)
            composeTestRule.onNodeWithContentDescription(
                "Selector de familia: Familia Plata, Propietario",
            ).assertIsDisplayed()
        }
    }
}
