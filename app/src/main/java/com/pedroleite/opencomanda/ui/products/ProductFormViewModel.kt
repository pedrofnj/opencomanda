package com.pedroleite.opencomanda.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.core.formatQuantity
import com.pedroleite.opencomanda.core.parseStockQuantity
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.data.repository.StockConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class ProductFormError { SAVE_FAILED, PRODUCT_NOT_FOUND }

data class ProductFormUiState(
    val editingProductId: Long? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val description: String = "",
    val priceCents: Long = 0L,
    val hasCost: Boolean = false,
    val costCents: Long = 0L,
    val trackStock: Boolean = false,
    val stockQuantityText: String = "",
    val categoryId: Long? = null,
    /** Every category, active or not — so a product already assigned to a since-deactivated
     *  category can still show its name instead of silently losing the association. */
    val categories: List<CategoryEntity> = emptyList(),
    val hasAttemptedSave: Boolean = false,
    val error: ProductFormError? = null,
    val saveComplete: Boolean = false,
) {
    val isEditing: Boolean get() = editingProductId != null

    val nameError: Boolean get() = hasAttemptedSave && name.isBlank()

    val parsedStockQuantity: Double? get() = parseStockQuantity(stockQuantityText)

    val stockQuantityError: Boolean
        get() = hasAttemptedSave && trackStock && parsedStockQuantity == null

    val isPriceZero: Boolean get() = priceCents == 0L

    /** Active categories, plus the currently selected one even if it has since been
     *  deactivated — so it stays visible/selected instead of disappearing from the picker. */
    val selectableCategories: List<CategoryEntity>
        get() = categories.filter { it.active || it.id == categoryId }

    val canSave: Boolean get() = !isSaving
}

/** Backs both Create Product and Edit Product — they share every field, differing only in
 *  whether an existing product is loaded first and whether repository.create or .update runs. */
class ProductFormViewModel(
    private val repository: ProductRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductFormUiState())
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    private var loadedProduct: ProductEntity? = null

    /** The stock text shown when the form opened — compared on save so stock is only written back
     *  when the operator actually edited it (see [ProductRepository.update]). */
    private var loadedStockText: String = ""

    init {
        categoryRepository.getAll()
            .onEach { categories -> _uiState.update { it.copy(categories = categories) } }
            .launchIn(viewModelScope)
    }

    fun loadForEditing(productId: Long) {
        if (_uiState.value.editingProductId == productId) return
        _uiState.update { it.copy(editingProductId = productId, isLoading = true) }
        viewModelScope.launch {
            val product = repository.getById(productId)
            if (product == null) {
                _uiState.update { it.copy(isLoading = false, error = ProductFormError.PRODUCT_NOT_FOUND) }
                return@launch
            }
            loadedProduct = product
            loadedStockText = if (product.trackStock) formatQuantity(product.stockQuantity, Locale.getDefault()) else ""
            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = product.name,
                    description = product.description.orEmpty(),
                    priceCents = product.priceCents,
                    hasCost = product.costCents != null,
                    costCents = product.costCents ?: 0L,
                    trackStock = product.trackStock,
                    stockQuantityText = loadedStockText,
                    categoryId = product.categoryId,
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun onPriceChange(cents: Long) = _uiState.update { it.copy(priceCents = cents) }

    fun onHasCostChange(hasCost: Boolean) = _uiState.update { it.copy(hasCost = hasCost) }

    fun onCostChange(cents: Long) = _uiState.update { it.copy(costCents = cents) }

    fun onTrackStockChange(trackStock: Boolean) = _uiState.update { it.copy(trackStock = trackStock) }

    fun onStockQuantityTextChange(text: String) = _uiState.update { it.copy(stockQuantityText = text) }

    fun onCategoryChange(categoryId: Long?) = _uiState.update { it.copy(categoryId = categoryId) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(hasAttemptedSave = true, error = null) }

        if (state.name.isBlank()) return
        if (state.trackStock && state.parsedStockQuantity == null) return

        val stockQuantity = if (state.trackStock) state.parsedStockQuantity ?: 0.0 else 0.0

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val existing = loadedProduct
                if (existing != null) {
                    val stockEdited = state.trackStock != existing.trackStock ||
                        (state.trackStock && state.stockQuantityText.trim() != loadedStockText)
                    repository.update(
                        existing.copy(
                            name = state.name,
                            description = state.description.ifBlank { null },
                            priceCents = state.priceCents,
                            costCents = if (state.hasCost) state.costCents else null,
                            categoryId = state.categoryId,
                        ),
                        stockConfig = if (stockEdited) StockConfig(state.trackStock, stockQuantity) else null,
                    )
                } else {
                    repository.create(
                        name = state.name,
                        description = state.description.ifBlank { null },
                        priceCents = state.priceCents,
                        costCents = if (state.hasCost) state.costCents else null,
                        trackStock = state.trackStock,
                        initialStockQuantity = stockQuantity,
                        categoryId = state.categoryId,
                    )
                }
                _uiState.update { it.copy(isSaving = false, saveComplete = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = ProductFormError.SAVE_FAILED) }
            }
        }
    }
}
