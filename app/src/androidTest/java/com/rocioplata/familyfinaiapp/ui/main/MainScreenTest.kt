package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
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
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Gómez")
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
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
    }

    @Test
    fun singleFamilyHasNoSelector() {
        setHost(MainUiState(families = contentState(listOf(OwnerFamily)), displayName = "rocío"))

        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).performClick()

        composeTestRule.onAllNodesWithText("Elegir familia").assertCountEquals(0)
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
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
    fun everyTabShowsTitleWithActiveFamilyAndOnlyMovementsHasFab() {
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
            composeTestRule.onAllNodesWithContentDescription("Agregar")
                .assertCountEquals(if (destination == MainDestination.Movements) 1 else 0)
        }
    }

    @Test
    fun fabOpensNewMovementFromHomeAndFromMovementsAndBackReturnsToTheTab() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithContentDescription("Agregar").performClick()
        composeTestRule.onNodeWithTag("movement_create_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nuevo movimiento").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Agregar").assertCountEquals(0)
        composeTestRule.onNodeWithTag("movement_back").performClick()
        composeTestRule.onNodeWithText("Inicio").assertIsSelected()

        composeTestRule.onNodeWithText("Movimientos").performClick()
        composeTestRule.onNodeWithContentDescription("Agregar").performClick()
        composeTestRule.onNodeWithTag("movement_create_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_back").performClick()
        composeTestRule.onNodeWithText("Movimientos").assertIsSelected()
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

    // --- Gestión de categorías (005, T-13) ---

    @Test
    fun homeShowsTheDashboardOfTheActiveFamilyWithItsCategoriesManageLink() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
        composeTestRule.onNodeWithTag("dashboard_breakdown_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_manage_categories").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeNoLongerShowsTheCategoriesSectionNorTheProvisionalText() {
        setHost(severalFamilies)

        composeTestRule.onAllNodesWithText("Categorías").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Inicio · Familia Plata").assertCountEquals(0)
        composeTestRule.onNodeWithText("Gastos por categoría").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeScrollsToReachThePaymentMethodsSectionBelowTheDashboard() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithText("Medios de pago").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_manage_payment_methods").assertIsDisplayed()
    }

    @Test
    fun tappingACategoryOfTheDashboardOpensTheNewMovementScreenFullScreen() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithTag("dashboard_category_c-food").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("movement_create_screen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("home_content").assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription(OWNER_SELECTOR).assertCountEquals(0)
    }

    @Test
    fun theDashboardIsRebuiltWhenTheFamilyChanges() {
        setHost(severalFamilies)
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")

        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).performClick()
        composeTestRule.onNodeWithContentDescription("Familia Gómez, Integrante").performClick()

        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Gómez")
        composeTestRule.onNodeWithTag("dashboard_breakdown_card").assertIsDisplayed()
    }

    @Test
    fun manageIsHiddenWhenThereIsNoActiveFamily() {
        setHost(MainUiState(families = FamiliesState.Loading))

        composeTestRule.onAllNodesWithText("Gestionar").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Categorías").assertCountEquals(0)
    }

    @Test
    fun manageOpensCategoriesFullScreenWithoutHeaderNorTabs() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithTag("home_manage_categories").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("categories_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Categorías").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Volver").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription(OWNER_SELECTOR).assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("Perfil de usuario, inicial R").assertCountEquals(0)
        listOf("Inicio", "Movimientos", "Reportes", "Presupuestos", "Asistente").forEach {
            composeTestRule.onAllNodesWithText(it).assertCountEquals(0)
        }
        composeTestRule.onAllNodesWithContentDescription("Agregar").assertCountEquals(0)
    }

    @Test
    fun theArrowReturnsToHomeWithHeaderAndTabs() {
        setHost(severalFamilies)
        composeTestRule.onNodeWithTag("home_manage_categories").performScrollTo().performClick()

        composeTestRule.onNodeWithContentDescription("Volver").performClick()

        composeTestRule.onAllNodesWithTag("categories_screen").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).assertIsDisplayed()
        composeTestRule.onNodeWithText("Inicio").assertIsSelected()
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
    }

    @Test
    fun theSystemBackGestureReturnsToHome() {
        setHost(severalFamilies)
        composeTestRule.onNodeWithTag("home_manage_categories").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("categories_screen").assertIsDisplayed()

        pressBack()

        composeTestRule.onAllNodesWithTag("categories_screen").assertCountEquals(0)
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).assertIsDisplayed()
    }

    @Test
    fun categoriesBackArrowHasATouchTargetOfAtLeast48dp() {
        setHost(severalFamilies)
        composeTestRule.onNodeWithTag("home_manage_categories").performScrollTo().performClick()

        // El área táctil (no el tamaño visual de 40 dp) es la que debe llegar a 48 dp.
        composeTestRule.onNodeWithTag("categories_back")
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
    }

    // --- Gestión de medios de pago (006, T-09) ---

    @Test
    fun homeShowsPaymentMethodsSectionBelowTheDashboard() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithText("Medios de pago").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_manage_payment_methods").assertIsDisplayed()
        val paymentTop = composeTestRule.onNodeWithText("Medios de pago").fetchSemanticsNode().positionInRoot.y
        val dashboard = composeTestRule.onNodeWithTag("dashboard_content").fetchSemanticsNode()
        val dashboardBottom = dashboard.positionInRoot.y + dashboard.size.height
        assertTrue("«Medios de pago» debe estar debajo del dashboard", paymentTop >= dashboardBottom)
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
    }

    @Test
    fun theTwoManageLinksAreToldApartByTheScreenReader() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithContentDescription("Gestionar medios de pago").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Gestionar categorías").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun paymentMethodsSectionIsHiddenWhenThereIsNoActiveFamily() {
        setHost(MainUiState(families = FamiliesState.Loading))

        composeTestRule.onAllNodesWithText("Medios de pago").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("home_manage_payment_methods").assertCountEquals(0)
    }

    @Test
    fun managePaymentMethodsOpensFullScreenWithoutHeaderNorTabs() {
        setHost(severalFamilies)

        composeTestRule.onNodeWithTag("home_manage_payment_methods").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("payment_methods_screen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("categories_screen").assertCountEquals(0)
        composeTestRule.onNodeWithText("Medios de pago").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Volver").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription(OWNER_SELECTOR).assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("Perfil de usuario, inicial R").assertCountEquals(0)
        listOf("Inicio", "Movimientos", "Reportes", "Presupuestos", "Asistente").forEach {
            composeTestRule.onAllNodesWithText(it).assertCountEquals(0)
        }
        composeTestRule.onAllNodesWithContentDescription("Agregar").assertCountEquals(0)
    }

    @Test
    fun thePaymentMethodsArrowReturnsToHomeWithHeaderAndTabs() {
        setHost(severalFamilies)
        composeTestRule.onNodeWithTag("home_manage_payment_methods").performScrollTo().performClick()

        composeTestRule.onNodeWithContentDescription("Volver").performClick()

        composeTestRule.onAllNodesWithTag("payment_methods_screen").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
    }

    @Test
    fun theSystemBackGestureReturnsFromPaymentMethodsToHome() {
        setHost(severalFamilies)
        composeTestRule.onNodeWithTag("home_manage_payment_methods").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("payment_methods_screen").assertIsDisplayed()

        pressBack()

        composeTestRule.onAllNodesWithTag("payment_methods_screen").assertCountEquals(0)
        composeTestRule.onNodeWithTag("test_dashboard_family").assertTextEquals("Familia Plata")
        composeTestRule.onNodeWithContentDescription(OWNER_SELECTOR).assertIsDisplayed()
    }

    @Test
    fun paymentMethodsBackArrowHasATouchTargetOfAtLeast48dp() {
        setHost(severalFamilies)
        composeTestRule.onNodeWithTag("home_manage_payment_methods").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("payment_methods_back")
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun manageLinksHaveATouchTargetOfAtLeast48dp() {
        setHost(severalFamilies)

        // El área táctil (no el tamaño visual de 40 dp del botón) es la que debe llegar a 48 dp.
        listOf("home_manage_payment_methods", "home_manage_categories").forEach {
            composeTestRule.onNodeWithTag(it).performScrollTo().assertTouchHeightIsEqualTo(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }
}
