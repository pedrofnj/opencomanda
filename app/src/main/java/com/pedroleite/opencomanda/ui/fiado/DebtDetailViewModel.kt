package com.pedroleite.opencomanda.ui.fiado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.data.local.entity.DebtPaymentEntity
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.DebtRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which part of the debt payment flow is currently showing. */
enum class DebtDetailPhase { FORM, SUCCESS }

enum class DebtDetailErrorType { ZERO_AMOUNT, OVERPAYMENT, METHOD_REQUIRED, PAYMENT_FAILED }

/** The payment just registered, kept only long enough to show the success screen. */
data class DebtPaymentResult(val amountCents: Long, val remainingAfterCents: Long, val settled: Boolean)

private data class ScreenState(
    val phase: DebtDetailPhase = DebtDetailPhase.FORM,
    val amountCents: Long = 0,
    val paymentMethod: PaymentMethod? = null,
    val isSaving: Boolean = false,
    val error: DebtDetailErrorType? = null,
    val lastPayment: DebtPaymentResult? = null,
)

data class DebtDetailUiState(
    val isLoading: Boolean = true,
    val debt: DebtEntity? = null,
    val customerName: String? = null,
    val orderDisplayName: String? = null,
    val payments: List<DebtPaymentEntity> = emptyList(),
    val phase: DebtDetailPhase = DebtDetailPhase.FORM,
    val amountCents: Long = 0,
    val paymentMethod: PaymentMethod? = null,
    val isSaving: Boolean = false,
    val error: DebtDetailErrorType? = null,
    val lastPayment: DebtPaymentResult? = null,
) {
    val notFound: Boolean get() = !isLoading && debt == null

    val paidCents: Long get() = payments.sumOf { it.amountCents }

    val remainingCents: Long get() = (debt?.originalAmountCents ?: 0L) - paidCents

    val canConfirmPayment: Boolean get() = !isSaving && debt != null
}

/** Backs the Debt detail screen: outstanding balance, payment history, and registering a new
 *  payment — validated the same way [DebtRepository.registerPayment] validates it, so an
 *  invalid amount is rejected here with a clear message before ever reaching the repository. */
class DebtDetailViewModel(
    private val debtId: Long,
    private val debtRepository: DebtRepository,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val screenState = MutableStateFlow(ScreenState())
    private val customerName = MutableStateFlow<String?>(null)
    private val orderDisplayName = MutableStateFlow<String?>(null)

    private val debtFlow = debtRepository.observeDebt(debtId)
    private val paymentsFlow = debtRepository.getPaymentsForDebt(debtId)

    val uiState: StateFlow<DebtDetailUiState> = combine(
        debtFlow,
        paymentsFlow,
        customerName,
        orderDisplayName,
        screenState,
    ) { debt, payments, custName, orderName, screen ->
        DebtDetailUiState(
            isLoading = false,
            debt = debt,
            customerName = custName,
            orderDisplayName = orderName,
            payments = payments,
            phase = screen.phase,
            amountCents = screen.amountCents,
            paymentMethod = screen.paymentMethod,
            isSaving = screen.isSaving,
            error = screen.error,
            lastPayment = screen.lastPayment,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DebtDetailUiState(),
    )

    init {
        // One-shot lookups: the customer and order behind a debt never change, so there's no
        // need to observe them reactively — just resolve each exactly once when the debt loads.
        debtFlow
            .onEach { debt ->
                if (debt == null) return@onEach
                if (customerName.value == null) customerName.value = customerRepository.getById(debt.customerId)?.name
                val orderId = debt.orderId
                if (orderId != null && orderDisplayName.value == null) orderDisplayName.value = orderRepository.getDisplayName(orderId)
            }
            .launchIn(viewModelScope)
    }

    fun onAmountChanged(cents: Long) {
        screenState.update { it.copy(amountCents = cents, error = null) }
    }

    /** Prefills the amount field with the full remaining balance — a convenience for the common
     *  case of settling a debt in one payment, per the "Pagar tudo" action. */
    fun payAll() {
        screenState.update { it.copy(amountCents = uiState.value.remainingCents, error = null) }
    }

    fun onPaymentMethodSelected(method: PaymentMethod) {
        screenState.update { it.copy(paymentMethod = method, error = null) }
    }

    fun dismissError() {
        screenState.update { it.copy(error = null) }
    }

    fun confirmPayment() {
        val state = screenState.value
        val debt = uiState.value.debt ?: return
        if (state.isSaving) return

        val remaining = uiState.value.remainingCents
        when {
            state.amountCents <= 0 -> {
                screenState.update { it.copy(error = DebtDetailErrorType.ZERO_AMOUNT) }
                return
            }
            state.amountCents > remaining -> {
                screenState.update { it.copy(error = DebtDetailErrorType.OVERPAYMENT) }
                return
            }
            state.paymentMethod == null -> {
                screenState.update { it.copy(error = DebtDetailErrorType.METHOD_REQUIRED) }
                return
            }
        }
        val method = state.paymentMethod

        screenState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                debtRepository.registerPayment(debt.id, state.amountCents, method)
                val remainingAfter = remaining - state.amountCents
                screenState.value = ScreenState(
                    phase = DebtDetailPhase.SUCCESS,
                    lastPayment = DebtPaymentResult(
                        amountCents = state.amountCents,
                        remainingAfterCents = remainingAfter,
                        settled = remainingAfter <= 0L,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screenState.update { it.copy(isSaving = false, error = DebtDetailErrorType.PAYMENT_FAILED) }
            }
        }
    }
}
