package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.ColorUpdate
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementChanges
import com.rocioplata.familyfinaiapp.domain.movements.MovementEditResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementsData
import com.rocioplata.familyfinaiapp.domain.movements.MovementsRepository
import com.rocioplata.familyfinaiapp.domain.movements.NewMovement
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.YearMonth

// Repositorios falsos compartidos por los tests del listado y del formulario de movimientos.

internal class FakeMovements : MovementsRepository {
    val data = mutableMapOf<YearMonth, MutableStateFlow<MovementsData>>()
    val refreshed = mutableListOf<YearMonth>()
    var gate: CompletableDeferred<Unit>? = null
    var onRefresh: (YearMonth) -> MovementResult<Unit> = { MovementResult.Success(Unit) }
    val created = mutableListOf<NewMovement>()
    var createGate: CompletableDeferred<Unit>? = null
    var createFailure: MovementFailure? = null
    var lastUsedPaymentMethodId: String? = null

    // Detalle: sin emisión hasta que el test publique el movimiento (o su ausencia con `null`).
    val detail = MutableSharedFlow<Movement?>(replay = 1)
    val deleted = mutableListOf<String>()
    var deleteGate: CompletableDeferred<Unit>? = null
    var deleteFailure: MovementFailure? = null

    // Edición: registra lo enviado y devuelve el resultado configurado (éxito, fallo o parcial).
    data class Edit(val movementId: String, val changes: MovementChanges)
    val edited = mutableListOf<Edit>()
    var editGate: CompletableDeferred<Unit>? = null
    var editFailure: MovementFailure? = null
    var editPartialFailure: MovementFailure? = null

    fun flowOf(month: YearMonth) = data.getOrPut(month) { MutableStateFlow(MovementsData()) }

    override fun movements(familyId: String, month: YearMonth): Flow<MovementsData> = flowOf(month)

    override suspend fun refresh(familyId: String, month: YearMonth): MovementResult<Unit> {
        refreshed += month
        gate?.await()
        return onRefresh(month)
    }

    override fun movement(familyId: String, movementId: String): Flow<Movement?> = detail
    override suspend fun createMovement(familyId: String, movement: NewMovement): MovementResult<Movement> {
        created += movement
        createGate?.await()
        createFailure?.let { return MovementResult.Failure(it) }
        return MovementResult.Success(
            Movement(
                id = "m-new", type = MovementType.EXPENSE, amount = movement.amount, categoryId = movement.categoryId,
                tagId = movement.tagId, paymentMethodId = movement.paymentMethodId, title = movement.title,
                note = movement.note, date = movement.date, recordedBy = "u-1", createdAt = Instant.EPOCH,
            ),
        )
    }
    override suspend fun editMovement(
        familyId: String,
        movementId: String,
        changes: MovementChanges,
    ): MovementEditResult {
        edited += Edit(movementId, changes)
        editGate?.await()
        editFailure?.let { return MovementEditResult.Failure(it) }
        editPartialFailure?.let { return MovementEditResult.Partial(it) }
        return MovementEditResult.Success
    }
    override suspend fun deleteMovement(familyId: String, movementId: String): MovementResult<Unit> {
        deleted += movementId
        deleteGate?.await()
        deleteFailure?.let { return MovementResult.Failure(it) }
        detail.tryEmit(null)
        return MovementResult.Success(Unit)
    }
    override suspend fun lastUsedPaymentMethodId(familyId: String): String? = lastUsedPaymentMethodId
    override suspend fun clear() = Unit
}

internal class FakeCategories : CategoriesRepository {
    val data = MutableStateFlow(CategoriesData())
    var refreshes = 0
    var refreshFails = false
    override fun categories(familyId: String): Flow<CategoriesData> = data
    override suspend fun refresh(familyId: String): CategoryResult<Unit> {
        refreshes++
        return if (refreshFails) CategoryResult.Failure(CategoryFailure.Network) else CategoryResult.Success(Unit)
    }
    override suspend fun createCategory(familyId: String, type: CategoryType, name: String, icon: String?, color: String?) =
        error("No se usa")
    override suspend fun updateCategory(familyId: String, categoryId: String, newName: String?, icon: IconUpdate, color: ColorUpdate) =
        error("No se usa")
    override suspend fun deprecateCategory(familyId: String, categoryId: String) = error("No se usa")
    override suspend fun deleteCategory(familyId: String, categoryId: String) = error("No se usa")
    override suspend fun addTag(familyId: String, categoryId: String, name: String) = error("No se usa")
    override suspend fun renameTag(familyId: String, categoryId: String, tagId: String, name: String) =
        error("No se usa")
    override suspend fun reorderTags(familyId: String, categoryId: String, activeTagIdsInOrder: List<String>) =
        error("No se usa")
    override suspend fun deprecateTag(familyId: String, categoryId: String, tagId: String) = error("No se usa")
    override suspend fun deleteTag(familyId: String, categoryId: String, tagId: String) = error("No se usa")
    override suspend fun clear() = Unit
}

internal class FakePaymentMethods : PaymentMethodsRepository {
    val data = MutableStateFlow(PaymentMethodsData())
    var refreshes = 0
    var refreshFails = false
    override fun paymentMethods(familyId: String): Flow<PaymentMethodsData> = data
    override suspend fun refresh(familyId: String): PaymentMethodResult<Unit> {
        refreshes++
        return if (refreshFails) PaymentMethodResult.Failure(PaymentMethodFailure.Network)
        else PaymentMethodResult.Success(Unit)
    }
    override suspend fun createPaymentMethod(familyId: String, name: String) = error("No se usa")
    override suspend fun renamePaymentMethod(familyId: String, paymentMethodId: String, name: String) =
        error("No se usa")
    override suspend fun deprecatePaymentMethod(familyId: String, paymentMethodId: String) = error("No se usa")
    override suspend fun deletePaymentMethod(familyId: String, paymentMethodId: String) = error("No se usa")
    override suspend fun clear() = Unit
}

internal class FakeMembers : FamilyMembersRepository {
    val data = MutableStateFlow(FamilyMembersData())
    var refreshes = 0
    override fun members(familyId: String): Flow<FamilyMembersData> = data
    override suspend fun refresh(familyId: String): FamilyMembersResult<Unit> {
        refreshes++
        return FamilyMembersResult.Success(Unit)
    }
    override suspend fun clear() = Unit
}

internal class FakeConnectivity : ConnectivityObserver {
    val flow = MutableSharedFlow<Boolean>(replay = 1)
    override val isOnline: Flow<Boolean> = flow
}
