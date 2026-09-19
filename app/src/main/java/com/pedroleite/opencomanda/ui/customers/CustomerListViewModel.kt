package com.pedroleite.opencomanda.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.core.normalizePhoneDigits
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the Customers list is currently narrowed down by active state. */
enum class CustomerStatusFilter { ALL, ACTIVE, INACTIVE }

data class CustomerListUiState(
    val isLoading: Boolean = true,
    val customers: List<CustomerEntity> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: CustomerStatusFilter = CustomerStatusFilter.ALL,
) {
    val filteredCustomers: List<CustomerEntity>
        get() = customers
            .filter { matchesStatus(it) }
            .filter { matchesSearch(it) }

    private fun matchesStatus(customer: CustomerEntity): Boolean = when (statusFilter) {
        CustomerStatusFilter.ALL -> true
        CustomerStatusFilter.ACTIVE -> customer.active
        CustomerStatusFilter.INACTIVE -> !customer.active
    }

    private fun matchesSearch(customer: CustomerEntity): Boolean {
        val query = searchQuery.trim()
        if (query.isEmpty()) return true
        if (customer.name.contains(query, ignoreCase = true)) return true

        val queryDigits = normalizePhoneDigits(query)
        if (queryDigits.isEmpty()) return false
        val phoneDigits = customer.phone?.let { normalizePhoneDigits(it) } ?: return false
        return phoneDigits.isNotEmpty() && phoneDigits.contains(queryDigits)
    }
}

/** Backs the Customers list screen with the live set of customers from Room, narrowed down by
 *  an in-memory search/status filter — simple local filtering, appropriate for how many
 *  customers a small business realistically has. */
class CustomerListViewModel(private val repository: CustomerRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow(CustomerStatusFilter.ALL)

    val uiState: StateFlow<CustomerListUiState> = combine(
        repository.getAll(),
        searchQuery,
        statusFilter,
    ) { customers, query, filter ->
        CustomerListUiState(isLoading = false, customers = customers, searchQuery = query, statusFilter = filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CustomerListUiState(),
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onStatusFilterChange(filter: CustomerStatusFilter) {
        statusFilter.value = filter
    }

    fun setActive(customerId: Long, active: Boolean) {
        viewModelScope.launch {
            repository.setActive(customerId, active)
        }
    }
}
