package com.pedroleite.opencomanda.ui.cashregister

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.CashSessionEntity
import com.pedroleite.opencomanda.data.local.entity.PaymentEntity
import com.pedroleite.opencomanda.data.repository.CashRegisterRepository
import com.pedroleite.opencomanda.data.repository.CashSessionSummary
import com.pedroleite.opencomanda.domain.CashSessionStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which part of the Cash Register flow is currently showing. */
enum class CashRegisterPhase { EMPTY, OPENING, DASHBOARD, CLOSING, SUMMARY }

enum class CashRegisterErrorType { OPEN_FAILED, CLOSE_FAILED }

/** Whether the operator is looking at the empty state or the opening form, while no session is
 *  open — kept separate from [OpenSessionStep] so restoring/restarting the app with a session
 *  already OPEN lands straight on the dashboard rather than replaying local navigation state. */
private enum class NoSessionStep { EMPTY, OPENING }

private enum class OpenSessionStep { DASHBOARD, CLOSING }

private data class ScreenState(
    val noSessionStep: NoSessionStep = NoSessionStep.EMPTY,
    val openSessionStep: OpenSessionStep = OpenSessionStep.DASHBOARD,
    val openingBalanceCents: Long = 0,
    val openingNotes: String = "",
    val countedCashCents: Long = 0,
    val isSaving: Boolean = false,
    val error: CashRegisterErrorType? = null,
    val closedSummary: CashSessionSummary? = null,
)

data class CashRegisterUiState(
    val isLoading: Boolean = true,
    val phase: CashRegisterPhase = CashRegisterPhase.EMPTY,
    val openSession: CashSessionEntity? = null,
    /** Every payment recorded for [openSession], live — the dashboard's per-method totals, total
     *  received and expected cash are all derived from this, so they update the moment a Quick
     *  Sale/Comanda payment is confirmed elsewhere, with no manual refresh. */
    val payments: List<PaymentEntity> = emptyList(),
    val mostRecentClosedSession: CashSessionEntity? = null,
    val openingBalanceCents: Long = 0,
    val openingNotes: String = "",
    val countedCashCents: Long = 0,
    val isSaving: Boolean = false,
    val error: CashRegisterErrorType? = null,
    /** The just-closed session's authoritative summary, kept only long enough to show it. */
    val closedSummary: CashSessionSummary? = null,
) {
    val totalsByMethod: Map<PaymentMethod, Long>
        get() = payments.groupBy { it.method }.mapValues { (_, forMethod) -> forMethod.sumOf { it.amountCents } }

    val totalReceivedCents: Long get() = totalsByMethod.values.sum()

    /** Opening balance plus CASH payments only — PIX/debit/credit are received money too (see
     *  [totalReceivedCents]) but never touch the physical drawer. */
    val expectedCashCents: Long
        get() = (openSession?.openingBalanceCents ?: 0L) + (totalsByMethod[PaymentMethod.CASH] ?: 0L)

    /** Live preview of counted minus expected, shown while still deciding what to enter on the
     *  closing screen. [CashSessionSummary.differenceCents] is the authoritative figure actually
     *  persisted once the session closes. */
    val liveDifferenceCents: Long get() = countedCashCents - expectedCashCents

    val canConfirmOpen: Boolean get() = !isSaving

    val canConfirmClose: Boolean get() = !isSaving
}

/** Backs the Cash Register screen: a closed/empty state when no session is OPEN, an opening
 *  form, a live reactive dashboard while one is OPEN, and a closing flow that confirms against
 *  the same authoritative totals the repository itself computes on close. */
class CashRegisterViewModel(
    private val cashRegisterRepository: CashRegisterRepository,
) : ViewModel() {

    private val screenState = MutableStateFlow(ScreenState())
    private val mostRecentClosedSession = MutableStateFlow<CashSessionEntity?>(null)

    private val openSessionFlow = cashRegisterRepository.observeOpenSession()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val paymentsFlow = openSessionFlow.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else cashRegisterRepository.observePaymentsForSession(session.id)
    }

    val uiState: StateFlow<CashRegisterUiState> = combine(
        openSessionFlow,
        paymentsFlow,
        mostRecentClosedSession,
        screenState,
    ) { session, payments, closedSession, screen ->
        CashRegisterUiState(
            isLoading = false,
            phase = resolvePhase(session, screen),
            openSession = session,
            payments = payments,
            mostRecentClosedSession = closedSession,
            openingBalanceCents = screen.openingBalanceCents,
            openingNotes = screen.openingNotes,
            countedCashCents = screen.countedCashCents,
            isSaving = screen.isSaving,
            error = screen.error,
            closedSummary = screen.closedSummary,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CashRegisterUiState(),
    )

    init {
        cashRegisterRepository.getAllSessions()
            .onEach { sessions -> mostRecentClosedSession.value = sessions.firstOrNull { it.status == CashSessionStatus.CLOSED } }
            .launchIn(viewModelScope)
    }

    private fun resolvePhase(session: CashSessionEntity?, screen: ScreenState): CashRegisterPhase = when {
        screen.closedSummary != null -> CashRegisterPhase.SUMMARY
        session == null -> if (screen.noSessionStep == NoSessionStep.OPENING) CashRegisterPhase.OPENING else CashRegisterPhase.EMPTY
        else -> if (screen.openSessionStep == OpenSessionStep.CLOSING) CashRegisterPhase.CLOSING else CashRegisterPhase.DASHBOARD
    }

    fun startOpening() {
        if (uiState.value.openSession != null) return
        screenState.update {
            it.copy(noSessionStep = NoSessionStep.OPENING, openingBalanceCents = 0, openingNotes = "", error = null)
        }
    }

    fun backToEmpty() {
        screenState.update { it.copy(noSessionStep = NoSessionStep.EMPTY, error = null) }
    }

    fun onOpeningBalanceChanged(cents: Long) {
        screenState.update { it.copy(openingBalanceCents = cents) }
    }

    fun onOpeningNotesChanged(notes: String) {
        screenState.update { it.copy(openingNotes = notes) }
    }

    fun confirmOpen() {
        val state = screenState.value
        if (!uiState.value.canConfirmOpen) return

        screenState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                cashRegisterRepository.openSession(
                    openingBalanceCents = state.openingBalanceCents,
                    notes = state.openingNotes.trim().ifBlank { null },
                )
                screenState.update {
                    it.copy(isSaving = false, noSessionStep = NoSessionStep.EMPTY, openSessionStep = OpenSessionStep.DASHBOARD)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screenState.update { it.copy(isSaving = false, error = CashRegisterErrorType.OPEN_FAILED) }
            }
        }
    }

    fun startClosing() {
        if (uiState.value.openSession == null) return
        screenState.update { it.copy(openSessionStep = OpenSessionStep.CLOSING, countedCashCents = 0, error = null) }
    }

    fun backToDashboard() {
        screenState.update { it.copy(openSessionStep = OpenSessionStep.DASHBOARD, error = null) }
    }

    fun onCountedCashChanged(cents: Long) {
        screenState.update { it.copy(countedCashCents = cents) }
    }

    fun confirmClose() {
        val state = screenState.value
        val sessionId = uiState.value.openSession?.id ?: return
        if (!uiState.value.canConfirmClose) return

        screenState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val summary = cashRegisterRepository.closeSession(sessionId, countedCashCents = state.countedCashCents)
                screenState.value = ScreenState(closedSummary = summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screenState.update { it.copy(isSaving = false, error = CashRegisterErrorType.CLOSE_FAILED) }
            }
        }
    }

    /** Leaves the just-closed session's summary and returns to the empty state, ready for a new
     *  session to be opened. */
    fun doneWithSummary() {
        screenState.value = ScreenState()
    }

    fun dismissError() {
        screenState.update { it.copy(error = null) }
    }
}
