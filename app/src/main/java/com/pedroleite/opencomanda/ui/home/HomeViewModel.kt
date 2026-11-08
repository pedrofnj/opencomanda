package com.pedroleite.opencomanda.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.dao.OrderDao
import com.pedroleite.opencomanda.data.local.entity.CashSessionEntity
import com.pedroleite.opencomanda.data.repository.CashRegisterRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.cashregister.receivedByMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val isLoading: Boolean = true,
    /** The OPEN cash session, if any — Home's takings card shows its totals, or invites opening one. */
    val openSession: CashSessionEntity? = null,
    val receivedByMethod: Map<PaymentMethod, Long> = emptyMap(),
    val openComandas: List<OrderDao.ComandaSummary> = emptyList(),
) {
    val totalReceivedCents: Long get() = receivedByMethod.values.sum()

    fun receivedCentsFor(method: PaymentMethod): Long = receivedByMethod[method] ?: 0L

    /** Debit and credit together: Home shows one "card" figure; the Cash Register screen splits them. */
    val receivedByCardCents: Long get() = receivedCentsFor(PaymentMethod.DEBIT) + receivedCentsFor(PaymentMethod.CREDIT)
}

/** Backs Home: a read-only, live glance at the open cash session's takings and the open Comandas.
 *  Everything is derived from the same repositories (and the same totals rule) the Cash Register
 *  and Open Comandas screens use, so the figures always agree with theirs. */
class HomeViewModel(
    cashRegisterRepository: CashRegisterRepository,
    orderRepository: OrderRepository,
) : ViewModel() {

    private val openSession = cashRegisterRepository.observeOpenSession()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val received = openSession.flatMapLatest { session ->
        if (session == null) {
            flowOf(emptyMap())
        } else {
            combine(
                cashRegisterRepository.observePaymentsForSession(session.id),
                cashRegisterRepository.observeDebtPaymentsForSession(session.id),
            ) { payments, debtPayments -> receivedByMethod(payments, debtPayments) }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        openSession,
        received,
        orderRepository.getOpenComandas(),
    ) { session, byMethod, comandas ->
        HomeUiState(
            isLoading = false,
            openSession = session,
            receivedByMethod = byMethod,
            openComandas = comandas,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
