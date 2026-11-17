package com.rocioplata.familyfinaiapp.ui.movements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class MovementsContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val nov = YearMonth.of(2026, 11)
    private fun clp(amount: Long) = Money(amount, "CLP")

    private val superRow = MovementRow(
        "m-super", MovementType.EXPENSE, "Supermercado Jumbo", clp(45_900), "Comida", "shopping_cart",
        "supermercado", "Tarjeta", MemberLabel.You("Valentina Plata", FamilyRole.OWNER),
    )
    private val sueldoRow = MovementRow(
        "m-sueldo", MovementType.INCOME, "Sueldo", clp(1_200_000), "Sueldo", null, null, null,
        MemberLabel.Named("Carlos Mendoza", FamilyRole.MEMBER),
    )
    private val exRow = MovementRow(
        "m-ex", MovementType.EXPENSE, "Bencina", clp(25_000), null, null, null, null, MemberLabel.FormerMember,
    )
    private val unavailableRow = MovementRow(
        "m-na", MovementType.EXPENSE, "Taxi", clp(5_000), "Transporte", null, null, null, MemberLabel.Unavailable,
    )

    private fun content(
        groups: List<DayGroup> = listOf(
            DayGroup(nov.atDay(15), clp(1_154_100), listOf(superRow, sueldoRow)),
            DayGroup(nov.atDay(3), clp(-30_000), listOf(exRow)),
        ),
        summary: MonthSummary? = MonthSummary(clp(1_200_000), clp(70_900), clp(1_129_100)),
        isMonthEmpty: Boolean = false,
        isFilterEmpty: Boolean = false,
        isStale: Boolean = false,
        isRefreshing: Boolean = false,
    ) = MovementsListState.Content(summary, groups, isMonthEmpty, isFilterEmpty, isStale, isRefreshing)

    private fun state(
        list: MovementsListState = content(),
        canGoNext: Boolean = false,
        filter: MovementFilter = MovementFilter.ALL,
    ) = MovementsUiState(nov, canGoNext, filter, list)

    private fun show(
        state: MovementsUiState,
        onPrevious: () -> Unit = {},
        onNext: () -> Unit = {},
        onFilter: (MovementFilter) -> Unit = {},
        onRetry: () -> Unit = {},
        onClick: (String) -> Unit = {},
    ) = composeTestRule.setContent {
        FamilyFinAIAppTheme { MovementsContent(state, onPrevious, onNext, onFilter, onRetry, onClick) }
    }

    @Test
    fun showsMonthSummaryWithBalanceIncomeAndExpenses() {
        show(state())

        composeTestRule.onNodeWithTag("movements_month").assertTextEquals(formatMonth(nov))
        composeTestRule.onNodeWithTag("movements_summary_balance")
            .assertTextEquals(clp(1_129_100).formattedSigned())
        composeTestRule.onNodeWithTag("movements_summary_income")
            .assertTextContains(clp(1_200_000).formattedFor(MovementType.INCOME))
        composeTestRule.onNodeWithTag("movements_summary_expenses")
            .assertTextContains(clp(70_900).formattedFor(MovementType.EXPENSE))
    }

    @Test
    fun nextMonthIsDisabledInTheCurrentMonthAndEnabledOtherwise() {
        var next = 0
        show(state(canGoNext = false), onNext = { next++ })
        composeTestRule.onNodeWithTag("movements_next_month").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movements_prev_month").assertIsEnabled()
        assertEquals(0, next)
    }

    @Test
    fun monthArrowsCallTheirActions() {
        var previous = 0
        var next = 0
        show(state(canGoNext = true), onPrevious = { previous++ }, onNext = { next++ })

        composeTestRule.onNodeWithTag("movements_prev_month").performClick()
        composeTestRule.onNodeWithTag("movements_next_month").performClick()

        assertEquals(1, previous)
        assertEquals(1, next)
    }

    @Test
    fun filterShowsTheSelectedOneAndReportsTheChosen() {
        val chosen = mutableListOf<MovementFilter>()
        show(state(filter = MovementFilter.EXPENSES), onFilter = { chosen += it })

        composeTestRule.onNodeWithTag("movements_filter_EXPENSES").assertIsSelected()
        composeTestRule.onNodeWithTag("movements_filter_INCOMES").performClick()

        assertEquals(listOf(MovementFilter.INCOMES), chosen)
    }

    @Test
    fun groupsAppearInOrderWithTheirSubtotal() {
        show(state())

        val first = composeTestRule.onNodeWithTag("movements_day_${nov.atDay(15).toEpochDay()}")
        val second = composeTestRule.onNodeWithTag("movements_day_${nov.atDay(3).toEpochDay()}")
        first.assertIsDisplayed()
        second.assertIsDisplayed()
        assertTrue(first.getUnclippedBoundsInRoot().top < second.getUnclippedBoundsInRoot().top)
        composeTestRule.onNodeWithText(formatDay(nov.atDay(15))).assertIsDisplayed()
        composeTestRule.onNodeWithText(clp(1_154_100).formattedSigned()).assertIsDisplayed()
        composeTestRule.onNodeWithText(clp(-30_000).formattedSigned()).assertIsDisplayed()
    }

    @Test
    fun rowShowsAllItsDataAndAnAccessibleDescriptionWithTheType() {
        show(state())

        composeTestRule.onNodeWithTag("movement_row_m-super")
            .assertTextContains("Supermercado Jumbo")
            .assertTextContains("Comida · #supermercado")
            .assertTextContains(clp(45_900).formattedFor(MovementType.EXPENSE))
        composeTestRule.onNodeWithTag("movement_row_m-sueldo")
            .assertTextContains(clp(1_200_000).formattedFor(MovementType.INCOME))
        composeTestRule.onNodeWithTag("movement_row_m-super")
            .assertContentDescriptionContains("Gasto")
            .assertContentDescriptionContains("Supermercado Jumbo")
            .assertContentDescriptionContains(clp(45_900).formattedFor(MovementType.EXPENSE))
            .assertContentDescriptionContains("Comida")
        composeTestRule.onNodeWithTag("movement_row_m-sueldo").assertContentDescriptionContains("Ingreso")
    }

    @Test
    fun showsYouFormerMemberAndOthersByName_andNeverAnEmail() {
        show(state())

        composeTestRule.onNodeWithText("Valentina Plata").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tú").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carlos Mendoza").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ex-miembro").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("@", substring = true).assertCountEquals(0)
    }

    @Test
    fun whenTheMembersAreUnavailableTheRecordedByLineIsOmittedAndTheRestIsStillVisible() {
        show(state(content(groups = listOf(DayGroup(nov.atDay(3), clp(-5_000), listOf(unavailableRow))))))

        composeTestRule.onNodeWithText("Taxi").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("No disponible").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Ex-miembro").assertCountEquals(0)
    }

    @Test
    fun aMovementWhoseCategoryIsNotFoundShowsAGenericLabel() {
        show(state())

        composeTestRule.onNodeWithText("Sin categoría").assertIsDisplayed()
    }

    @Test
    fun tappingARowReportsItsId() {
        val clicked = mutableListOf<String>()
        show(state(), onClick = { clicked += it })

        composeTestRule.onNodeWithTag("movement_row_m-sueldo").performClick()

        assertEquals(listOf("m-sueldo"), clicked)
    }

    @Test
    fun aVeryLongTitleDoesNotBreakTheRow() {
        val long = superRow.copy(title = "Supermercado ".repeat(30))
        show(state(content(groups = listOf(DayGroup(nov.atDay(15), clp(-45_900), listOf(long))))))

        composeTestRule.onNodeWithTag("movement_row_m-super").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_row_m-super")
            .assertTextContains(clp(45_900).formattedFor(MovementType.EXPENSE))
    }

    @Test
    fun emptyMonthShowsTheMessageAndTheSummaryInZero() {
        show(
            state(
                content(
                    groups = emptyList(), isMonthEmpty = true,
                    summary = MonthSummary(clp(0), clp(0), clp(0)),
                ),
            ),
        )

        composeTestRule.onNodeWithTag("movements_empty").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movements_summary").assertIsDisplayed()
    }

    @Test
    fun aFilterWithoutResultsIsNotShownAsAnEmptyMonth() {
        show(state(content(groups = emptyList(), isFilterEmpty = true), filter = MovementFilter.INCOMES))

        composeTestRule.onNodeWithTag("movements_filter_empty").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("movements_empty").assertCountEquals(0)
    }

    @Test
    fun loadingKeepsTheMonthSelectorAndFilter() {
        show(state(MovementsListState.Loading))

        composeTestRule.onNodeWithTag("movements_loading").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movements_prev_month").assertIsEnabled()
        composeTestRule.onNodeWithTag("movements_filter_ALL").assertIsDisplayed()
    }

    @Test
    fun errorOffersRetry() {
        var retries = 0
        show(state(MovementsListState.Error), onRetry = { retries++ })

        composeTestRule.onNodeWithTag("movements_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movements_retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun staleDataShowsTheBannerAndRefreshingTheProgress() {
        show(state(content(isStale = true, isRefreshing = true)))

        composeTestRule.onNodeWithText(
            "Los datos podrían estar desactualizados. Conéctate para actualizarlos.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("movements_refreshing").assertIsDisplayed()
    }

    @Test
    fun freshDataShowsNoBanner() {
        show(state())

        composeTestRule.onAllNodesWithText("Los datos podrían", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("movements_refreshing").assertCountEquals(0)
    }

    @Test
    fun interactiveAreasAreAtLeast48dp() {
        show(state(canGoNext = true))

        composeTestRule.onNodeWithTag("movements_prev_month").assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeTestRule.onNodeWithTag("movements_next_month").assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeTestRule.onNodeWithTag("movement_row_m-super").assertHeightIsAtLeast(48.dp)
        MovementFilter.entries.forEach {
            composeTestRule.onNodeWithTag("movements_filter_${it.name}").assertTouchHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun theSelectedFilterSurvivesRecreation() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            var filter by rememberSaveable { mutableStateOf(MovementFilter.ALL) }
            FamilyFinAIAppTheme {
                MovementsContent(state(filter = filter), {}, {}, { filter = it }, {}, {})
            }
        }
        composeTestRule.onNodeWithTag("movements_filter_EXPENSES").performClick()
        composeTestRule.onNodeWithTag("movements_filter_EXPENSES").assertIsSelected()

        restoration.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag("movements_filter_EXPENSES").assertIsSelected()
    }
}

private fun SemanticsNodeInteraction.assertContentDescriptionContains(text: String): SemanticsNodeInteraction =
    assert(hasContentDescription(text, substring = true))
