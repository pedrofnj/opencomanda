package com.pedroleite.opencomanda.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.core.parseStockQuantity
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.data.repository.StockAdjustmentError
import com.pedroleite.opencomanda.data.repository.StockAdjustmentException
import com.pedroleite.opencomanda.data.repository.StockChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StockPhase { LIST, ADJUSTING, SUCCESS }

/** ADD/REMOVE change the stock BY an amount; SET replaces it with a counted amount. */
enum class StockAdjustMode { ADD, REMOVE, SET }

enum class StockErrorType { INVALID_QUANTITY, ZERO_QUANTITY, NEGATIVE_RESULT, NOT_TRACKED, PRODUCT_NOT_FOUND, FAILED }

private data class ScreenState(
    val searchQuery: String = "",
    val phase: StockPhase = StockPhase.LIST,
    val adjustingProductId: Long? = null,
    val mode: StockAdjustMode = StockAdjustMode.ADD,
    val amountText: String = "",
    val isSaving: Boolean = false,
    val error: StockErrorType? = null,
    val lastChange: StockChange? = null,
)

data class StockUiState(
    val isLoading: Boolean = true,
    /** Only products with stock control on — untracked products have no inventory to show. */
    val products: List<ProductEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val searchQuery: String = "",
    val phase: StockPhase = StockPhase.LIST,
    val adjustingProductId: Long? = null,
    val mode: StockAdjustMode = StockAdjustMode.ADD,
    val amountText: String = "",
    val isSaving: Boolean = false,
    val error: StockErrorType? = null,
    val lastChange: StockChange? = null,
) {
    val categoriesById: Map<Long, CategoryEntity> get() = categories.associateBy { it.id }

    val filteredProducts: List<ProductEntity>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return products
            return products.filter { product ->
                product.name.contains(query, ignoreCase = true) ||
                    product.categoryId?.let { categoriesById[it]?.name }?.contains(query, ignoreCase = true) == true
            }
        }

    /** The product being adjusted, read live from the list — so the "current stock" shown while
     *  adjusting follows any sale made meanwhile. Null if it stopped being tracked. */
    val adjustingProduct: ProductEntity? get() = products.find { it.id == adjustingProductId }

    val parsedAmount: Double? get() = parseStockQuantity(amountText)

    /** What the stock would become — a preview for the operator only. The repository recalculates
     *  from the database's own value when the adjustment is confirmed. Null when there is nothing
     *  sensible to show: no valid amount yet, or a removal that would go below zero (the
     *  negative-stock message covers that case, and "9 → -11" would only confuse). */
    val previewQuantity: Double?
        get() {
            val current = adjustingProduct?.stockQuantity ?: return null
            val amount = parsedAmount ?: return null
            val result = when (mode) {
                StockAdjustMode.ADD -> current + amount
                StockAdjustMode.REMOVE -> current - amount
                StockAdjustMode.SET -> amount
            }
            return result.takeIf { it >= 0.0 }
        }

    val hasNoTrackedProducts: Boolean get() = !isLoading && products.isEmpty()
}

/** Backs the Stock screen: the list of stock-controlled products, and a small adjustment flow that
 *  hands the repository only the amount to change by (or the counted total) — never a stale
 *  "resulting" figure. */
class StockViewModel(
    private val productRepository: ProductRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val screenState = MutableStateFlow(ScreenState())

    val uiState: StateFlow<StockUiState> = combine(
        productRepository.getTrackedStock(),
        categoryRepository.getAll(),
        screenState,
    ) { products, categories, screen ->
        StockUiState(
            isLoading = false,
            products = products,
            categories = categories,
            searchQuery = screen.searchQuery,
            phase = screen.phase,
            adjustingProductId = screen.adjustingProductId,
            mode = screen.mode,
            amountText = screen.amountText,
            isSaving = screen.isSaving,
            error = screen.error,
            lastChange = screen.lastChange,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockUiState(),
    )

    fun onSearchQueryChange(query: String) = screenState.update { it.copy(searchQuery = query) }

    fun startAdjusting(productId: Long) = screenState.update {
        it.copy(
            phase = StockPhase.ADJUSTING,
            adjustingProductId = productId,
            mode = StockAdjustMode.ADD,
            amountText = "",
            isSaving = false,
            error = null,
        )
    }

    fun backToList() = screenState.update {
        it.copy(phase = StockPhase.LIST, adjustingProductId = null, amountText = "", error = null)
    }

    fun onModeChange(mode: StockAdjustMode) = screenState.update { it.copy(mode = mode, error = null) }

    fun onAmountTextChange(text: String) = screenState.update { it.copy(amountText = text, error = null) }

    fun dismissError() = screenState.update { it.copy(error = null) }

    fun confirm() {
        val state = uiState.value
        if (state.isSaving || state.phase != StockPhase.ADJUSTING) return
        val product = state.adjustingProduct ?: run {
            screenState.update { it.copy(error = StockErrorType.NOT_TRACKED) }
            return
        }
        val amount = state.parsedAmount
        val mode = state.mode
        when {
            amount == null -> return fail(StockErrorType.INVALID_QUANTITY)
            mode != StockAdjustMode.SET && amount == 0.0 -> return fail(StockErrorType.ZERO_QUANTITY)
            mode == StockAdjustMode.REMOVE && amount > product.stockQuantity -> return fail(StockErrorType.NEGATIVE_RESULT)
        }

        screenState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val change = when (mode) {
                    StockAdjustMode.ADD -> productRepository.adjustStock(product.id, requireNotNull(amount))
                    StockAdjustMode.REMOVE -> productRepository.adjustStock(product.id, -requireNotNull(amount))
                    StockAdjustMode.SET -> productRepository.setStock(product.id, requireNotNull(amount))
                }
                screenState.update {
                    it.copy(phase = StockPhase.SUCCESS, isSaving = false, lastChange = change, amountText = "", error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StockAdjustmentException) {
                screenState.update { it.copy(isSaving = false, error = e.error.toUiError()) }
            } catch (e: Exception) {
                screenState.update { it.copy(isSaving = false, error = StockErrorType.FAILED) }
            }
        }
    }

    /** Leaves the success screen and returns to the list, ready for the next correction. */
    fun done() = screenState.update {
        it.copy(phase = StockPhase.LIST, adjustingProductId = null, lastChange = null, error = null)
    }

    private fun fail(error: StockErrorType) = screenState.update { it.copy(error = error) }

    private fun StockAdjustmentError.toUiError(): StockErrorType = when (this) {
        StockAdjustmentError.PRODUCT_NOT_FOUND -> StockErrorType.PRODUCT_NOT_FOUND
        StockAdjustmentError.NOT_TRACKED -> StockErrorType.NOT_TRACKED
        StockAdjustmentError.INVALID_QUANTITY -> StockErrorType.INVALID_QUANTITY
        StockAdjustmentError.ZERO_ADJUSTMENT -> StockErrorType.ZERO_QUANTITY
        StockAdjustmentError.NEGATIVE_RESULT -> StockErrorType.NEGATIVE_RESULT
    }
}
