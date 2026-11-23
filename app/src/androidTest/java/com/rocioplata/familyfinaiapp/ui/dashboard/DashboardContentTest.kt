package com.rocioplata.familyfinaiapp.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.dashboard.CategoryAmount
import com.rocioplata.familyfinaiapp.domain.dashboard.CategoryBreakdown
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeDirection
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeOutcome
import com.rocioplata.familyfinaiapp.domain.dashboard.ChartSegment
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthComparison
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthTotals
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.dashboard.PaymentMethodAmount
import com.rocioplata.familyfinaiapp.domain.dashboard.PaymentMethodBreakdown
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.ui.categories.CategoryColors
import com.rocioplata.familyfinaiapp.ui.categories.containsColorNear
import com.rocioplata.familyfinaiapp.ui.movements.formatMonth
import com.rocioplata.familyfinaiapp.ui.movements.formatted
import com.rocioplata.familyfinaiapp.ui.theme.Expense
import com.rocioplata.familyfinaiapp.ui.theme.ExpenseStrong
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import com.rocioplata.familyfinaiapp.ui.theme.Income
import com.rocioplata.familyfinaiapp.ui.theme.IncomeStrong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class DashboardContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Events {
        var previous = 0
        var next = 0
        var current = 0
        val types = mutableListOf<MovementType>()
        val categoryClicks = mutableListOf<Pair<String, MovementType>>()
        var manage = 0
        var managePayments = 0
        var retry = 0
    }

    private val october = YearMonth.of(2026, 10)

    private fun clp(amount: Long) = Money(amount, "CLP")

    private fun category(id: String, name: String, color: String) =
        Category(id, CategoryType.EXPENSE, name, null, CategoryStatus.ACTIVE, emptyList(), color)

    private val food = CategoryAmount(category("c1", "Comestibles", "sage"), clp(425_800), 0)
    private val health = CategoryAmount(category("c2", "Salud", "amber"), clp(115_000), 1)
    private val dining = CategoryAmount(category("c3", "Restaurantes", "slate"), clp(579_650), 2)
    private val zero = CategoryAmount(category("c4", "Regalos", "coral"), clp(0), 3)
    private val archived = CategoryAmount(
        category("c5", "Vacaciones", "mauve").copy(status = CategoryStatus.DEPRECATED), clp(30_000), 4,
    )

    private fun share(amount: Long, total: Long) =
        BigDecimal(amount).divide(BigDecimal(total), 10, RoundingMode.HALF_UP)

    private val downFavorable = MonthComparison(
        clp(1_120_450), clp(1_177_000), BigDecimal("-4.8"), ChangeDirection.DOWN, ChangeOutcome.FAVORABLE,
    )

    private fun state(
        type: MovementType = MovementType.EXPENSE,
        income: Long = 1_850_000,
        expenses: Long = 1_120_450,
        comparison: MonthComparison? = downFavorable,
        withSegments: Boolean = true,
        canGoNext: Boolean = true,
        isCurrentMonth: Boolean = false,
        entries: List<CategoryAmount> = listOf(food, health, dining, zero),
        isStale: Boolean = false,
        isRefreshing: Boolean = false,
        categoriesAvailable: Boolean = true,
        paymentMethods: PaymentMethodBreakdown? = null,
    ): DashboardUiState {
        val totals = MonthTotals(clp(income), clp(expenses))
        val segments = if (withSegments) {
            entries.filter { it.hasAmount }.map { ChartSegment(it, share(it.amount.amountMinor, expenses)) }
        } else {
            emptyList()
        }
        return DashboardUiState(
            month = october, canGoNext = canGoNext, isCurrentMonth = isCurrentMonth, activeType = type,
            content = DashboardContentState.Content(
                totals = totals, percentOfIncome = totals.percentOfIncome(type),
                breakdown = if (categoriesAvailable) CategoryBreakdown(totals.totalOf(type), entries, segments) else null,
                comparison = comparison, isMonthEmpty = !withSegments, isStale = isStale, isRefreshing = isRefreshing,
                paymentMethods = paymentMethods,
            ),
        )
    }

    private fun stateOf(content: DashboardContentState) = DashboardUiState(
        month = october, canGoNext = true, isCurrentMonth = false, activeType = MovementType.EXPENSE, content = content,
    )

    private fun show(
        initial: DashboardUiState,
        events: Events = Events(),
        width: Int? = null,
        fontScale: Float? = null,
    ): Events {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale ?: base.fontScale)) {
                FamilyFinAIAppTheme(darkTheme = false) {
                    // Como en Inicio, el dashboard va dentro de un contenedor que se desplaza.
                    val content = @androidx.compose.runtime.Composable {
                        DashboardContent(
                            state = initial,
                            onPreviousMonth = { events.previous++ },
                            onNextMonth = { events.next++ },
                            onCurrentMonth = { events.current++ },
                            onSelectType = { events.types += it },
                            onCategoryClick = { id, type -> events.categoryClicks += id to type },
                            onManageCategories = { events.manage++ },
                            onManagePaymentMethods = { events.managePayments++ },
                            onRetry = { events.retry++ },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        if (width == null) content() else Box(Modifier.width(width.dp)) { content() }
                    }
                }
            }
        }
        return events
    }

    // Posiciones sin recortar por el área visible: con el contenedor desplazable, `getBoundsInRoot` da 0 fuera de ella.
    private fun nodeOf(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    private fun top(tag: String) = nodeOf(tag).positionInRoot.y
    private fun left(tag: String) = nodeOf(tag).positionInRoot.x
    private fun bottom(tag: String) = nodeOf(tag).positionInRoot.y + nodeOf(tag).size.height
    private fun width(tag: String) = nodeOf(tag).size.width

    /** El texto del nodo se dibuja entero: no hay desborde de ancho ni se corta por líneas. */
    private fun assertNotCut(tag: String) {
        val nodes = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("no hay nodos con la etiqueta $tag", nodes.isNotEmpty())
        nodes.forEach { node ->
            val layout = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
            val result = layout.single()
            assertTrue(
                "$tag se corta: '${result.layoutInput.text.text}' tamaño=${result.size} " +
                    "visual=${result.hasVisualOverflow} ancho=${result.didOverflowWidth} " +
                    "multi=${result.multiParagraph.width}x${result.multiParagraph.height} lineas=${result.lineCount} " +
                    "fuente=${result.layoutInput.style.fontSize} maxLines=${result.layoutInput.maxLines} " +
                    "constr=${result.layoutInput.constraints}",
                !result.hasVisualOverflow && !result.didOverflowWidth,
            )
        }
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(0)

    private fun description(tag: String): String? =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    // --- Selector de mes (RF-01, CA-01) ---

    @Test
    fun theMonthSelectorShowsTheMonth() {
        show(state())

        rule.onNodeWithTag("dashboard_month_label").assert(hasText(formatMonth(october)))
    }

    @Test
    fun previousAndNextAreReported_andNextIsDisabledInTheCurrentMonth() {
        val events = show(state(canGoNext = true))

        rule.onNodeWithTag("dashboard_previous_month").assertIsEnabled().performClick()
        rule.onNodeWithTag("dashboard_next_month").assertIsEnabled().performClick()

        assertEquals(1, events.previous)
        assertEquals(1, events.next)
    }

    @Test
    fun inTheCurrentMonth_nextAndCurrentMonthAreDisabledAndDoNotReportTaps() {
        val events = show(state(canGoNext = false, isCurrentMonth = true))

        rule.onNodeWithTag("dashboard_next_month").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("dashboard_current_month").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("dashboard_previous_month").assertIsEnabled()

        assertEquals(0, events.next)
        assertEquals(0, events.current)
    }

    @Test
    fun inAPastMonth_theCurrentMonthButtonIsEnabledAndReported() {
        val events = show(state(isCurrentMonth = false))

        rule.onNodeWithTag("dashboard_current_month").assertIsEnabled().performClick()

        assertEquals(1, events.current)
    }

    // --- Tarjetas Gastos e Ingresos (RF-02, RF-03, CA-03, CA-04) ---

    @Test
    fun theActiveTypeIsSelected_andTheOtherOneIsNot() {
        show(state(type = MovementType.EXPENSE))

        rule.onNodeWithTag("dashboard_type_expenses").assertIsSelected()
        rule.onNodeWithTag("dashboard_type_income").assertIsNotSelected()
    }

    @Test
    fun theTilesAreAnnouncedWithNameAmountAndSelection() {
        show(state(type = MovementType.EXPENSE))

        assertEquals("Gastos, ${clp(1_120_450).formatted()}, seleccionado", description("dashboard_type_expenses"))
        assertEquals("Ingresos, ${clp(1_850_000).formatted()}", description("dashboard_type_income"))
    }

    @Test
    fun tappingTheInactiveTileReportsItsType() {
        val events = show(state(type = MovementType.EXPENSE))

        rule.onNodeWithTag("dashboard_type_income").performClick()

        assertEquals(listOf(MovementType.INCOME), events.types)
    }

    @Test
    fun theTitleFollowsTheActiveType() {
        show(state(type = MovementType.EXPENSE))
        rule.onNodeWithTag("dashboard_breakdown_title").assert(hasText("Desglose de gastos"))
    }

    @Test
    fun theTitleFollowsTheActiveType_income() {
        show(state(type = MovementType.INCOME))

        rule.onNodeWithTag("dashboard_breakdown_title").assert(hasText("Desglose de ingresos"))
        rule.onNodeWithTag("dashboard_type_income").assertIsSelected()
        rule.onNodeWithTag("dashboard_type_expenses").assertIsNotSelected()
    }

    @Test
    fun theAvailableAmountIsShown_alsoWhenNegative() {
        show(state(income = 500_000, expenses = 800_000, withSegments = true))

        rule.onNodeWithTag("dashboard_available_amount").assert(hasText(clp(-300_000).formatted()))
    }

    @Test
    fun theTileAmountsAreTheMonthTotals() {
        show(state())

        rule.onNodeWithTag("dashboard_available_amount").assert(hasText(clp(729_550).formatted()))
    }

    // --- Porcentaje (RF-05, CA-06) ---

    @Test
    fun inExpenses_theActiveTileAndTheCenterShowTheShareOfIncome() {
        show(state(type = MovementType.EXPENSE))

        rule.onNodeWithTag("dashboard_type_percent", useUnmergedTree = true).assert(hasText(formatPercent(BigDecimal("60.6"))))
        rule.onNodeWithTag("dashboard_chart_caption", useUnmergedTree = true).assert(hasText(formatPercent(BigDecimal("60.6"))))
    }

    @Test
    fun inIncome_theCenterSaysHundredPercentReceived() {
        show(state(type = MovementType.INCOME))

        rule.onNodeWithTag("dashboard_chart_caption", useUnmergedTree = true)
            .assert(hasText("${formatPercent(BigDecimal("100"))} recibido"))
        rule.onNodeWithTag("dashboard_type_percent", useUnmergedTree = true).assert(hasText(formatPercent(BigDecimal("100"))))
    }

    @Test
    fun withoutIncome_noPercentIsShownAnywhere() {
        show(state(income = 0, expenses = 100_000))

        absent("dashboard_type_percent")
        absent("dashboard_chart_caption")
    }

    // --- Comparación (RF-06, CA-07) ---

    private fun assertTitleAndVariationShareOneLine(type: MovementType) {
        val down = MonthComparison(clp(1_120_450), clp(1_177_000), BigDecimal("-4.8"), ChangeDirection.DOWN, ChangeOutcome.FAVORABLE)
        show(state(type = type, comparison = down))

        val titleTop = top("dashboard_breakdown_title")
        val titleBottom = bottom("dashboard_breakdown_title")
        val chipTop = top("dashboard_variation")
        val chipBottom = bottom("dashboard_variation")
        assertTrue(
            "$type: el indicador debe estar a la derecha del título",
            left("dashboard_variation") >= left("dashboard_breakdown_title") + width("dashboard_breakdown_title"),
        )
        assertTrue(
            "$type: título ($titleTop-$titleBottom) e indicador ($chipTop-$chipBottom) en líneas distintas",
            chipTop < titleBottom && titleTop < chipBottom,
        )
    }

    @Test
    fun onAPhone_theExpensesTitleAndTheVariationShareOneLine() = assertTitleAndVariationShareOneLine(MovementType.EXPENSE)

    @Test
    fun onAPhone_theIncomeTitleAndTheVariationShareOneLine() = assertTitleAndVariationShareOneLine(MovementType.INCOME)

    @Test
    fun withTheTextAt200Percent_theVariationMovesBelowTheTitle() {
        show(state(), fontScale = 2f)

        assertTrue("con texto grande el indicador baja", top("dashboard_variation") >= bottom("dashboard_breakdown_title"))
        rule.onNodeWithTag("dashboard_variation").assertIsDisplayed()
    }

    @Test
    fun theVariationShowsSignAndText_andIsAnnouncedWithItsDirection() {
        show(state(comparison = downFavorable))

        rule.onNodeWithTag("dashboard_variation_text", useUnmergedTree = true)
            .assert(hasText("${downFavorable.formattedVariation()} vs mes anterior"))
        assertTrue(description("dashboard_variation")!!.startsWith("Bajó"))
        assertTrue(description("dashboard_variation")!!.contains("respecto del mes anterior"))
    }

    @Test
    fun theVariationIsHiddenWithoutComparison() {
        show(state(comparison = null))

        absent("dashboard_variation")
        rule.onNodeWithTag("dashboard_breakdown_card").assertIsDisplayed()
    }

    @Test
    fun anIncreaseAndNoChangeAreAnnouncedAndSignedAccordingly() {
        val up = MonthComparison(clp(120), clp(100), BigDecimal("20.0"), ChangeDirection.UP, ChangeOutcome.UNFAVORABLE)
        show(state(comparison = up))
        assertTrue(description("dashboard_variation")!!.startsWith("Subió"))
        rule.onNodeWithTag("dashboard_variation_text", useUnmergedTree = true).assert(hasText("+${formatPercent(BigDecimal("20.0"))} vs mes anterior"))
    }

    @Test
    fun noChangeShowsNoSign() {
        val flat = MonthComparison(clp(100), clp(100), BigDecimal("0.0"), ChangeDirection.UNCHANGED, ChangeOutcome.NEUTRAL)
        show(state(comparison = flat))

        assertEquals("Sin cambios respecto del mes anterior", description("dashboard_variation"))
        rule.onNodeWithTag("dashboard_variation_text", useUnmergedTree = true).assert(hasText("${formatPercent(BigDecimal.ZERO)} vs mes anterior"))
    }

    @Test
    fun theVariationIsGreenWhenFavorable() {
        show(state(comparison = downFavorable))
        assertTrue("favorable sin verde", rule.onNodeWithTag("dashboard_variation").captureToImage().containsColorNear(IncomeStrong))
    }

    @Test
    fun theVariationIsRedWhenUnfavorable() {
        val bad = MonthComparison(clp(150), clp(100), BigDecimal("50.0"), ChangeDirection.UP, ChangeOutcome.UNFAVORABLE)
        show(state(comparison = bad))

        assertTrue("desfavorable sin rojo", rule.onNodeWithTag("dashboard_variation").captureToImage().containsColorNear(ExpenseStrong))
    }

    // --- Gráfico (RF-04, CA-05) ---

    @Test
    fun theChartIsOneUnitDescribedWithTypeTotalAndSplit() {
        show(state())

        val text = description("dashboard_chart")!!
        assertTrue(text, text.startsWith("Gastos del mes, total ${clp(1_120_450).formatted()}"))
        assertTrue(text, text.contains("Comestibles 38%") || text.contains("Comestibles 38 %"))
        assertTrue(text, text.contains("Restaurantes"))
        assertTrue(text, !text.contains("Regalos"))
    }

    @Test
    fun theChartCenterShowsTheLabelAndTheTotal() {
        show(state())

        rule.onNodeWithTag("dashboard_chart_label", useUnmergedTree = true).assert(hasText("TOTAL"))
        rule.onNodeWithTag("dashboard_chart_total", useUnmergedTree = true).assert(hasText(clp(1_120_450).formatted()))
    }

    @Test
    fun theSegmentsAreDrawnInTheColorOfEachCategory() {
        show(state())

        val image = rule.onNodeWithTag("dashboard_chart").captureToImage()
        listOf("sage", "amber", "slate").forEach {
            assertTrue("falta el color $it", image.containsColorNear(CategoryColors.find(it)!!.tone.light))
        }
        assertTrue("no debe pintar el color de una categoría sin monto", !image.containsColorNear(CategoryColors.find("coral")!!.tone.light))
    }

    @Test
    fun withoutAmountsTheRingIsEmpty_andTheDescriptionIsOnlyTheHeader() {
        show(state(expenses = 0, withSegments = false))

        val text = description("dashboard_chart")!!
        assertEquals("Gastos del mes, total ${clp(0).formatted()}", text)
        val image = rule.onNodeWithTag("dashboard_chart").captureToImage()
        listOf("sage", "amber", "slate", "coral").forEach {
            assertTrue("anillo vacío con color $it", !image.containsColorNear(CategoryColors.find(it)!!.tone.light))
        }
    }

    @Test
    fun theIncomeChartIsDescribedAsIncome() {
        show(state(type = MovementType.INCOME))

        assertTrue(description("dashboard_chart")!!.startsWith("Ingresos del mes, total ${clp(1_850_000).formatted()}"))
    }

    // --- Ancho y texto grande (pantallas estrechas) ---

    @Test
    fun withTheNormalTextSize_theChartIsBesideTheTiles() {
        show(state())

        assertTrue(
            "el gráfico debe estar a la derecha",
            left("dashboard_chart") >= left("dashboard_type_expenses") + width("dashboard_type_expenses") - 1,
        )
        assertNotCut("dashboard_type_amount")
        assertNotCut("dashboard_chart_total")
        assertNotCut("dashboard_available_amount")
    }

    @Test
    fun withTheTextAt200Percent_theChartMovesBelow_andNoAmountIsCut() {
        show(state(), fontScale = 2f)

        assertTrue("el gráfico debe quedar debajo de lo disponible", top("dashboard_chart") >= bottom("dashboard_available"))
        rule.onNodeWithTag("dashboard_chart").assertIsDisplayed()
        assertNotCut("dashboard_chart_total")
        assertNotCut("dashboard_available_amount")
        rule.onAllNodesWithTag("dashboard_type_amount", useUnmergedTree = true).assertCountEquals(2)
        listOf("dashboard_type_expenses", "dashboard_type_income").forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun withHugeAmountsAndLargeText_theTotalsAreShrunkNotCut() {
        val huge = 999_999_999_999L
        show(state(income = huge, expenses = huge - 1), fontScale = 2f)

        assertNotCut("dashboard_chart_total")
        assertNotCut("dashboard_available_amount")
        rule.onAllNodesWithTag("dashboard_type_amount", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun onANarrowScreen_theChartIsBelowTheTiles() {
        show(state(), width = 300)

        assertTrue(top("dashboard_chart") >= bottom("dashboard_available"))
    }

    @Test
    fun onANarrowScreenEverythingIsStillShown() {
        val events = show(state(), width = 300)

        rule.onNodeWithTag("dashboard_type_expenses").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_type_income").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_chart").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_available").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_type_income").performClick()
        assertEquals(listOf(MovementType.INCOME), events.types)
    }

    // --- Sección por categoría (RF-07 a RF-11, CA-09, CA-11) ---

    private fun card(id: String) = "dashboard_category_$id"

    @Test
    fun theSectionShowsTheTitleByTypeAndTheCountOfShownCategories() {
        show(state(type = MovementType.EXPENSE))
        rule.onNodeWithTag("dashboard_categories_title").assert(hasText("Gastos por categoría"))
        rule.onNodeWithTag("dashboard_categories_count", useUnmergedTree = true).assert(hasText("4 categorías"))
    }

    @Test
    fun theTitleFollowsTheActiveType_inTheCategorySection() {
        show(state(type = MovementType.INCOME))

        rule.onNodeWithTag("dashboard_categories_title").assert(hasText("Ingresos por categoría"))
    }

    @Test
    fun aSingleCategoryUsesTheSingularCount() {
        show(state(entries = listOf(food)))

        rule.onNodeWithTag("dashboard_categories_count", useUnmergedTree = true).assert(hasText("1 categoría"))
    }

    @Test
    fun theCardsFollowTheOrderOfTheBreakdown_andShowNameAndAmount() {
        show(state())

        assertTrue(left(card("c1")) < left(card("c2")) && left(card("c2")) < left(card("c3")))
        assertTrue("la cuarta va en la fila siguiente", top(card("c4")) > top(card("c1")))
        assertEquals("Comestibles, ${clp(425_800).formatted()}", description(card("c1")))
        rule.onNodeWithTag(card("c1"), useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("dashboard_category_amount", useUnmergedTree = true).assertCountEquals(4)
    }

    @Test
    fun onAPhoneTheGridHasThreeColumns() {
        show(state())

        assertEquals(top(card("c1")), top(card("c2")), 1f)
        assertEquals(top(card("c1")), top(card("c3")), 1f)
        assertTrue(top(card("c4")) > top(card("c3")))
        assertEquals(left(card("c1")), left(card("c4")), 1f)
    }

    @Test
    fun onANarrowScreenTheGridHasTwoColumns() {
        show(state(), width = 300)

        assertEquals(top(card("c1")), top(card("c2")), 1f)
        assertTrue("la tercera va debajo", top(card("c3")) > top(card("c1")))
    }

    @Test
    fun withTheTextAt200Percent_theGridHasOneColumn_andNoAmountIsCut() {
        show(state(), fontScale = 2f)

        assertEquals(left(card("c1")), left(card("c2")), 1f)
        assertTrue(top(card("c2")) > top(card("c1")))
        assertNotCut("dashboard_category_amount")
        assertTrue("la tarjeta debe ocupar el ancho", width(card("c1")) > width("dashboard_type_expenses") * 0.9f)
    }

    @Test
    fun withHugeAmounts_theCardAmountsAreShrunkNotCut() {
        val big = CategoryAmount(category("c9", "Casa", "olive"), clp(999_999_999_999), 5)
        show(state(entries = listOf(big, food, health)))

        assertNotCut("dashboard_category_amount")
    }

    @Test
    fun aCategoryWithZeroIsDimmed_butAnActiveOneStillWorks() {
        val events = show(state())

        // Atenuada: su trazo más oscuro es más claro que el de una tarjeta con monto.
        val normal = darkest(rule.onNodeWithTag(card("c1")).captureToImage())
        val dimmed = darkest(rule.onNodeWithTag(card("c4")).captureToImage())
        assertTrue("la de cero no se ve atenuada ($dimmed frente a $normal)", dimmed > normal + 0.1f)
        assertEquals("Regalos, ${clp(0).formatted()}", description(card("c4")))

        rule.onNodeWithTag(card("c4")).performClick()
        assertEquals(listOf("c4" to MovementType.EXPENSE), events.categoryClicks)
    }

    @Test
    fun anArchivedCategoryIsMarkedAndAnnounced_andDoesNothingWhenTapped() {
        val events = show(state(entries = listOf(food, archived)))

        rule.onNodeWithTag("dashboard_category_archived", useUnmergedTree = true).assert(hasText("Archivada"))
        assertEquals("Vacaciones, ${clp(30_000).formatted()}, archivada", description(card("c5")))
        rule.onNodeWithTag(card("c5")).performClick()

        assertTrue(events.categoryClicks.isEmpty())
    }

    @Test
    fun tappingAnActiveCategoryReportsItsIdAndTheActiveType() {
        val events = show(state(type = MovementType.INCOME))

        rule.onNodeWithTag(card("c1")).performClick()

        assertEquals(listOf("c1" to MovementType.INCOME), events.categoryClicks)
    }

    @Test
    fun anActiveCardHasAnAccessibleClickActionWithItsLabel() {
        show(state())

        val config = nodeOf(card("c2")).config
        assertEquals("Registrar un movimiento en Salud", config.getOrNull(SemanticsActions.OnClick)?.label)
    }

    @Test
    fun manageOpensTheCategoriesManagement() {
        val events = show(state())

        rule.onNodeWithTag("home_manage_categories").assertIsEnabled().performClick()

        assertEquals(1, events.manage)
    }

    @Test
    fun withoutCategoriesOfTheType_theSectionExplainsIt_andManageStaysAvailable() {
        val events = show(state(entries = emptyList()))

        rule.onNodeWithTag("dashboard_categories_empty").assert(hasText("No hay categorías de gasto activas."))
        absent("dashboard_categories_count")
        rule.onNodeWithTag("home_manage_categories").performClick()
        assertEquals(1, events.manage)
    }

    @Test
    fun whileTheCategoriesHaveNoData_theTotalsShowAndTheSectionWaits() {
        show(state(categoriesAvailable = false))

        rule.onNodeWithTag("dashboard_categories_loading").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_breakdown_card").assertIsDisplayed()
        absent("dashboard_category_grid")
    }

    @Test
    fun theLastRowLeavesRoomForThePlusButton() {
        show(state())

        val spare = bottom("dashboard_content") - bottom(card("c4"))
        assertTrue("sin espacio para el botón «+» ($spare px)", spare >= 80 * rule.density.density)
    }

    // --- Medios de pago (RF-16 a RF-18, CA-19, CA-20) ---

    private fun method(id: String, name: String, status: PaymentMethodStatus = PaymentMethodStatus.ACTIVE) =
        PaymentMethod(id, name, status)

    private val card = PaymentMethodAmount(method("p1", "Tarjeta de crédito"), clp(520_000))
    private val cash = PaymentMethodAmount(method("p2", "Efectivo"), clp(80_450))
    private val unused = PaymentMethodAmount(method("p3", "Cuenta vista"), clp(0))
    private val oldMethod = PaymentMethodAmount(method("p4", "Tarjeta antigua", PaymentMethodStatus.DEPRECATED), clp(30_000))
    private val noMethod = PaymentMethodAmount(null, clp(12_000))

    private fun methods(vararg entries: PaymentMethodAmount) =
        PaymentMethodBreakdown(clp(entries.sumOf { it.amount.amountMinor }), entries.toList())

    private fun methodCard(id: String) = "dashboard_payment_method_$id"

    @Test
    fun theMethodsSectionShowsTitleCountAndOneCardPerMethodWithItsAmount() {
        show(state(paymentMethods = methods(card, cash, unused)))

        rule.onNodeWithTag("dashboard_payment_methods_title").performScrollTo().assert(hasText("Medios de pago"))
        rule.onNodeWithTag("dashboard_payment_methods_count", useUnmergedTree = true).assert(hasText("3 medios de pago"))
        rule.onAllNodesWithTag("dashboard_payment_method_amount", useUnmergedTree = true).assertCountEquals(3)
        assertEquals("Tarjeta de crédito, ${clp(520_000).formatted()}", description(methodCard("p1")))
        assertEquals("Efectivo, ${clp(80_450).formatted()}", description(methodCard("p2")))
        rule.onAllNodesWithTag("dashboard_payment_method_amount", useUnmergedTree = true)[0].assert(hasText(clp(520_000).formatted()))
    }

    @Test
    fun theCountUsesTheSingularForOneMethod() {
        show(state(paymentMethods = methods(card)))

        rule.onNodeWithTag("dashboard_payment_methods_count", useUnmergedTree = true).assert(hasText("1 medio de pago"))
    }

    @Test
    fun theMethodsSectionComesBelowTheCategories() {
        show(state(paymentMethods = methods(card)))

        assertTrue(top("dashboard_payment_methods") >= bottom("dashboard_categories"))
    }

    @Test
    fun aMethodWithZeroIsDimmed() {
        show(state(paymentMethods = methods(card, unused)))

        rule.onNodeWithTag(methodCard("p1")).performScrollTo()
        val normal = darkest(rule.onNodeWithTag(methodCard("p1")).captureToImage())
        val dimmed = darkest(rule.onNodeWithTag(methodCard("p3")).captureToImage())
        assertTrue("el de cero no se ve atenuado ($dimmed frente a $normal)", dimmed > normal + 0.1f)
        assertEquals("Cuenta vista, ${clp(0).formatted()}", description(methodCard("p3")))
    }

    @Test
    fun anArchivedMethodIsMarkedAndAnnounced() {
        show(state(paymentMethods = methods(card, oldMethod)))

        rule.onNodeWithTag("dashboard_payment_method_archived", useUnmergedTree = true).performScrollTo().assert(hasText("Archivado"))
        assertEquals("Tarjeta antigua, ${clp(30_000).formatted()}, archivado", description(methodCard("p4")))
    }

    @Test
    fun theUnassignedCardIsNamedWithoutAMethod() {
        show(state(paymentMethods = methods(card, noMethod)))

        rule.onNodeWithTag(methodCard("none")).performScrollTo()
        assertEquals("Sin medio de pago, ${clp(12_000).formatted()}", description(methodCard("none")))
    }

    @Test
    fun theMethodCardsCannotBeTapped() {
        show(state(paymentMethods = methods(card, oldMethod, noMethod)))

        rule.onNodeWithTag(methodCard("p1")).performScrollTo().assertHasNoClickAction()
        rule.onNodeWithTag(methodCard("p4")).assertHasNoClickAction()
        rule.onNodeWithTag(methodCard("none")).assertHasNoClickAction()
    }

    @Test
    fun manageInTheMethodsSectionIsReported() {
        val events = show(state(paymentMethods = methods(card)))

        rule.onNodeWithTag("home_manage_payment_methods").performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, events.managePayments)
        assertEquals(0, events.manage)
    }

    @Test
    fun whileTheMethodsHaveNoData_theTotalsShowAndTheSectionWaits() {
        show(state(paymentMethods = null))

        rule.onNodeWithTag("dashboard_payment_methods_loading").performScrollTo().assert(hasText("Cargando medios de pago…"))
        rule.onNodeWithTag("dashboard_breakdown_card").assertIsDisplayed()
        absent("dashboard_payment_method_grid")
        absent("dashboard_payment_methods_count")
    }

    @Test
    fun withoutAnyMethod_theSectionInvitesToCreateThemAndManageStaysAvailable() {
        show(state(paymentMethods = PaymentMethodBreakdown(clp(0), emptyList())))

        rule.onNodeWithTag("dashboard_payment_methods_empty").performScrollTo()
            .assert(hasText("No hay medios de pago activos. Créalos con «Gestionar»."))
        rule.onNodeWithTag("home_manage_payment_methods").assertIsEnabled()
        absent("dashboard_payment_method_grid")
    }

    @Test
    fun theMethodCardsFitWithLargeText() {
        show(state(paymentMethods = methods(card, cash, unused, oldMethod, noMethod)), fontScale = 2f)

        rule.onNodeWithTag(methodCard("p1")).performScrollTo()
        assertNotCut("dashboard_payment_method_amount")
    }

    @Test
    fun theMethodsSectionKeepsThreeColumnsOnAPhone() {
        show(state(paymentMethods = methods(card, cash, unused)), width = 360)

        rule.onNodeWithTag(methodCard("p1")).performScrollTo()
        assertEquals(top(methodCard("p1")), top(methodCard("p3")), 1f)
        assertTrue(left(methodCard("p3")) > left(methodCard("p2")))
    }

    // --- Estados (CA-15, CA-16) ---

    @Test
    fun whileLoading_theMonthSelectorStaysAndThereIsNoBreakdown() {
        show(stateOf(DashboardContentState.Loading))

        rule.onNodeWithTag("dashboard_loading").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_month_label").assertIsDisplayed()
        absent("dashboard_breakdown_card")
        absent("dashboard_categories")
    }

    @Test
    fun onError_theMessageAndRetryAreShown_andRetryIsReported() {
        val events = show(stateOf(DashboardContentState.Error))

        rule.onNodeWithTag("dashboard_error").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_month_label").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_retry").performClick()

        assertEquals(1, events.retry)
        absent("dashboard_breakdown_card")
    }

    @Test
    fun anEmptyMonthShowsZerosTheEmptyRingAndTheInvitation() {
        show(state(income = 0, expenses = 0, withSegments = false, comparison = null))

        rule.onNodeWithTag("dashboard_empty_month").assert(hasText("No hay movimientos este mes. Registra uno con el botón «+»."))
        rule.onNodeWithTag("dashboard_available_amount").assert(hasText(clp(0).formatted()))
        rule.onAllNodesWithTag("dashboard_category_amount", useUnmergedTree = true).assertCountEquals(4)
        assertEquals("Gastos del mes, total ${clp(0).formatted()}", description("dashboard_chart"))
    }

    @Test
    fun aMonthWithMovementsDoesNotShowTheInvitation() {
        show(state())

        absent("dashboard_empty_month")
    }

    @Test
    fun theStaleBannerShowsOnlyWithOldData() {
        show(state(isStale = true))
        rule.onNodeWithTag("dashboard_stale").assertIsDisplayed()
    }

    @Test
    fun theStaleBannerIsHiddenWithFreshData() {
        show(state(isStale = false))

        absent("dashboard_stale")
    }

    @Test
    fun theRefreshBarShowsWhileUpdating() {
        show(state(isRefreshing = true))
        rule.onNodeWithTag("dashboard_refreshing").assertIsDisplayed()
    }

    @Test
    fun theRefreshBarIsHiddenWhenNotUpdating() {
        show(state(isRefreshing = false))

        absent("dashboard_refreshing")
    }

    /** Luminancia del píxel más oscuro de una imagen (0 negro, 1 blanco). */
    private fun darkest(image: androidx.compose.ui.graphics.ImageBitmap): Float {
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels, 0, 0, image.width, image.height)
        return pixels.minOf { px ->
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            0.2126f * r + 0.7152f * g + 0.0722f * b
        }
    }
}
