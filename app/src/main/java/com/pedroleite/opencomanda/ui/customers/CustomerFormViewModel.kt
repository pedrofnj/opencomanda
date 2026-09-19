package com.pedroleite.opencomanda.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CustomerFormError { SAVE_FAILED, CUSTOMER_NOT_FOUND }

data class CustomerFormUiState(
    val editingCustomerId: Long? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val notes: String = "",
    val hasAttemptedSave: Boolean = false,
    val error: CustomerFormError? = null,
    val saveComplete: Boolean = false,
) {
    val isEditing: Boolean get() = editingCustomerId != null

    val nameError: Boolean get() = hasAttemptedSave && name.isBlank()

    val canSave: Boolean get() = !isSaving
}

/** Backs both Create Customer and Edit Customer — they share every field, differing only in
 *  whether an existing customer is loaded first and whether repository.create or .update runs. */
class CustomerFormViewModel(private val repository: CustomerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerFormUiState())
    val uiState: StateFlow<CustomerFormUiState> = _uiState.asStateFlow()

    private var loadedCustomer: CustomerEntity? = null

    fun loadForEditing(customerId: Long) {
        if (_uiState.value.editingCustomerId == customerId) return
        _uiState.update { it.copy(editingCustomerId = customerId, isLoading = true) }
        viewModelScope.launch {
            val customer = repository.getById(customerId)
            if (customer == null) {
                _uiState.update { it.copy(isLoading = false, error = CustomerFormError.CUSTOMER_NOT_FOUND) }
                return@launch
            }
            loadedCustomer = customer
            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = customer.name,
                    phone = customer.phone.orEmpty(),
                    notes = customer.notes.orEmpty(),
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(hasAttemptedSave = true, error = null) }

        if (state.name.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val existing = loadedCustomer
                if (existing != null) {
                    repository.update(
                        existing.copy(
                            name = state.name,
                            phone = state.phone.ifBlank { null },
                            notes = state.notes.ifBlank { null },
                        ),
                    )
                } else {
                    repository.create(
                        name = state.name,
                        phone = state.phone.ifBlank { null },
                        notes = state.notes.ifBlank { null },
                    )
                }
                _uiState.update { it.copy(isSaving = false, saveComplete = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = CustomerFormError.SAVE_FAILED) }
            }
        }
    }
}
