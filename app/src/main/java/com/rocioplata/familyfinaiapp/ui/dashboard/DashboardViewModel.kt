package com.rocioplata.familyfinaiapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.dashboard.DashboardCalculator
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthTotalsResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementsData
import com.rocioplata.familyfinaiapp.domain.movements.MovementsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Estado del dashboard de Inicio de una familia (RF-01 a RF-09, RF-13): mes mostrado (independiente del de la
 * pestaña «Movimientos»), tipo activo y el resumen calculado con los movimientos del mes y del anterior y con las
 * categorías, todo de los repositorios y sin llamadas propias: lo que se registre, edite o elimine se refleja solo
 * porque esos repositorios actualizan sus cachés. Mes y tipo viven aquí y sobreviven a la recreación; se crea con
 * una clave que incluye la sesión, la familia y las veces que cambió la familia activa, así que cambiar de familia
 * o de usuario nunca reutiliza su estado (CA-17, CA-18). No se guarda nada al terminar el proceso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val movements: MovementsRepository,
    private val categories: CategoriesRepository,
    private val familyId: String,
    /** Moneda de la familia; solo se usa para el resumen de un mes sin movimientos. */
    private val currency: String?,
    private val dates: MovementDates,
    connectivity: ConnectivityObserver,
    /** El backend dijo que el usuario ya no puede operar en esta familia: que la pantalla la reevalúe. */
    private val onInsufficientRole: () -> Unit = {},
) : ViewModel() {
    private val month = MutableStateFlow(dates.currentMonth())
    private val activeType = MutableStateFlow(MovementType.EXPENSE)
    private val refreshState = MutableStateFlow(RefreshState())
    private val refreshJobs = mutableMapOf<YearMonth, Job>()

    // El mes mostrado y el anterior (para la comparación); `flatMapLatest` evita mezclar datos de dos meses.
    private val monthData = month.flatMapLatest { shown ->
        combine(
            movements.movements(familyId, shown),
            movements.movements(familyId, shown.minusMonths(1)),
        ) { current, previous -> MonthData(shown, current, previous) }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        monthData,
        categories.categories(familyId),
        month,
        activeType,
        refreshState,
    ) { data, categoryData, shown, type, refresh -> buildState(data, categoryData, shown, type, refresh) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialState())

    init {
        refreshAll()
        viewModelScope.launch {
            connectivity.isOnline.drop(1).filter { it }.collect { refreshAll() }
        }
    }

    /** Al volver a primer plano. Ignorado para lo que ya tiene una carga en curso. */
    fun onStart() = refreshAll()

    fun retry() = refreshAll()

    fun previousMonth() = showMonth(month.value.minusMonths(1))

    /** Ignorado en el mes en curso: no hay meses futuros (RF-01). */
    fun nextMonth() {
        if (!canGoNext(month.value)) return
        showMonth(month.value.plusMonths(1))
    }

    /** «Mes actual» (RF-01). */
    fun goToCurrentMonth() {
        val current = dates.currentMonth()
        if (month.value != current) showMonth(current)
    }

    /** Tocar la tarjeta del tipo no activo (RF-03). */
    fun selectType(type: MovementType) {
        activeType.value = type
    }

    private fun showMonth(shown: YearMonth) {
        month.value = shown
        refreshMonths(shown)
    }

    private fun canGoNext(shown: YearMonth) = shown.isBefore(dates.currentMonth())

    private fun refreshAll() {
        refreshMonths(month.value)
        // Las categorías salen del mismo repositorio que la gestión; su fallo no impide ver los totales.
        viewModelScope.launch { categories.refresh(familyId) }
    }

    // El mes mostrado y su anterior: este último solo sirve para el indicador de comparación (RF-06).
    private fun refreshMonths(shown: YearMonth) {
        refreshMonth(shown)
        refreshMonth(shown.minusMonths(1))
    }

    private fun refreshMonth(target: YearMonth) {
        if (refreshJobs[target]?.isActive == true) return
        refreshJobs[target] = viewModelScope.launch {
            refreshState.update { it.copy(inFlight = it.inFlight + target) }
            val result = try {
                movements.refresh(familyId, target)
            } catch (e: CancellationException) {
                throw e
            } finally {
                refreshState.update { it.copy(inFlight = it.inFlight - target) }
            }
            refreshState.update { it.copy(results = it.results + (target to result)) }
            if ((result as? MovementResult.Failure)?.failure == MovementFailure.NotAllowed) onInsufficientRole()
        }
    }

    private fun initialState() = DashboardUiState(
        month = month.value,
        canGoNext = canGoNext(month.value),
        isCurrentMonth = true,
        activeType = MovementType.EXPENSE,
        content = DashboardContentState.Loading,
    )

    private fun buildState(
        data: MonthData,
        categoryData: CategoriesData,
        shown: YearMonth,
        type: MovementType,
        refresh: RefreshState,
    ): DashboardUiState {
        val content = when {
            // Al cambiar de mes el flujo del anterior puede emitir una vez más: no se mezclan.
            data.month != shown -> DashboardContentState.Loading
            else -> buildContent(data, categoryData, type, refresh, shown)
        }
        return DashboardUiState(shown, canGoNext(shown), shown == dates.currentMonth(), type, content)
    }

    private fun buildContent(
        data: MonthData,
        categoryData: CategoriesData,
        type: MovementType,
        refresh: RefreshState,
        shown: YearMonth,
    ): DashboardContentState {
        val rows = data.current.movements ?: return when {
            shown in refresh.inFlight || shown !in refresh.results -> DashboardContentState.Loading
            refresh.results.getValue(shown) is MovementResult.Failure -> DashboardContentState.Error
            else -> DashboardContentState.Loading
        }
        val totals = when (val result = DashboardCalculator.monthTotals(rows, currency)) {
            is MonthTotalsResult.Valid -> result.totals
            // Monedas mezcladas (la de la familia cambió con datos guardados): no se suma. Sin moneda no hay ceros.
            MonthTotalsResult.MixedCurrencies, MonthTotalsResult.UnknownCurrency -> return DashboardContentState.Error
        }
        val moneyCurrency = totals.income.currency
        return DashboardContentState.Content(
            totals = totals,
            percentOfIncome = totals.percentOfIncome(type),
            breakdown = categoryData.categories?.let {
                DashboardCalculator.categoryBreakdown(rows, it, type, moneyCurrency)
            },
            comparison = DashboardCalculator.comparison(
                current = rows,
                previous = data.previous.movements,
                type = type,
                shown = shown,
                today = dates.today(),
                currency = moneyCurrency,
            ),
            isMonthEmpty = rows.isEmpty(),
            isStale = data.current.isStale,
            isRefreshing = shown in refresh.inFlight,
        )
    }

    private data class MonthData(val month: YearMonth, val current: MovementsData, val previous: MovementsData)

    private data class RefreshState(
        val inFlight: Set<YearMonth> = emptySet(),
        val results: Map<YearMonth, MovementResult<Unit>> = emptyMap(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
