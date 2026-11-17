package com.rocioplata.familyfinaiapp.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementsRepository
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Detalle de un movimiento de la familia (RF-08, RF-11, RF-15). Observa el movimiento en los meses cargados
 * (el backend no tiene consulta individual, BG-06) y lo resuelve con categorías, etiquetas, medios de pago
 * (también archivados) y miembros. Se crea con una clave de la ruta, así que abrir otro detalle nunca
 * reutiliza el estado de éste. Una sola operación de eliminar a la vez (CA-18): `isDeleting` se fija antes
 * de lanzar la corrutina.
 */
class MovementDetailViewModel(
    private val movements: MovementsRepository,
    private val categories: CategoriesRepository,
    private val paymentMethods: PaymentMethodsRepository,
    private val members: FamilyMembersRepository,
    private val familyId: String,
    private val movementId: String,
    /** Usuario de la sesión, para marcar "Tú" (RF-15). */
    private val currentUserId: String?,
    /** Cierra el detalle: eliminado con éxito o el usuario vuelve al listado. Se llama como mucho una vez. */
    private val onClose: () -> Unit,
    /** El backend dijo que el usuario ya no puede operar en esta familia: que la pantalla la reevalúe. */
    private val onInsufficientRole: () -> Unit = {},
) : ViewModel() {
    private val ops = MutableStateFlow(Ops())
    private var closed = false

    // Mes del último valor conocido del movimiento: es el que se refresca si el backend dice que ya no existe.
    private var lastKnownMonth: YearMonth? = null

    private val catalogs = combine(
        categories.categories(familyId),
        paymentMethods.paymentMethods(familyId),
        members.members(familyId),
    ) { categoryData, paymentData, memberData -> Catalogs(categoryData, paymentData, memberData) }

    val uiState: StateFlow<MovementDetailUiState> = combine(
        movements.movement(familyId, movementId),
        catalogs,
        ops,
    ) { movement, catalogs, ops -> buildState(movement, catalogs, ops) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MovementDetailUiState.Loading)

    init {
        // Los nombres del detalle salen de estos tres recursos; sus fallos no impiden ver el movimiento.
        viewModelScope.launch { categories.refresh(familyId) }
        viewModelScope.launch { paymentMethods.refresh(familyId) }
        viewModelScope.launch { members.refresh(familyId) }
        // Si el movimiento desaparece (otro dispositivo, un refresco) ya no se puede eliminar ni mostrar.
        viewModelScope.launch {
            movements.movement(familyId, movementId).collect { movement ->
                if (movement == null) ops.update { it.copy(gone = true) }
            }
        }
    }

    /** Pide confirmación para eliminar (RF-11). Ignorado mientras se elimina. */
    fun requestDelete() = ops.update { if (it.isDeleting || it.gone) it else it.copy(confirmingDelete = true) }

    /** Cancelar: no cambia nada ni llama al backend. */
    fun cancelDelete() = ops.update { if (it.isDeleting) it else it.copy(confirmingDelete = false) }

    /**
     * Confirma la eliminación. Una sola llamada aunque se pulse varias veces: `isDeleting` se fija antes de
     * lanzar la corrutina (CA-18). Si sale bien cierra el detalle.
     */
    fun confirmDelete() {
        val before = ops.getAndUpdate { if (it.confirmingDelete && !it.isDeleting) it.copy(isDeleting = true, notice = null) else it }
        if (!before.confirmingDelete || before.isDeleting) return
        viewModelScope.launch {
            val result = try {
                movements.deleteMovement(familyId, movementId)
            } catch (e: CancellationException) {
                ops.update { it.copy(isDeleting = false) }
                throw e
            }
            onDeleteResult(result)
        }
    }

    /** Atrás, la flecha y "Volver al listado". Ignorado mientras se elimina. */
    fun requestClose() {
        if (ops.value.isDeleting) return
        finish()
    }

    fun consumeNotice() = ops.update { it.copy(notice = null) }

    private fun onDeleteResult(result: MovementResult<Unit>) {
        when (result) {
            is MovementResult.Success -> {
                ops.update { it.copy(isDeleting = false, confirmingDelete = false, deleted = true) }
                finish()
            }
            is MovementResult.Failure -> onDeleteFailed(result.failure)
        }
    }

    // Un fallo no cambia el movimiento: se informa y se puede volver a intentar. Si ya no existe, se informa
    // con el estado `Gone` y se refresca su mes para que el listado quede actualizado (CA-22).
    private fun onDeleteFailed(failure: MovementFailure) {
        if (failure == MovementFailure.NotFound) {
            ops.update { it.copy(isDeleting = false, confirmingDelete = false, gone = true) }
            lastKnownMonth?.let { month -> viewModelScope.launch { movements.refresh(familyId, month) } }
            return
        }
        ops.update { it.copy(isDeleting = false, confirmingDelete = false, notice = failure) }
        if (failure == MovementFailure.NotAllowed) onInsufficientRole()
    }

    private fun finish() {
        if (closed) return
        closed = true
        onClose()
    }

    private fun buildState(movement: Movement?, catalogs: Catalogs, ops: Ops): MovementDetailUiState = when {
        ops.deleted -> MovementDetailUiState.Loading
        ops.gone -> MovementDetailUiState.Gone
        movement == null -> MovementDetailUiState.Gone
        else -> {
            lastKnownMonth = YearMonth.from(movement.date)
            contentOf(movement, catalogs, ops)
        }
    }

    private fun contentOf(movement: Movement, catalogs: Catalogs, ops: Ops): MovementDetailUiState.Content {
        val category = catalogs.categories.categories?.firstOrNull { it.id == movement.categoryId }
        val tag = movement.tagId?.let { id -> category?.tags?.firstOrNull { it.id == id } }
        val method = movement.paymentMethodId?.let { id -> catalogs.paymentMethods.paymentMethods?.firstOrNull { it.id == id } }
        return MovementDetailUiState.Content(
            id = movement.id,
            type = movement.type,
            amount = movement.amount,
            title = movement.title,
            note = movement.note,
            date = movement.date,
            category = ResolvedItem(category?.name, category?.status == CategoryStatus.DEPRECATED),
            categoryIcon = category?.icon,
            tag = movement.tagId?.let { ResolvedItem(tag?.name, tag?.status == TagStatus.DEPRECATED) },
            paymentMethod = movement.paymentMethodId?.let {
                ResolvedItem(method?.name, method?.status == PaymentMethodStatus.DEPRECATED)
            },
            recordedBy = resolveMemberLabel(movement.recordedBy, currentUserId, catalogs.members.members),
            confirmingDelete = ops.confirmingDelete,
            isDeleting = ops.isDeleting,
            notice = ops.notice,
        )
    }

    private data class Catalogs(
        val categories: CategoriesData,
        val paymentMethods: PaymentMethodsData,
        val members: FamilyMembersData,
    )

    private data class Ops(
        val confirmingDelete: Boolean = false,
        val isDeleting: Boolean = false,
        val notice: MovementFailure? = null,
        /** El backend dijo que el movimiento ya no existe. */
        val gone: Boolean = false,
        /** Se eliminó con éxito: se evita mostrar "ya no existe" mientras la pantalla se cierra. */
        val deleted: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
