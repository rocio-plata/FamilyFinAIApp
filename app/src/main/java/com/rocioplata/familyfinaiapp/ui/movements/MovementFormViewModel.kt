package com.rocioplata.familyfinaiapp.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.money.MoneyInput
import com.rocioplata.familyfinaiapp.domain.movements.CategoryChange
import com.rocioplata.familyfinaiapp.domain.movements.DateError
import com.rocioplata.familyfinaiapp.domain.movements.FieldValidation
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementChanges
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementEditResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementValidation
import com.rocioplata.familyfinaiapp.domain.movements.MovementsRepository
import com.rocioplata.familyfinaiapp.domain.movements.NewMovement
import com.rocioplata.familyfinaiapp.domain.movements.NoteUpdate
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Formulario de un movimiento: alta (RF-05 a RF-07, RF-14, RF-16) o, si se pasa [movementId], edición de uno
 * existente (RF-09, RF-10). Todo lo escrito vive aquí y sobrevive a la recreación; se crea con una clave de la
 * ruta, así que abrir otro formulario nunca reutiliza el estado de este. Una sola operación de guardado a la
 * vez (CA-18): `isSaving` se fija antes de lanzar la corrutina.
 *
 * En edición el tipo es fijo, se parte de los datos del movimiento y solo se envía lo que cambió; el
 * movimiento original sirve de referencia para detectar cambios y para el descarte.
 */
class MovementFormViewModel(
    private val movements: MovementsRepository,
    private val categories: CategoriesRepository,
    private val paymentMethods: PaymentMethodsRepository,
    private val familyId: String,
    /** Moneda de la familia; si falta o no es válida no se puede guardar un alta (RF-05). */
    currency: String?,
    private val dates: MovementDates,
    /** Cierra el formulario: guardado con éxito, descarte confirmado o cierre sin cambios. */
    private val onClose: () -> Unit,
    /** El backend dijo que el usuario ya no puede operar en esta familia: que la pantalla la reevalúe. */
    private val onInsufficientRole: () -> Unit = {},
    /** La moneda de la familia cambió: que se vuelva a consultar la cabecera. */
    private val onCurrencyMismatch: () -> Unit = {},
    /** Movimiento que se edita; nulo en el alta. */
    private val movementId: String? = null,
) : ViewModel() {
    private val familyCurrency: String? = currency?.takeIf { Money.isKnown(it) }
    private val isEdit = movementId != null
    private val today: LocalDate = dates.today()

    private val fields = MutableStateFlow(Fields(date = today))

    // Copias síncronas de los flujos de los repositorios: al guardar o cerrar se valida contra el estado
    // actual, no contra el último `uiState` publicado, que se recalcula de forma asíncrona.
    private val categoryData = MutableStateFlow(CategoriesData())
    private val paymentData = MutableStateFlow(PaymentMethodsData())
    private val lastUsedPaymentMethodId = MutableStateFlow<String?>(null)
    private val optionsRefreshFailed = MutableStateFlow(false)

    val uiState: StateFlow<MovementFormUiState> = combine(
        fields,
        categoryData,
        paymentData,
        lastUsedPaymentMethodId,
        optionsRefreshFailed,
    ) { fields, categoryData, paymentData, lastUsed, refreshFailed ->
        buildState(fields, categoryData, paymentData, lastUsed, refreshFailed)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        buildState(fields.value, CategoriesData(), PaymentMethodsData(), null, false),
    )

    init {
        viewModelScope.launch { categories.categories(familyId).collect { categoryData.value = it } }
        viewModelScope.launch { paymentMethods.paymentMethods(familyId).collect { paymentData.value = it } }
        refreshOptions()
        if (movementId == null) {
            viewModelScope.launch { lastUsedPaymentMethodId.value = movements.lastUsedPaymentMethodId(familyId) }
        } else {
            viewModelScope.launch { loadMovement(movementId) }
        }
    }

    /** Vuelve a pedir categorías y medios de pago (al fallar su carga o tras un aviso de no disponible). */
    fun retryOptions() = refreshOptions()

    /** El tipo no se puede cambiar al editar (RF-09). */
    fun onTypeChange(type: MovementType) {
        if (isEdit) return
        edit {
            if (it.type == type) it
            else it.copy(type = type, categoryId = null, tagId = null, errors = it.errors.copy(category = false))
        }
    }

    fun onAmountChange(text: String) {
        edit { it.copy(amountText = MoneyInput.filter(text, fractionDigits(it.original)), errors = it.errors.copy(amount = null)) }
    }

    fun onTitleChange(text: String) {
        edit { it.copy(title = text.take(MovementValidation.MAX_TITLE_LENGTH), errors = it.errors.copy(title = null)) }
    }

    fun onNoteChange(text: String) {
        edit { it.copy(note = text.take(MovementValidation.MAX_NOTE_LENGTH), errors = it.errors.copy(note = null)) }
    }

    /**
     * Elegir otra categoría quita la etiqueta elegida (RF-06). Solo se aceptan las que ofrece el formulario:
     * activas del tipo, y en edición también la actual aunque esté archivada (RF-10).
     */
    fun onCategorySelect(categoryId: String) {
        if (currentState().categories.none { it.id == categoryId }) return
        edit {
            if (it.categoryId == categoryId) it
            else it.copy(categoryId = categoryId, tagId = null, errors = it.errors.copy(category = false))
        }
    }

    /** Una etiqueta o ninguna: tocar la elegida, o pasar `null`, la quita (RF-06). Solo las que ofrece el formulario. */
    fun onTagSelect(tagId: String?) {
        if (tagId != null && currentState().tags.none { it.id == tagId }) return
        edit { it.copy(tagId = if (tagId == it.tagId) null else tagId) }
    }

    /** Solo los medios que ofrece el formulario: activos y, en edición, el actual aunque esté archivado (RF-09). */
    fun onPaymentMethodSelect(paymentMethodId: String) {
        if (currentState().paymentMethods.none { it.id == paymentMethodId }) return
        edit { it.copy(paymentMethodId = paymentMethodId, errors = it.errors.copy(paymentMethod = false)) }
    }

    fun onToday() = onDateSelect(today)

    fun onYesterday() = onDateSelect(today.minusDays(1))

    /** Un día futuro se rechaza y se avisa junto a la fecha; la anterior se conserva (RF-16). */
    fun onDateSelect(date: LocalDate) {
        edit {
            if (date.isAfter(today)) it.copy(errors = it.errors.copy(date = DateError.FUTURE))
            else it.copy(date = date, errors = it.errors.copy(date = null))
        }
    }

    /**
     * Valida todo y guarda. Con algún campo inválido no llama al backend y deja el aviso junto a cada campo
     * afectado (CA-06). En edición envía solo lo modificado y, sin cambios, cierra sin llamar (RF-09). Una sola
     * operación a la vez: `isSaving` se fija antes de lanzar la corrutina.
     */
    fun save() {
        val current = fields.value
        if (current.isSaving || current.confirmingDiscard || (isEdit && current.original == null)) return
        val valid = validate(current, currentState()) ?: return
        val original = current.original
        if (original == null) {
            val request = valid.toNewMovement()
            if (fields.compareAndSetSaving(current)) launchSave { onCreateResult(movements.createMovement(familyId, request)) }
            return
        }
        val changes = changesOf(valid, original)
        if (changes.isEmpty) {
            onClose()
            return
        }
        if (fields.compareAndSetSaving(current)) {
            launchSave { onEditResult(movements.editMovement(familyId, checkNotNull(movementId), changes), changes) }
        }
    }

    /** Atrás, la "X" y cualquier salida pasan por aquí. Con cambios pide confirmar el descarte (CA-19). */
    fun requestClose() {
        val current = fields.value
        if (current.isSaving || current.confirmingDiscard) return
        if (hasChanges(current)) fields.update { it.copy(confirmingDiscard = true) } else onClose()
    }

    fun confirmDiscard() {
        if (fields.value.confirmingDiscard && !fields.value.isSaving) onClose()
    }

    /** Seguir en el formulario con lo escrito. */
    fun dismissDiscard() = fields.update { it.copy(confirmingDiscard = false) }

    fun consumeNotice() = fields.update { it.copy(notice = null) }

    private suspend fun loadMovement(id: String) {
        val movement = movements.movement(familyId, id).first()
        // Se abre siempre desde el detalle, cuyo mes está en memoria; si no estuviera, no hay nada que editar.
        if (movement == null) {
            onClose()
            return
        }
        fields.value = Fields(
            type = movement.type,
            amountText = MoneyInput.format(movement.amount.amountMinor, fractionDigits(movement)),
            title = movement.title,
            categoryId = movement.categoryId,
            tagId = movement.tagId,
            paymentMethodId = movement.paymentMethodId,
            date = movement.date,
            note = movement.note.orEmpty(),
            original = movement,
        )
    }

    // Mientras se guarda, o mientras se lee el movimiento que se edita, el formulario no cambia.
    private fun edit(transform: (Fields) -> Fields) {
        fields.update { if (it.isSaving || (isEdit && it.original == null)) it else transform(it) }
    }

    private fun MutableStateFlow<Fields>.compareAndSetSaving(expected: Fields): Boolean = compareAndSet(
        expected,
        expected.copy(isSaving = true, notice = null, savedPartially = false, errors = MovementFormErrors()),
    )

    private fun launchSave(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                fields.update { it.copy(isSaving = false) }
                throw e
            }
        }
    }

    // Moneda efectiva: la de la familia o, al editar una familia sin ella, la del propio movimiento.
    private fun currencyFor(original: Movement?): String? =
        familyCurrency ?: original?.amount?.currency?.takeIf { Money.isKnown(it) }

    private fun fractionDigits(original: Movement?): Int = currencyFor(original)?.let { Money.fractionDigitsOf(it) } ?: 0

    private fun validate(current: Fields, state: MovementFormUiState): Valid? {
        // Sin moneda válida no hay nada que guardar: la pantalla lo explica y deshabilita "Guardar" (RF-05).
        val currency = currencyFor(current.original) ?: return null
        val amount = MovementValidation.validateAmount(MoneyInput.parse(current.amountText, fractionDigits(current.original)))
        val title = MovementValidation.validateTitle(current.title)
        val note = MovementValidation.validateNote(current.note)
        val date = MovementValidation.validateDate(current.date, today)
        val categoryId = state.categoryId
        val paymentMethodId = state.paymentMethodId
        val errors = MovementFormErrors(
            amount = (amount as? FieldValidation.Invalid)?.error,
            title = (title as? FieldValidation.Invalid)?.error,
            category = categoryId == null,
            // Al editar, un movimiento sin medio de pago (el backend puede no devolverlo) se guarda sin él.
            paymentMethod = !isEdit && paymentMethodId == null,
            note = (note as? FieldValidation.Invalid)?.error,
            date = (date as? FieldValidation.Invalid)?.error,
        )
        if (!errors.isEmpty) {
            fields.update { it.copy(errors = errors) }
            return null
        }
        return Valid(
            amount = Money((amount as FieldValidation.Valid).value, currency),
            title = (title as FieldValidation.Valid).value,
            note = (note as FieldValidation.Valid).value,
            date = (date as FieldValidation.Valid).value,
            categoryId = checkNotNull(categoryId),
            tagId = state.tagId,
            paymentMethodId = paymentMethodId,
        )
    }

    private fun Valid.toNewMovement() = NewMovement(
        amount = amount,
        categoryId = categoryId,
        tagId = tagId,
        paymentMethodId = checkNotNull(paymentMethodId),
        title = title,
        note = note,
        date = date,
    )

    // Solo lo modificado respecto al original (RF-09, RF-10): la fecha solo si cambió el día.
    private fun changesOf(valid: Valid, original: Movement) = MovementChanges(
        amount = valid.amount.takeIf { it.amountMinor != original.amount.amountMinor },
        title = valid.title.takeIf { it != original.title },
        note = when {
            valid.note == original.note -> NoteUpdate.Unchanged
            valid.note == null -> NoteUpdate.Remove
            else -> NoteUpdate.Set(valid.note)
        },
        date = valid.date.takeIf { it != original.date },
        paymentMethodId = valid.paymentMethodId?.takeIf { it != original.paymentMethodId },
        category = CategoryChange(valid.categoryId, valid.tagId)
            .takeIf { valid.categoryId != original.categoryId || valid.tagId != original.tagId },
    )

    private fun onCreateResult(result: MovementResult<*>) {
        when (result) {
            is MovementResult.Success -> {
                fields.update { it.copy(isSaving = false) }
                onClose()
            }
            is MovementResult.Failure -> onSaveFailed(result.failure)
        }
    }

    private fun onEditResult(result: MovementEditResult, changes: MovementChanges) {
        when (result) {
            MovementEditResult.Success -> {
                fields.update { it.copy(isSaving = false) }
                onClose()
            }
            is MovementEditResult.Failure ->
                if (result.failure == MovementFailure.NotFound) onMovementGone() else onSaveFailed(result.failure)
            // Los datos se guardaron y la categoría no: lo guardado pasa a ser el nuevo original, así que un
            // nuevo guardado solo envía lo pendiente (RF-10).
            is MovementEditResult.Partial -> {
                fields.update { it.copy(original = it.original?.applying(changes), savedPartially = true) }
                onSaveFailed(result.failure)
            }
        }
    }

    // El movimiento ya no existe: se vuelve al detalle, que se informa de ello, y se refresca su mes (CA-22).
    private fun onMovementGone() {
        fields.value.original?.let { original ->
            viewModelScope.launch { movements.refresh(familyId, YearMonth.from(original.date)) }
        }
        fields.update { it.copy(isSaving = false) }
        onClose()
    }

    private fun Movement.applying(changes: MovementChanges) = copy(
        amount = changes.amount ?: amount,
        title = changes.title ?: title,
        note = when (val update = changes.note) {
            NoteUpdate.Unchanged -> note
            NoteUpdate.Remove -> null
            is NoteUpdate.Set -> update.text
        },
        date = changes.date ?: date,
        paymentMethodId = changes.paymentMethodId ?: paymentMethodId,
    )

    // Ningún fallo borra lo escrito (RF-14, CA-22): se informa, se quita solo lo que ya no existe (en edición,
    // se vuelve a lo que tenía el movimiento) y se actualizan las opciones.
    private fun onSaveFailed(failure: MovementFailure) {
        fields.update { current ->
            val original = current.original
            val cleared = when (failure) {
                MovementFailure.CategoryUnavailable ->
                    current.copy(categoryId = original?.categoryId, tagId = original?.tagId)
                MovementFailure.TagUnavailable ->
                    current.copy(tagId = original?.tagId?.takeIf { current.categoryId == original.categoryId })
                MovementFailure.PaymentMethodUnavailable -> current.copy(paymentMethodId = original?.paymentMethodId)
                MovementFailure.FutureDate ->
                    current.copy(errors = current.errors.copy(date = DateError.FUTURE))
                else -> current
            }
            cleared.copy(isSaving = false, notice = failure)
        }
        when (failure) {
            MovementFailure.CategoryUnavailable, MovementFailure.TagUnavailable, MovementFailure.TypeMismatch ->
                refreshCategories()
            MovementFailure.PaymentMethodUnavailable -> {
                lastUsedPaymentMethodId.value = null
                refreshPaymentMethods()
            }
            MovementFailure.CurrencyMismatch -> onCurrencyMismatch()
            MovementFailure.NotAllowed -> onInsufficientRole()
            else -> Unit
        }
    }

    private fun currentState() = buildState(
        fields.value, categoryData.value, paymentData.value, lastUsedPaymentMethodId.value, optionsRefreshFailed.value,
    )

    private fun hasChanges(current: Fields): Boolean {
        val original = current.original
        if (isEdit) {
            // El descarte compara con el movimiento original, no con un formulario vacío (CA-19).
            if (original == null) return false
            return MoneyInput.parse(current.amountText, fractionDigits(original)) != original.amount.amountMinor ||
                current.title.trim() != original.title || current.note.trim() != original.note.orEmpty() ||
                current.date != original.date || current.categoryId != original.categoryId ||
                current.tagId != original.tagId || current.paymentMethodId != original.paymentMethodId
        }
        val proposal = proposedPaymentMethod(lastUsedPaymentMethodId.value, currentState().paymentMethods)
        return current.amountText.isNotEmpty() || current.title.isNotBlank() || current.note.isNotBlank() ||
            current.type != MovementType.EXPENSE || current.categoryId != null || current.date != today ||
            (current.paymentMethodId != null && current.paymentMethodId != proposal)
    }

    private fun refreshOptions() {
        optionsRefreshFailed.value = false
        refreshCategories()
        refreshPaymentMethods()
    }

    private fun refreshCategories() {
        viewModelScope.launch {
            if (categories.refresh(familyId) is CategoryResult.Failure) optionsRefreshFailed.value = true
        }
    }

    private fun refreshPaymentMethods() {
        viewModelScope.launch {
            if (paymentMethods.refresh(familyId) is PaymentMethodResult.Failure) optionsRefreshFailed.value = true
        }
    }

    private fun proposedPaymentMethod(lastUsed: String?, active: List<PaymentMethod>) =
        lastUsed?.takeIf { id -> active.any { it.id == id } }

    private fun buildState(
        fields: Fields,
        categoryData: CategoriesData,
        paymentData: PaymentMethodsData,
        lastUsed: String?,
        refreshFailed: Boolean,
    ): MovementFormUiState {
        val original = fields.original
        val categoryType = if (fields.type == MovementType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
        // Activas del tipo y, al editar, la categoría actual del movimiento aunque esté archivada (RF-10).
        val availableCategories = categoryData.categories?.filter {
            it.type == categoryType && (it.status == CategoryStatus.ACTIVE || it.id == original?.categoryId)
        }.orEmpty()
        val category = availableCategories.firstOrNull { it.id == fields.categoryId }
        val tags = category?.tags
            ?.filter { it.isOffered(original, category.id) }
            ?.sortedBy { it.displayOrder }
            .orEmpty()
        val offeredMethods = paymentData.paymentMethods?.filter {
            it.status == PaymentMethodStatus.ACTIVE || it.id == original?.paymentMethodId
        }.orEmpty()
        val loaded = categoryData.categories != null && paymentData.paymentMethods != null
        return MovementFormUiState(
            type = fields.type,
            amountText = fields.amountText,
            currency = currencyFor(original),
            title = fields.title,
            categories = availableCategories,
            categoryId = category?.id,
            tags = tags,
            tagId = fields.tagId?.takeIf { id -> tags.any { it.id == id } },
            paymentMethods = offeredMethods,
            // Lo elegido solo vale si se ofrece; en el alta, si no, se propone el último usado (RF-07).
            paymentMethodId = fields.paymentMethodId?.takeIf { id -> offeredMethods.any { it.id == id } }
                ?: if (isEdit) null else proposedPaymentMethod(lastUsed, offeredMethods),
            date = fields.date,
            today = today,
            note = fields.note,
            errors = fields.errors,
            options = when {
                isEdit && original == null -> FormOptionsState.Loading
                loaded -> FormOptionsState.Ready
                refreshFailed -> FormOptionsState.Error
                else -> FormOptionsState.Loading
            },
            noCategories = categoryData.categories != null && availableCategories.isEmpty(),
            noPaymentMethods = paymentData.paymentMethods != null && offeredMethods.isEmpty(),
            isSaving = fields.isSaving,
            confirmingDiscard = fields.confirmingDiscard,
            notice = fields.notice,
            isEditing = isEdit,
            isLoadingMovement = isEdit && original == null,
            savedPartially = fields.savedPartially,
        )
    }

    // Una etiqueta se ofrece si está activa o, al editar, si es la actual del movimiento en su categoría.
    private fun Tag.isOffered(original: Movement?, categoryId: String) =
        status == TagStatus.ACTIVE || (original != null && original.categoryId == categoryId && original.tagId == id)

    /** Lo ya validado y normalizado del formulario, listo para convertirse en alta o en cambios. */
    private class Valid(
        val amount: Money,
        val title: String,
        val note: String?,
        val date: LocalDate,
        val categoryId: String,
        val tagId: String?,
        val paymentMethodId: String?,
    )

    private data class Fields(
        val type: MovementType = MovementType.EXPENSE,
        val amountText: String = "",
        val title: String = "",
        val categoryId: String? = null,
        val tagId: String? = null,
        /** Lo que eligió el usuario; en el alta, sin elegir, el estado propone el último usado (RF-07). */
        val paymentMethodId: String? = null,
        val date: LocalDate,
        val note: String = "",
        val errors: MovementFormErrors = MovementFormErrors(),
        val isSaving: Boolean = false,
        val confirmingDiscard: Boolean = false,
        val notice: MovementFailure? = null,
        /** El movimiento que se edita tal como está guardado (con lo ya guardado de un resultado parcial). */
        val original: Movement? = null,
        val savedPartially: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
