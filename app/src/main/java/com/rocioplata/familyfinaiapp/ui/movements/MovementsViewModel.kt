package com.rocioplata.familyfinaiapp.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.money.sum
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementsData
import com.rocioplata.familyfinaiapp.domain.movements.MovementsRepository
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Estado del listado de movimientos de una familia (RF-01 a RF-03, RF-13, RF-15, RF-17): mes mostrado,
 * filtro de tipo, y filas ya resueltas con categorías, medios de pago, miembros y la sesión. Mes y filtro
 * viven aquí y sobreviven a la recreación. Se crea con una clave `sesión-familia`, por lo que cambiar de
 * familia o de usuario nunca reutiliza su estado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovementsViewModel(
    private val movements: MovementsRepository,
    private val categories: CategoriesRepository,
    private val paymentMethods: PaymentMethodsRepository,
    private val members: FamilyMembersRepository,
    private val familyId: String,
    /** Moneda de la familia; solo se usa para el resumen de un mes sin movimientos. */
    private val currency: String?,
    /** Usuario de la sesión, para marcar "Tú" (RF-15). */
    private val currentUserId: String?,
    private val dates: MovementDates,
    connectivity: ConnectivityObserver,
    /** El backend dijo que el usuario ya no puede operar en esta familia: que la pantalla la reevalúe. */
    private val onInsufficientRole: () -> Unit = {},
) : ViewModel() {
    private val month = MutableStateFlow(dates.currentMonth())
    private val filter = MutableStateFlow(MovementFilter.ALL)
    private val refreshState = MutableStateFlow(RefreshState())
    private val refreshJobs = mutableMapOf<YearMonth, Job>()

    private val monthData = month.flatMapLatest { shown ->
        movements.movements(familyId, shown).map { data -> MonthData(shown, data) }
    }

    private val catalogs = combine(
        categories.categories(familyId),
        paymentMethods.paymentMethods(familyId),
        members.members(familyId),
    ) { categoryData, paymentData, memberData ->
        Catalogs(
            categories = categoryData.categories.orEmpty().associateBy { it.id },
            paymentMethods = paymentData.paymentMethods.orEmpty().associateBy { it.id },
            members = memberData.members,
        )
    }

    val uiState: StateFlow<MovementsUiState> = combine(
        monthData,
        catalogs,
        month,
        filter,
        refreshState,
    ) { data, catalogs, shown, filter, refresh -> buildState(data, catalogs, shown, filter, refresh) }
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

    fun selectFilter(selected: MovementFilter) {
        filter.value = selected
    }

    private fun showMonth(shown: YearMonth) {
        month.value = shown
        refreshMonth(shown)
    }

    private fun canGoNext(shown: YearMonth) = shown.isBefore(dates.currentMonth())

    private fun refreshAll() {
        refreshMonth(month.value)
        // Los nombres del listado salen de estos tres recursos; sus fallos no impiden ver los movimientos.
        viewModelScope.launch { categories.refresh(familyId) }
        viewModelScope.launch { paymentMethods.refresh(familyId) }
        viewModelScope.launch { members.refresh(familyId) }
    }

    private fun refreshMonth(shown: YearMonth) {
        if (refreshJobs[shown]?.isActive == true) return
        refreshJobs[shown] = viewModelScope.launch {
            refreshState.update { it.copy(inFlight = it.inFlight + shown) }
            val result = try {
                movements.refresh(familyId, shown)
            } catch (e: CancellationException) {
                throw e
            } finally {
                refreshState.update { it.copy(inFlight = it.inFlight - shown) }
            }
            refreshState.update { it.copy(results = it.results + (shown to result)) }
            if ((result as? MovementResult.Failure)?.failure == MovementFailure.NotAllowed) onInsufficientRole()
        }
    }

    private fun initialState() = MovementsUiState(
        month = month.value,
        canGoNext = canGoNext(month.value),
        filter = MovementFilter.ALL,
        list = MovementsListState.Loading,
    )

    private fun buildState(
        data: MonthData,
        catalogs: Catalogs,
        shown: YearMonth,
        filter: MovementFilter,
        refresh: RefreshState,
    ): MovementsUiState {
        val list = when {
            // Al cambiar de mes el flujo del anterior puede emitir una vez más: no se mezclan.
            data.month != shown -> MovementsListState.Loading
            else -> buildList(data.data, catalogs, filter, refresh, shown)
        }
        return MovementsUiState(shown, canGoNext(shown), filter, list)
    }

    private fun buildList(
        data: MovementsData,
        catalogs: Catalogs,
        filter: MovementFilter,
        refresh: RefreshState,
        shown: YearMonth,
    ): MovementsListState {
        val all = data.movements ?: return when {
            shown in refresh.inFlight || shown !in refresh.results -> MovementsListState.Loading
            refresh.results.getValue(shown) is MovementResult.Failure -> MovementsListState.Error
            else -> MovementsListState.Loading
        }
        // Monedas mezcladas (la de la familia cambió con datos guardados): no se suma, se trata como sin datos.
        val currencies = all.map { it.amount.currency }.distinct()
        if (currencies.size > 1) return MovementsListState.Error
        val moneyCurrency = currencies.singleOrNull() ?: currency
        val visible = all.filter { filter.accepts(it.type) }
        return MovementsListState.Content(
            summary = moneyCurrency?.let { summaryOf(all, it) },
            groups = groupsOf(visible, catalogs, moneyCurrency),
            isMonthEmpty = all.isEmpty(),
            isFilterEmpty = all.isNotEmpty() && visible.isEmpty(),
            isStale = data.isStale,
            isRefreshing = shown in refresh.inFlight,
        )
    }

    private fun summaryOf(all: List<Movement>, currency: String): MonthSummary {
        val income = all.filter { it.type == MovementType.INCOME }.map { it.amount }.sum(currency)
        val expenses = all.filter { it.type == MovementType.EXPENSE }.map { it.amount }.sum(currency)
        return MonthSummary(income, expenses, income - expenses)
    }

    // El repositorio ya entrega los movimientos por día descendente y `createdAt` descendente: se conserva.
    private fun groupsOf(visible: List<Movement>, catalogs: Catalogs, currency: String?): List<DayGroup> =
        visible.groupBy { it.date }.map { (date, movements) ->
            DayGroup(
                date = date,
                subtotal = movements.fold(Money.zero(checkNotNull(currency))) { total, m ->
                    if (m.type == MovementType.INCOME) total + m.amount else total - m.amount
                },
                rows = movements.map { rowOf(it, catalogs) },
            )
        }

    private fun rowOf(movement: Movement, catalogs: Catalogs): MovementRow {
        val category = catalogs.categories[movement.categoryId]
        return MovementRow(
            id = movement.id,
            type = movement.type,
            title = movement.title,
            amount = movement.amount,
            categoryName = category?.name,
            categoryIcon = category?.icon,
            tagName = movement.tagId?.let { id -> category?.tags?.firstOrNull { it.id == id }?.name },
            paymentMethodName = movement.paymentMethodId?.let { catalogs.paymentMethods[it]?.name },
            recordedBy = resolveMemberLabel(movement.recordedBy, currentUserId, catalogs.members),
        )
    }

    private data class MonthData(val month: YearMonth, val data: MovementsData)

    private data class Catalogs(
        val categories: Map<String, Category>,
        val paymentMethods: Map<String, PaymentMethod>,
        val members: List<FamilyMember>?,
    )

    private data class RefreshState(
        val inFlight: Set<YearMonth> = emptySet(),
        val results: Map<YearMonth, MovementResult<Unit>> = emptyMap(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
