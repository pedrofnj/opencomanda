package com.pedroleite.opencomanda.ui.fiado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.DebtRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.domain.DebtStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One outstanding debt as shown on the customer's Fiado detail — [orderDisplayName] labels it
 *  by the Comanda it came from (e.g. "Mesa 4") when known. */
data class DebtRowInfo(
    val debtId: Long,
    val orderDisplayName: String?,
    val createdAt: Long,
    val originalAmountCents: Long,
    val paidCents: Long,
    val remainingCents: Long,
    val status: DebtStatus,
)

data class CustomerFiadoUiState(
    val isLoading: Boolean = true,
    val customer: CustomerEntity? = null,
    val debts: List<DebtRowInfo> = emptyList(),
) {
    val notFound: Boolean get() = !isLoading && customer == null
    val totalOutstandingCents: Long get() = debts.sumOf { it.remainingCents }
    val hasNoOpenDebts: Boolean get() = !isLoading && customer != null && debts.isEmpty()
}

/** Backs the customer Fiado detail screen: this customer's own outstanding debts (open or
 *  partial only — a paid-off debt is history, not an operational concern here), each with a
 *  live remaining balance. Works the same whether the customer is active or not (see
 *  [CustomerRepository]) — a historical debt is never hidden just because the customer was
 *  later deactivated. */
class CustomerFiadoViewModel(
    private val customerId: Long,
    private val debtRepository: DebtRepository,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val customer = MutableStateFlow<CustomerEntity?>(null)
    private val orderDisplayNames = MutableStateFlow<Map<Long, String>>(emptyMap())

    private val debtsFlow = debtRepository.getOpenDebtsForCustomerWithRemaining(customerId)

    val uiState: StateFlow<CustomerFiadoUiState> = combine(
        debtsFlow,
        customer,
        orderDisplayNames,
    ) { debts, cust, names ->
        CustomerFiadoUiState(
            isLoading = false,
            customer = cust,
            debts = debts
                .map { entry ->
                    DebtRowInfo(
                        debtId = entry.debt.id,
                        orderDisplayName = entry.debt.orderId?.let { names[it] },
                        createdAt = entry.debt.createdAt,
                        originalAmountCents = entry.debt.originalAmountCents,
                        paidCents = entry.paidCents,
                        remainingCents = entry.remainingCents,
                        status = entry.debt.status,
                    )
                }
                .sortedByDescending { it.createdAt },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CustomerFiadoUiState(),
    )

    init {
        viewModelScope.launch { customer.value = customerRepository.getById(customerId) }

        // One-shot lookups for each distinct order behind a debt — display names never change,
        // so there's no need to observe them, only to fetch each one exactly once.
        debtsFlow
            .onEach { debts ->
                val missingOrderIds = debts.mapNotNull { it.debt.orderId }.filter { it !in orderDisplayNames.value }
                if (missingOrderIds.isNotEmpty()) {
                    val fetched = missingOrderIds.associateWith { orderRepository.getDisplayName(it).orEmpty() }
                    orderDisplayNames.update { it + fetched }
                }
            }
            .launchIn(viewModelScope)
    }
}
