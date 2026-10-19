package com.rocioplata.familyfinaiapp.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenBehaviorTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backFromNonHomeTabReturnsToHome() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { MainScreen() }
        }

        composeTestRule.onNodeWithText("Movimientos").performClick()
        composeTestRule.onNodeWithTag("tab_placeholder_Movements").assertIsDisplayed()

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

        composeTestRule.onNodeWithText("Inicio").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Agregar").assertIsDisplayed()
    }

    @Test
    fun interactiveElementsHaveAccessibleLabels() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { MainScreen() }
        }

        listOf(
            "Selector de familia: Familia Plata, Propietario",
            "Notificaciones",
            "Perfil de usuario",
            "Agregar",
        ).forEach { description ->
            composeTestRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        listOf("Inicio", "Movimientos", "Reportes", "Presupuestos", "Asistente")
            .forEach { label ->
                composeTestRule.onNodeWithText(label).assertIsDisplayed()
            }
    }

    @Test
    fun repeatedTabAndFabClicksDoNotDuplicateEffects() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { MainScreen() }
        }

        repeat(5) {
            composeTestRule.onNodeWithText("Inicio").performClick()
            composeTestRule.onNodeWithContentDescription("Agregar").performClick()
        }

        composeTestRule.onNodeWithText("Inicio").assertIsSelected()
        composeTestRule.onAllNodesWithContentDescription("Agregar").assertCountEquals(1)
        composeTestRule.onNodeWithContentDescription(
            "Selector de familia: Familia Plata, Propietario",
        ).assertIsDisplayed()
    }
}
