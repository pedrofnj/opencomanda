package com.pedroleite.opencomanda.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.DuplicateCategoryNameException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CategoryFormError { DUPLICATE_NAME, SAVE_FAILED }

data class CategoryDialogState(
    val isOpen: Boolean = false,
    val editingCategoryId: Long? = null,
    val nameInput: String = "",
    val isSaving: Boolean = false,
    val hasAttemptedSave: Boolean = false,
    val error: CategoryFormError? = null,
) {
    val isEditing: Boolean get() = editingCategoryId != null
    val nameError: Boolean get() = hasAttemptedSave && nameInput.isBlank()
}

data class CategoryManagementUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryEntity> = emptyList(),
    val dialog: CategoryDialogState = CategoryDialogState(),
    /** Why switching a category on or off failed — shown once, then dismissed by the operator. */
    val activationError: CategoryFormError? = null,
)

/** Backs the lightweight "Manage categories" screen: a flat list plus a single create/rename
 *  dialog — deliberately not a full second CRUD module, mirroring how small this feature is. */
class CategoryManagementViewModel(private val repository: CategoryRepository) : ViewModel() {

    private val dialog = MutableStateFlow(CategoryDialogState())
    private val activationError = MutableStateFlow<CategoryFormError?>(null)

    val uiState: StateFlow<CategoryManagementUiState> = combine(
        repository.getAll(),
        dialog,
        activationError,
    ) { categories, dialog, activationError ->
        CategoryManagementUiState(
            isLoading = false,
            categories = categories,
            dialog = dialog,
            activationError = activationError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryManagementUiState(),
    )

    fun openCreateDialog() {
        dialog.value = CategoryDialogState(isOpen = true)
    }

    fun openEditDialog(category: CategoryEntity) {
        dialog.value = CategoryDialogState(isOpen = true, editingCategoryId = category.id, nameInput = category.name)
    }

    fun dismissDialog() {
        dialog.value = CategoryDialogState()
    }

    fun onNameInputChange(value: String) {
        dialog.update { it.copy(nameInput = value) }
    }

    fun save() {
        val state = dialog.value
        if (state.isSaving) return
        dialog.update { it.copy(hasAttemptedSave = true, error = null) }
        if (state.nameInput.isBlank()) return

        viewModelScope.launch {
            dialog.update { it.copy(isSaving = true) }
            try {
                val editingId = state.editingCategoryId
                if (editingId != null) {
                    repository.rename(editingId, state.nameInput)
                } else {
                    repository.create(state.nameInput)
                }
                dialog.value = CategoryDialogState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateCategoryNameException) {
                dialog.update { it.copy(isSaving = false, error = CategoryFormError.DUPLICATE_NAME) }
            } catch (e: Exception) {
                dialog.update { it.copy(isSaving = false, error = CategoryFormError.SAVE_FAILED) }
            }
        }
    }

    fun setActive(categoryId: Long, active: Boolean) {
        viewModelScope.launch {
            try {
                repository.setActive(categoryId, active)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateCategoryNameException) {
                activationError.value = CategoryFormError.DUPLICATE_NAME
            } catch (e: Exception) {
                activationError.value = CategoryFormError.SAVE_FAILED
            }
        }
    }

    fun dismissActivationError() {
        activationError.value = null
    }
}
