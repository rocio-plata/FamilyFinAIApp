package com.rocioplata.familyfinaiapp.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.StateRestorationTester
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
            FamilyFinAIAppTheme { TestMainHost(initial = MainUiState(families = contentState(), displayName = "rocío")) }
        }

        composeTestRule.onNodeWithText("Movimientos").performClick()
        composeTestRule.onNodeWithTag("tab_placeholder_Movements").assertIsDisplayed()

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

        composeTestRule.onNodeWithText("Inicio").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Agregar").assertIsDisplayed()
    }

    @Test
    fun selectedTabIsKeptWhenTheScreenIsRecreated() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            FamilyFinAIAppTheme { TestMainHost(initial = MainUiState(families = contentState(), displayName = "rocío")) }
        }
        composeTestRule.onNodeWithText("Reportes").performClick()
        composeTestRule.onNodeWithTag("tab_placeholder_Reports").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Reportes").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_placeholder_Reports").assertIsDisplayed()
    }

    @Test
    fun backAfterRecreationStillReturnsToHome() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            FamilyFinAIAppTheme { TestMainHost(initial = MainUiState(families = contentState(), displayName = "rocío")) }
        }
        composeTestRule.onNodeWithText("Presupuestos").performClick()
        restoration.emulateSavedInstanceStateRestore()

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

        composeTestRule.onNodeWithText("Inicio").assertIsSelected()
    }

    @Test
    fun interactiveElementsHaveAccessibleLabels() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { TestMainHost(initial = MainUiState(families = contentState(), displayName = "rocío")) }
        }

        listOf(
            "Selector de familia: Familia Plata, Propietario",
            "Notificaciones",
            "Perfil de usuario, inicial R",
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
    fun repeatedTabClicksDoNotDuplicateEffectsAndFabOpensNewMovementOnce() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme { TestMainHost(initial = MainUiState(families = contentState(), displayName = "rocío")) }
        }

        repeat(5) { composeTestRule.onNodeWithText("Inicio").performClick() }
        composeTestRule.onNodeWithText("Inicio").assertIsSelected()
        composeTestRule.onAllNodesWithContentDescription("Agregar").assertCountEquals(1)
        composeTestRule.onNodeWithContentDescription(
            "Selector de familia: Familia Plata, Propietario",
        ).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Agregar").performClick()

        composeTestRule.onAllNodesWithTag("movement_create_screen").assertCountEquals(1)
        composeTestRule.onAllNodesWithContentDescription("Agregar").assertCountEquals(0)
    }

    @Test
    fun backFromTheEditReturnsToTheDetailAndThenToTheTabs() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme {
                TestMainHost(
                    initial = MainUiState(
                        families = contentState(), displayName = "rocío", selectedTab = MainDestination.Movements,
                        managementRoute = MovementRoute.Detail("m-1"),
                    ),
                )
            }
        }
        composeTestRule.onNodeWithTag("test_detail_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("test_detail_edit").performClick()
        composeTestRule.onNodeWithTag("movement_edit_screen").assertIsDisplayed()

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.onNodeWithTag("test_detail_screen").assertIsDisplayed()

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.onAllNodesWithTag("test_detail_screen").assertCountEquals(0)
        composeTestRule.onNodeWithText("Movimientos").assertIsSelected()
    }

    @Test
    fun theBackArrowOfTheEditAlsoReturnsToTheDetail() {
        composeTestRule.setContent {
            FamilyFinAIAppTheme {
                TestMainHost(
                    initial = MainUiState(
                        families = contentState(), displayName = "rocío",
                        managementRoute = MovementRoute.Edit("m-1"),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("movement_back").performClick()

        composeTestRule.onNodeWithTag("test_detail_screen").assertIsDisplayed()
    }
}
