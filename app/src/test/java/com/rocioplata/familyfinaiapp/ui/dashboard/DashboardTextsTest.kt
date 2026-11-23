package com.rocioplata.familyfinaiapp.ui.dashboard

import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.dashboard.CategoryAmount
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeDirection
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeOutcome
import com.rocioplata.familyfinaiapp.domain.dashboard.ChartSegment
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthComparison
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.math.BigDecimal
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class DashboardTextsTest {
    private val esCl = Locale.forLanguageTag("es-CL")
    private val enUs = Locale.forLanguageTag("en-US")

    // Según la versión de ICU el espacio antes del % es normal, duro o fino: se compara sin distinguirlos.
    private fun String.plain() = replace('\u00A0', ' ').replace('\u202F', ' ')

    private fun pct(text: String) = BigDecimal(text)

    private fun comparison(variation: String, direction: ChangeDirection) = MonthComparison(
        current = Money(1, "CLP"), previous = Money(1, "CLP"), variationPercent = pct(variation), direction = direction,
        outcome = ChangeOutcome.NEUTRAL,
    )

    private fun segment(name: String, share: String) = ChartSegment(
        entry = CategoryAmount(
            Category("c-$name", CategoryType.EXPENSE, name, null, CategoryStatus.ACTIVE, emptyList()),
            Money(1, "CLP"), 0,
        ),
        share = pct(share),
    )

    // --- Porcentaje (RF-05, CA-06) ---

    @Test
    fun porcentaje_conUnDecimal_enEspanolDeChileYEnInglesDeEstadosUnidos() {
        assertEquals("60,6 %", formatPercent(pct("60.6"), esCl).plain())
        assertEquals("60.6%", formatPercent(pct("60.6"), enUs).plain())
        assertEquals("4,2 %", formatPercent(pct("4.2"), esCl).plain())
        assertEquals("160,5 %", formatPercent(pct("160.5"), esCl).plain())
    }

    @Test
    fun porcentaje_entero_sinDecimales() {
        assertEquals("100 %", formatPercent(pct("100.0"), esCl).plain())
        assertEquals("100%", formatPercent(pct("100.0"), enUs).plain())
        assertEquals("50 %", formatPercent(pct("50.0"), esCl).plain())
        assertEquals("0 %", formatPercent(pct("0.0"), esCl).plain())
    }

    @Test
    fun porcentaje_conMilesUsaElSeparadorDeLaRegion() {
        assertEquals("1.250,5 %", formatPercent(pct("1250.5"), esCl).plain())
        assertEquals("1,250.5%", formatPercent(pct("1250.5"), enUs).plain())
    }

    @Test
    fun porcentaje_noUsaComaFlotante_redondeaMitadHaciaArriba() {
        // Un valor con más decimales de los que se muestran se redondea hacia arriba en la mitad: 0,25 → 0,3.
        assertEquals("0,3 %", formatPercent(pct("0.25"), esCl).plain())
        assertEquals("0,1 %", formatPercent(pct("0.05"), esCl).plain())
        assertEquals("33,3 %", formatPercent(pct("33.33333"), esCl).plain())
    }

    // --- Variación frente al mes anterior (RF-06, CA-07) ---

    @Test
    fun variacion_bajaLlevaMenos_subeLlevaMas_ySinCambioNingunSigno() {
        assertEquals("−4,8 %", comparison("-4.8", ChangeDirection.DOWN).formattedVariation(esCl).plain())
        assertEquals("+6,2 %", comparison("6.2", ChangeDirection.UP).formattedVariation(esCl).plain())
        assertEquals("0 %", comparison("0.0", ChangeDirection.UNCHANGED).formattedVariation(esCl).plain())
    }

    @Test
    fun variacion_enInglesDeEstadosUnidos() {
        assertEquals("−4.8%", comparison("-4.8", ChangeDirection.DOWN).formattedVariation(enUs).plain())
        assertEquals("+6.2%", comparison("6.2", ChangeDirection.UP).formattedVariation(enUs).plain())
        assertEquals("0%", comparison("0.0", ChangeDirection.UNCHANGED).formattedVariation(enUs).plain())
    }

    @Test
    fun variacion_elSignoSeDecidePorLaDireccion_noPorElValor() {
        // Un −0,0 que redondeó a cero no debe mostrar signo aunque el valor lo tuviera.
        assertEquals("0 %", comparison("-0.0", ChangeDirection.UNCHANGED).formattedVariation(esCl).plain())
        assertEquals("−100 %", comparison("-100.0", ChangeDirection.DOWN).formattedVariation(esCl).plain())
        assertEquals("+250 %", comparison("250.0", ChangeDirection.UP).formattedVariation(esCl).plain())
    }

    @Test
    fun variacion_laDescripcionDependeDeLaDireccion() {
        assertEquals(R.string.dashboard_variation_up_description, ChangeDirection.UP.variationDescriptionRes())
        assertEquals(R.string.dashboard_variation_down_description, ChangeDirection.DOWN.variationDescriptionRes())
        assertEquals(R.string.dashboard_variation_unchanged_description, ChangeDirection.UNCHANGED.variationDescriptionRes())
        assertEquals(3, ChangeDirection.entries.map { it.variationDescriptionRes() }.toSet().size)
    }

    // --- Descripción del gráfico (RF-04, CA-05) ---

    @Test
    fun descripcionDelGrafico_conElReparto() {
        val segments = listOf(segment("Comestibles", "0.3796000000"), segment("Salud", "0.1000000000"), segment("Casa", "0.0004000000"))

        assertEquals(
            "Gastos del mes, total \$1.120.450. Comestibles 38 %, Salud 10 %, Casa 0 %",
            chartDescription("Gastos del mes, total \$1.120.450", segments, esCl).plain(),
        )
        assertEquals(
            "Expenses, total \$1,120,450. Comestibles 38%, Salud 10%, Casa 0%",
            chartDescription("Expenses, total \$1,120,450", segments, enUs).plain(),
        )
    }

    @Test
    fun descripcionDelGrafico_unaParteConDecimal() {
        assertEquals(
            "Total. Ocio 37,6 %",
            chartDescription("Total", listOf(segment("Ocio", "0.3760000000")), esCl).plain(),
        )
    }

    @Test
    fun descripcionDelGrafico_sinSegmentos_esSoloElEncabezado() {
        assertEquals("Gastos del mes, total \$0", chartDescription("Gastos del mes, total \$0", emptyList(), esCl))
    }

    @Test
    fun parteDeUnSegmento_unoEsCienPorCiento() {
        assertEquals("100 %", formatShare(pct("1.0000000000"), esCl).plain())
        assertEquals("33,3 %", formatShare(pct("0.3333333333"), esCl).plain())
    }

    // --- Textos que dependen del tipo (RF-03, RF-07) ---

    @Test
    fun cadaTipoTieneSusTextos_distintosEntreSi() {
        assertEquals(R.string.dashboard_type_expenses, MovementType.EXPENSE.typeLabelRes())
        assertEquals(R.string.dashboard_type_income, MovementType.INCOME.typeLabelRes())
        assertEquals(R.string.dashboard_breakdown_title_expenses, MovementType.EXPENSE.breakdownTitleRes())
        assertEquals(R.string.dashboard_breakdown_title_income, MovementType.INCOME.breakdownTitleRes())
        assertEquals(R.string.dashboard_categories_title_expenses, MovementType.EXPENSE.categoriesTitleRes())
        assertEquals(R.string.dashboard_categories_title_income, MovementType.INCOME.categoriesTitleRes())
        assertEquals(R.string.dashboard_categories_empty_expenses, MovementType.EXPENSE.noCategoriesRes())
        assertEquals(R.string.dashboard_categories_empty_income, MovementType.INCOME.noCategoriesRes())
        assertEquals(R.string.dashboard_chart_description_expenses, MovementType.EXPENSE.chartDescriptionRes())
        assertEquals(R.string.dashboard_chart_description_income, MovementType.INCOME.chartDescriptionRes())
        val functions: List<(MovementType) -> Int> = listOf(
            { it.typeLabelRes() }, { it.breakdownTitleRes() }, { it.categoriesTitleRes() },
            { it.noCategoriesRes() }, { it.chartDescriptionRes() },
        )
        functions.forEach { assertNotEquals(it(MovementType.EXPENSE), it(MovementType.INCOME)) }
    }

    // --- Contrato de los recursos (se comprueba el XML: las pruebas JVM no cargan recursos de Android) ---

    private val strings: Element by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml")).documentElement
    }

    private fun stringsByName(): Map<String, String> {
        val nodes = strings.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun elContadorDeCategoriasTieneSingularYPlural_conElNumero() {
        val plurals = strings.getElementsByTagName("plurals")
        val counter = (0 until plurals.length).map { plurals.item(it) as Element }
            .single { it.getAttribute("name") == "dashboard_categories_count" }
        val items = counter.getElementsByTagName("item")
        val byQuantity = (0 until items.length).map { items.item(it) as Element }
            .associate { it.getAttribute("quantity") to it.textContent }

        assertEquals(setOf("one", "other"), byQuantity.keys)
        assertEquals("%d categoría", byQuantity["one"])
        assertEquals("%d categorías", byQuantity["other"])
    }

    @Test
    fun losTextosConDatosLlevanSusMarcadores() {
        val texts = stringsByName()

        listOf(
            "dashboard_variation_vs_previous", "dashboard_variation_up_description", "dashboard_variation_down_description",
            "dashboard_chart_received", "dashboard_chart_description_expenses", "dashboard_chart_description_income",
            "dashboard_category_card_action",
        ).forEach { assertTrue("$it sin %1\$s", texts.getValue(it).contains("%1\$s")) }
        listOf(
            "dashboard_type_tile_description", "dashboard_type_tile_selected_description",
            "dashboard_category_card_description", "dashboard_category_card_archived_description",
        ).forEach {
            assertTrue("$it sin %1\$s o %2\$s", texts.getValue(it).contains("%1\$s") && texts.getValue(it).contains("%2\$s"))
        }
        assertTrue(!texts.getValue("dashboard_variation_unchanged_description").contains("%"))
    }

    @Test
    fun losTextosFijosEstanComoPideLaSpec() {
        val texts = stringsByName()

        assertEquals("Desglose de gastos", texts["dashboard_breakdown_title_expenses"])
        assertEquals("Desglose de ingresos", texts["dashboard_breakdown_title_income"])
        assertEquals("Gastos por categoría", texts["dashboard_categories_title_expenses"])
        assertEquals("Ingresos por categoría", texts["dashboard_categories_title_income"])
        assertEquals("Disponible", texts["dashboard_available"])
        assertEquals("Mes actual", texts["dashboard_current_month"])
        assertEquals("Archivada", texts["dashboard_category_archived"])
        assertEquals("Reintentar", texts["dashboard_retry"])
        assertEquals("%1\$s vs mes anterior", texts["dashboard_variation_vs_previous"])
        assertTrue(texts.getValue("dashboard_empty_month").contains("«+»"))
        assertTrue(texts.getValue("dashboard_error").isNotBlank())
    }
}
