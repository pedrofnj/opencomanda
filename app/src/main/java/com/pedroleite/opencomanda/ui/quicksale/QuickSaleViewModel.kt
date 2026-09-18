package com.pedroleite.opencomanda.ui.quicksale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.repository.CartLine
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.InsufficientStockException
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.data.repository.ProductUnavailableException
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.domain.QuickSaleCart
import com.pedroleite.opencomanda.domain.QuickSaleCartLine
import com.pedroleite.opencomanda.ui.products.ProductCategoryFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which part of the Quick Sale flow is currently showing. */
enum class QuickSalePhase { SELECTING, REVIEW, SUCCESS }

enum class QuickSaleErrorType { INSUFFICIENT_STOCK, PRODUCT_UNAVAILABLE, SAVE_FAILED }

data class QuickSaleErrorInfo(
    val type: QuickSaleErrorType,
    val productName: String? = null,
    val availableQuantity: Double? = null,
)

/** A completed sale, kept only long enough to show the success summary. */
data class QuickSaleSummary(
    val lines: List<QuickSaleCartLine>,
    val totalCents: Long,
    val paymentMethod: PaymentMethod,
)

data class QuickSaleUiState(
    val isLoading: Boolean = true,
    /** Active products only — Quick Sale sells what's currently sellable. */
    val products: List<ProductEntity> = emptyList(),
    /** Every category, active or not — an active product can still belong to a since-deactivated
     *  category (see Products/Categories) and must still show its name here. */
    val categories: List<CategoryEntity> = emptyList(),
    val categoryFilter: ProductCategoryFilter = ProductCategoryFilter.All,
    val cartLines: List<QuickSaleCartLine> = emptyList(),
    val paymentMethod: PaymentMethod? = null,
    val phase: QuickSalePhase = QuickSalePhase.SELECTING,
    val isSaving: Boolean = false,
    val error: QuickSaleErrorInfo? = null,
    val completedSale: QuickSaleSummary? = null,
) {
    val activeCategories: List<CategoryEntity> get() = categories.filter { it.active }

    val categoriesById: Map<Long, CategoryEntity> get() = categories.associateBy { it.id }

    val filteredProducts: List<ProductEntity>
        get() = when (val f = categoryFilter) {
            ProductCategoryFilter.All -> products
            ProductCategoryFilter.Uncategorized -> products.filter { it.categoryId == null }
            is ProductCategoryFilter.ByCategory -> products.filter { it.categoryId == f.categoryId }
        }

    val cartQuantityByProductId: Map<Long, Double> get() = cartLines.associate { it.productId to it.quantity }

    val totalCents: Long get() = QuickSaleCart.totalCents(cartLines)

    val canReview: Boolean get() = cartLines.isNotEmpty()

    val canConfirm: Boolean get() = cartLines.isNotEmpty() && paymentMethod != null && !isSaving
}

/** Backs the Quick Sale screen. The cart lives only here in memory until [confirmSale] succeeds
 *  — nothing is written to Room just because the operator opened the screen or added an item. */
class QuickSaleViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val categoryFilter = MutableStateFlow<ProductCategoryFilter>(ProductCategoryFilter.All)
    private val cartLines = MutableStateFlow<List<QuickSaleCartLine>>(emptyList())
    private val screenState = MutableStateFlow(ScreenState())

    private data class ScreenState(
        val phase: QuickSalePhase = QuickSalePhase.SELECTING,
        val paymentMethod: PaymentMethod? = null,
        val isSaving: Boolean = false,
        val error: QuickSaleErrorInfo? = null,
        val completedSale: QuickSaleSummary? = null,
    )

    val uiState: StateFlow<QuickSaleUiState> = combine(
        productRepository.getActive(),
        categoryRepository.getAll(),
        categoryFilter,
        cartLines,
        screenState,
    ) { products, categories, filter, cart, screen ->
        QuickSaleUiState(
            isLoading = false,
            products = products,
            categories = categories,
            categoryFilter = filter,
            cartLines = cart,
            paymentMethod = screen.paymentMethod,
            phase = screen.phase,
            isSaving = screen.isSaving,
            error = screen.error,
            completedSale = screen.completedSale,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuickSaleUiState(),
    )

    fun setCategoryFilter(filter: ProductCategoryFilter) {
        categoryFilter.value = filter
    }

    fun addToCart(product: ProductEntity) {
        cartLines.update { QuickSaleCart.add(it, product.id, product.name, product.priceCents) }
    }

    fun incrementQuantity(productId: Long) {
        cartLines.update { QuickSaleCart.increment(it, productId) }
    }

    fun decrementQuantity(productId: Long) {
        cartLines.update { QuickSaleCart.decrement(it, productId) }
    }

    fun removeFromCart(productId: Long) {
        cartLines.update { QuickSaleCart.remove(it, productId) }
    }

    fun proceedToReview() {
        if (cartLines.value.isEmpty()) return
        screenState.update { it.copy(phase = QuickSalePhase.REVIEW, error = null) }
    }

    fun backToSelecting() {
        screenState.update { it.copy(phase = QuickSalePhase.SELECTING, error = null) }
    }

    fun onPaymentMethodSelected(method: PaymentMethod) {
        screenState.update { it.copy(paymentMethod = method) }
    }

    fun dismissError() {
        screenState.update { it.copy(error = null) }
    }

    /** Discards the whole in-progress sale (cart, payment selection, any error) and returns to
     *  product selection — used by the discard-sale confirmation on back navigation. */
    fun discardSale() {
        cartLines.value = emptyList()
        screenState.value = ScreenState()
    }

    fun startNewSale() {
        cartLines.value = emptyList()
        categoryFilter.value = ProductCategoryFilter.All
        screenState.value = ScreenState()
    }

    fun confirmSale() {
        val state = uiState.value
        if (!state.canConfirm) return
        val method = requireNotNull(state.paymentMethod)
        val lines = state.cartLines

        screenState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                orderRepository.confirmQuickSale(
                    lines = lines.map { CartLine(productId = it.productId, quantity = it.quantity) },
                    method = method,
                    isFiado = false,
                    customerId = null,
                )
                val summary = QuickSaleSummary(lines = lines, totalCents = state.totalCents, paymentMethod = method)
                cartLines.value = emptyList()
                screenState.value = ScreenState(phase = QuickSalePhase.SUCCESS, completedSale = summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InsufficientStockException) {
                screenState.update {
                    it.copy(
                        isSaving = false,
                        error = QuickSaleErrorInfo(
                            type = QuickSaleErrorType.INSUFFICIENT_STOCK,
                            productName = e.productName,
                            availableQuantity = e.availableQuantity,
                        ),
                    )
                }
            } catch (e: ProductUnavailableException) {
                // The product may since have been hard-removed (never happens via this app's own
                // UI, but defend against it anyway) — in that rare case the exception has no
                // name to report, so fall back to what the cart itself last knew it as.
                val fallbackName = lines.find { it.productId == e.productId }?.productName
                screenState.update {
                    it.copy(
                        isSaving = false,
                        error = QuickSaleErrorInfo(
                            type = QuickSaleErrorType.PRODUCT_UNAVAILABLE,
                            productName = e.productName.ifBlank { fallbackName },
                        ),
                    )
                }
            } catch (e: Exception) {
                screenState.update { it.copy(isSaving = false, error = QuickSaleErrorInfo(QuickSaleErrorType.SAVE_FAILED)) }
            }
        }
    }
}
