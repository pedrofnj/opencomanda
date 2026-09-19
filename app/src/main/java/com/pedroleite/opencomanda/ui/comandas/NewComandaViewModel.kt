package com.pedroleite.opencomanda.ui.comandas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewComandaUiState(
    val name: String = "",
    val selectedCustomerId: Long? = null,
    val activeCustomers: List<CustomerEntity> = emptyList(),
    val hasAttemptedCreate: Boolean = false,
    val isSaving: Boolean = false,
    val error: Boolean = false,
    val createdComandaId: Long? = null,
) {
    val nameError: Boolean get() = hasAttemptedCreate && name.isBlank()

    val canCreate: Boolean get() = !isSaving
}

/** Backs the New Comanda creation form. A Comanda is persisted the moment [create] succeeds —
 *  there is no in-memory draft to lose, unlike Quick Sale's cart. */
class NewComandaViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewComandaUiState())
    val uiState: StateFlow<NewComandaUiState> = _uiState.asStateFlow()

    init {
        customerRepository.getActive()
            .onEach { customers -> _uiState.update { it.copy(activeCustomers = customers) } }
            .launchIn(viewModelScope)
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun onCustomerSelected(customerId: Long?) {
        _uiState.update { it.copy(selectedCustomerId = customerId) }
    }

    fun create() {
        _uiState.update { it.copy(hasAttemptedCreate = true) }
        val state = _uiState.value
        if (state.name.isBlank() || state.isSaving) return

        _uiState.update { it.copy(isSaving = true, error = false) }
        viewModelScope.launch {
            try {
                val orderId = orderRepository.createComanda(
                    customerId = state.selectedCustomerId,
                    displayName = state.name,
                )
                _uiState.update { it.copy(isSaving = false, createdComandaId = orderId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = true) }
            }
        }
    }
}
