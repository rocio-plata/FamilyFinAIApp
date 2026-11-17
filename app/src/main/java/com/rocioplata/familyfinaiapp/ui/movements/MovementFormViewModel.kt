package com.rocioplata.familyfinaiapp.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.money.MoneyInput
import com.rocioplata.familyfinaiapp.domain.movements.FieldValidation
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementValidation
import com.rocioplata.familyfinaiapp.domain.movements.MovementsRepository
import com.rocioplata.familyfinaiapp.domain.movements.NewMovement
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import com.rocioplata.familyfinaiapp.domain.movements.DateError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Formulario de alta de un movimiento (RF-05 a RF-07, RF-14, RF-16). Todo lo escrito vive aquí y sobrevive a
 * la recreación; se crea con una clave de la ruta, así que abrir otro formulario nunca reutiliza el estado de
 * este. Una sola operación de guardado a la vez (CA-18): `isSaving` se fija antes de lanzar la corrutina.
 */
class MovementFormViewModel(
    private val movements: MovementsRepository,
    private val categories: CategoriesRepository,
    private val paymentMethods: PaymentMethodsRepository,
    private val familyId: String,
    /** Moneda de la familia; si falta o no es válida no se puede guardar (RF-05). */
    currency: String?,
    private val dates: MovementDates,
    /** Cierra el formulario: guardado con éxito, descarte confirmado o cierre sin cambios. */
    private val onClose: () -> Unit,
    /** El backend dijo que el usuario ya no puede operar en esta familia: que la pantalla la reevalúe. */
    private val onInsufficientRole: () -> Unit = {},
    /** La moneda de la familia cambió: que se vuelva a consultar la cabecera. */
    private val onCurrencyMismatch: () -> Unit = {},
) : ViewModel() {
    private val currency: String? = currency?.takeIf { Money.isKnown(it) }
    private val fractionDigits: Int = this.currency?.let { Money.fractionDigitsOf(it) } ?: 0
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
        viewModelScope.launch { lastUsedPaymentMethodId.value = movements.lastUsedPaymentMethodId(familyId) }
    }

    /** Vuelve a pedir categorías y medios de pago (al fallar su carga o tras un aviso de no disponible). */
    fun retryOptions() = refreshOptions()

    fun onTypeChange(type: MovementType) {
        edit {
            if (it.type == type) it
            else it.copy(type = type, categoryId = null, tagId = null, errors = it.errors.copy(category = false))
        }
    }

    fun onAmountChange(text: String) {
        edit { it.copy(amountText = MoneyInput.filter(text, fractionDigits), errors = it.errors.copy(amount = null)) }
    }

    fun onTitleChange(text: String) {
        edit { it.copy(title = text.take(MovementValidation.MAX_TITLE_LENGTH), errors = it.errors.copy(title = null)) }
    }

    fun onNoteChange(text: String) {
        edit { it.copy(note = text.take(MovementValidation.MAX_NOTE_LENGTH), errors = it.errors.copy(note = null)) }
    }

    /** Elegir otra categoría quita la etiqueta elegida (RF-06). */
    fun onCategorySelect(categoryId: String) {
        edit {
            if (it.categoryId == categoryId) it
            else it.copy(categoryId = categoryId, tagId = null, errors = it.errors.copy(category = false))
        }
    }

    /** Una etiqueta o ninguna: tocar la elegida, o pasar `null`, la quita (RF-06). */
    fun onTagSelect(tagId: String?) {
        edit { it.copy(tagId = if (tagId == it.tagId) null else tagId) }
    }

    fun onPaymentMethodSelect(paymentMethodId: String) {
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
     * afectado (CA-06). Una sola operación a la vez: `isSaving` se fija antes de lanzar la corrutina.
     */
    fun save() {
        val current = fields.value
        if (current.isSaving || current.confirmingDiscard) return
        val request = validate(current, currentState()) ?: return
        if (!fields.compareAndSetSaving(current)) return
        viewModelScope.launch {
            val result = try {
                movements.createMovement(familyId, request)
            } catch (e: CancellationException) {
                fields.update { it.copy(isSaving = false) }
                throw e
            }
            onSaveResult(result)
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

    // Mientras se guarda, el formulario no cambia.
    private fun edit(transform: (Fields) -> Fields) {
        fields.update { if (it.isSaving) it else transform(it) }
    }

    private fun MutableStateFlow<Fields>.compareAndSetSaving(expected: Fields): Boolean =
        compareAndSet(expected, expected.copy(isSaving = true, notice = null, errors = MovementFormErrors()))

    private fun validate(current: Fields, state: MovementFormUiState): NewMovement? {
        // Sin moneda válida no hay nada que guardar: la pantalla lo explica y deshabilita "Guardar" (RF-05).
        if (currency == null) return null
        val amount = MovementValidation.validateAmount(MoneyInput.parse(current.amountText, fractionDigits))
        val title = MovementValidation.validateTitle(current.title)
        val note = MovementValidation.validateNote(current.note)
        val date = MovementValidation.validateDate(current.date, today)
        val categoryId = state.categoryId
        val paymentMethodId = state.paymentMethodId
        val errors = MovementFormErrors(
            amount = (amount as? FieldValidation.Invalid)?.error,
            title = (title as? FieldValidation.Invalid)?.error,
            category = categoryId == null,
            paymentMethod = paymentMethodId == null,
            note = (note as? FieldValidation.Invalid)?.error,
            date = (date as? FieldValidation.Invalid)?.error,
        )
        if (!errors.isEmpty) {
            fields.update { it.copy(errors = errors) }
            return null
        }
        return NewMovement(
            amount = Money((amount as FieldValidation.Valid).value, currency),
            categoryId = checkNotNull(categoryId),
            tagId = state.tagId,
            paymentMethodId = checkNotNull(paymentMethodId),
            title = (title as FieldValidation.Valid).value,
            note = (note as FieldValidation.Valid).value,
            date = (date as FieldValidation.Valid).value,
        )
    }

    private fun onSaveResult(result: MovementResult<*>) {
        when (result) {
            is MovementResult.Success -> {
                fields.update { it.copy(isSaving = false) }
                onClose()
            }
            is MovementResult.Failure -> onSaveFailed(result.failure)
        }
    }

    // Ningún fallo borra lo escrito (RF-14, CA-22): se informa, se quita solo lo que ya no existe y se
    // actualizan las opciones.
    private fun onSaveFailed(failure: MovementFailure) {
        fields.update { current ->
            val cleared = when (failure) {
                MovementFailure.CategoryUnavailable -> current.copy(categoryId = null, tagId = null)
                MovementFailure.TagUnavailable -> current.copy(tagId = null)
                MovementFailure.PaymentMethodUnavailable -> current.copy(paymentMethodId = null)
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
        val categoryType = if (fields.type == MovementType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
        val availableCategories = categoryData.categories?.filter {
            it.status == CategoryStatus.ACTIVE && it.type == categoryType
        }.orEmpty()
        val category = availableCategories.firstOrNull { it.id == fields.categoryId }
        val tags = category.activeTags()
        val activeMethods = paymentData.paymentMethods?.filter { it.status == PaymentMethodStatus.ACTIVE }.orEmpty()
        val loaded = categoryData.categories != null && paymentData.paymentMethods != null
        return MovementFormUiState(
            type = fields.type,
            amountText = fields.amountText,
            currency = currency,
            title = fields.title,
            categories = availableCategories,
            categoryId = category?.id,
            tags = tags,
            tagId = fields.tagId?.takeIf { id -> tags.any { it.id == id } },
            paymentMethods = activeMethods,
            // Lo elegido solo vale si sigue activo; si no, se propone el último usado (RF-07).
            paymentMethodId = fields.paymentMethodId?.takeIf { id -> activeMethods.any { it.id == id } }
                ?: proposedPaymentMethod(lastUsed, activeMethods),
            date = fields.date,
            today = today,
            note = fields.note,
            errors = fields.errors,
            options = when {
                loaded -> FormOptionsState.Ready
                refreshFailed -> FormOptionsState.Error
                else -> FormOptionsState.Loading
            },
            noCategories = categoryData.categories != null && availableCategories.isEmpty(),
            noPaymentMethods = paymentData.paymentMethods != null && activeMethods.isEmpty(),
            isSaving = fields.isSaving,
            confirmingDiscard = fields.confirmingDiscard,
            notice = fields.notice,
        )
    }

    private fun Category?.activeTags() =
        this?.tags?.filter { it.status == TagStatus.ACTIVE }?.sortedBy { it.displayOrder }.orEmpty()

    private data class Fields(
        val type: MovementType = MovementType.EXPENSE,
        val amountText: String = "",
        val title: String = "",
        val categoryId: String? = null,
        val tagId: String? = null,
        /** Lo que eligió el usuario; sin elegir, el estado propone el último usado (RF-07). */
        val paymentMethodId: String? = null,
        val date: LocalDate,
        val note: String = "",
        val errors: MovementFormErrors = MovementFormErrors(),
        val isSaving: Boolean = false,
        val confirmingDiscard: Boolean = false,
        val notice: MovementFailure? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
