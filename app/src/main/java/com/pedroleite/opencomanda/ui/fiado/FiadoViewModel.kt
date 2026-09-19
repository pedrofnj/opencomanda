package com.pedroleite.opencomanda.ui.fiado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.DebtRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One customer's aggregated Fiado standing — the main Fiado screen answers "who owes money?",
 *  not "list every debt row", so debts are grouped and summed by customer before display. An
 *  inactive customer can still appear here (see [com.pedroleite.opencomanda.data.repository.CustomerRepository]) —
 *  a historical debt is never hidden just because the customer was later deactivated. */
data class CustomerFiadoSummary(
    val customerId: Long,
    val customerName: String,
    val totalOutstandingCents: Long,
    val debtCount: Int,
)

data class FiadoUiState(
    val isLoading: Boolean = true,
    val customers: List<CustomerFiadoSummary> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && customers.isEmpty()
}

/** Backs the main Fiado screen: every customer who currently owes money, each with their
 *  combined outstanding total, derived live from persisted debts and repayments — never a
 *  separately stored total that could drift. */
class FiadoViewModel(
    private val debtRepository: DebtRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    val uiState: StateFlow<FiadoUiState> = combine(
        debtRepository.getOpenDebtsWithRemaining(),
        customerRepository.getAll(),
    ) { debts, customers ->
        val customersById = customers.associateBy { it.id }
        val summaries = debts
            .groupBy { it.debt.customerId }
            .mapNotNull { (customerId, debtsForCustomer) ->
                val customer = customersById[customerId] ?: return@mapNotNull null
                CustomerFiadoSummary(
                    customerId = customerId,
                    customerName = customer.name,
                    totalOutstandingCents = debtsForCustomer.sumOf { it.remainingCents },
                    debtCount = debtsForCustomer.size,
                )
            }
            .sortedBy { it.customerName.lowercase() }
        FiadoUiState(isLoading = false, customers = summaries)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FiadoUiState(),
    )
}
