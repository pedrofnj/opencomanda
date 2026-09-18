package com.pedroleite.opencomanda.ui.comandas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.local.entity.OrderEntity
import com.pedroleite.opencomanda.data.local.entity.OrderItemEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.InsufficientStockException
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.data.repository.ProductUnavailableException
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.products.ProductCategoryFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which part of the Comanda detail flow is currently showing. */
enum class ComandaDetailPhase { DETAIL, ADDING_PRODUCTS, CLOSING, SUCCESS }

enum class ComandaDetailErrorType { INSUFFICIENT_STOCK, PRODUCT_UNAVAILABLE, ADD_FAILED, CLOSE_FAILED }

data class ComandaDetailError(
    val type: ComandaDetailErrorType,
    val productName: String? = null,
    val availableQuantity: Double? = null,
)

/** A closed Comanda's summary, kept only long enough to show the success screen. */
data class ComandaClosedSummary(
    val displayName: String,
    val items: List<OrderItemEntity>,
    val totalCents: Long,
    val paymentMethod: PaymentMethod,
)

private data class ComandaData(val order: OrderEntity?, val items: List<OrderItemEntity>)

private data class CatalogData(
    val products: List<ProductEntity>,
    val categories: List<CategoryEntity>,
    val filter: ProductCategoryFilter,
)

private data class ScreenState(
    val phase: ComandaDetailPhase = ComandaDetailPhase.DETAIL,
    val paymentMethod: PaymentMethod? = null,
    val isMutating: Boolean = false,
    val isClosing: Boolean = false,
    val error: ComandaDetailError? = null,
    val completedSale: ComandaClosedSummary? = null,
)

data class ComandaDetailUiState(
    val isLoading: Boolean = true,
    val order: OrderEntity? = null,
    val customer: CustomerEntity? = null,
    val items: List<OrderItemEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val categoryFilter: ProductCategoryFilter = ProductCategoryFilter.All,
    val phase: ComandaDetailPhase = ComandaDetailPhase.DETAIL,
    val paymentMethod: PaymentMethod? = null,
    val isMutating: Boolean = false,
    val isClosing: Boolean = false,
    val error: ComandaDetailError? = null,
    val completedSale: ComandaClosedSummary? = null,
) {
    val notFound: Boolean get() = !isLoading && order == null

    val isOpen: Boolean get() = order?.status == OrderStatus.OPEN

    val totalCents: Long get() = items.sumOf { it.subtotalCents }

    val activeCategories: List<CategoryEntity> get() = categories.filter { it.active }

    val categoriesById: Map<Long, CategoryEntity> get() = categories.associateBy { it.id }

    val filteredProducts: List<ProductEntity>
        get() = when (val f = categoryFilter) {
            ProductCategoryFilter.All -> products
            ProductCategoryFilter.Uncategorized -> products.filter { it.categoryId == null }
            is ProductCategoryFilter.ByCategory -> products.filter { it.categoryId == f.categoryId }
        }

    val itemQuantityByProductId: Map<Long, Double>
        get() = items.mapNotNull { item -> item.productId?.let { it to item.quantity } }.toMap()

    val canClose: Boolean get() = isOpen && items.isNotEmpty()

    val canConfirmClose: Boolean get() = canClose && paymentMethod != null && !isClosing
}

/** Backs the Comanda detail screen. Unlike Quick Sale, every mutation here (add/decrement an
 *  item, close, cancel) is written straight through to Room via [orderRepository] — there is no
 *  in-memory draft, since the Comanda itself is already persisted before this screen ever opens. */
class ComandaDetailViewModel(
    private val comandaId: Long,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    private val categoryFilter = MutableStateFlow<ProductCategoryFilter>(ProductCategoryFilter.All)
    private val screenState = MutableStateFlow(ScreenState())
    private val customer = MutableStateFlow<CustomerEntity?>(null)

    private val comandaFlow = combine(
        orderRepository.getComanda(comandaId),
        orderRepository.getItemsForOrder(comandaId),
    ) { order, items -> ComandaData(order, items) }

    private val catalogFlow = combine(
        productRepository.getActive(),
        categoryRepository.getAll(),
        categoryFilter,
    ) { products, categories, filter -> CatalogData(products, categories, filter) }

    val uiState: StateFlow<ComandaDetailUiState> = combine(
        comandaFlow,
        catalogFlow,
        customer,
        screenState,
    ) { comanda, catalog, cust, screen ->
        ComandaDetailUiState(
            isLoading = false,
            order = comanda.order,
            customer = cust,
            items = comanda.items,
            products = catalog.products,
            categories = catalog.categories,
            categoryFilter = catalog.filter,
            phase = screen.phase,
            paymentMethod = screen.paymentMethod,
            isMutating = screen.isMutating,
            isClosing = screen.isClosing,
            error = screen.error,
            completedSale = screen.completedSale,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ComandaDetailUiState(),
    )

    init {
        // The associated customer is fixed at creation for this increment (no edit-customer
        // action), so a one-time lookup once the order first loads is enough — no need to
        // re-resolve it on every emission.
        comandaFlow
            .onEach { data ->
                val customerId = data.order?.customerId
                if (customerId != null && customer.value?.id != customerId) {
                    customer.value = customerRepository.getById(customerId)
                }
            }
            .launchIn(viewModelScope)
    }

    fun setCategoryFilter(filter: ProductCategoryFilter) {
        categoryFilter.value = filter
    }

    fun addProduct(productId: Long) {
        viewModelScope.launch {
            screenState.update { it.copy(isMutating = true, error = null) }
            try {
                orderRepository.addComandaItem(comandaId, productId, 1.0)
                screenState.update { it.copy(isMutating = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InsufficientStockException) {
                screenState.update {
                    it.copy(
                        isMutating = false,
                        error = ComandaDetailError(
                            type = ComandaDetailErrorType.INSUFFICIENT_STOCK,
                            productName = e.productName,
                            availableQuantity = e.availableQuantity,
                        ),
                    )
                }
            } catch (e: ProductUnavailableException) {
                screenState.update {
                    it.copy(
                        isMutating = false,
                        error = ComandaDetailError(ComandaDetailErrorType.PRODUCT_UNAVAILABLE, e.productName),
                    )
                }
            } catch (e: Exception) {
                screenState.update { it.copy(isMutating = false, error = ComandaDetailError(ComandaDetailErrorType.ADD_FAILED)) }
            }
        }
    }

    fun decrementProduct(productId: Long) {
        viewModelScope.launch {
            screenState.update { it.copy(isMutating = true, error = null) }
            try {
                orderRepository.decrementComandaItem(comandaId, productId)
                screenState.update { it.copy(isMutating = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screenState.update { it.copy(isMutating = false, error = ComandaDetailError(ComandaDetailErrorType.ADD_FAILED)) }
            }
        }
    }

    fun startAddingProducts() {
        screenState.update { it.copy(phase = ComandaDetailPhase.ADDING_PRODUCTS, error = null) }
    }

    fun proceedToClosing() {
        if (!uiState.value.canClose) return
        screenState.update { it.copy(phase = ComandaDetailPhase.CLOSING, error = null) }
    }

    fun backToDetail() {
        screenState.update { it.copy(phase = ComandaDetailPhase.DETAIL, error = null) }
    }

    fun onPaymentMethodSelected(method: PaymentMethod) {
        screenState.update { it.copy(paymentMethod = method) }
    }

    fun dismissError() {
        screenState.update { it.copy(error = null) }
    }

    fun confirmClose() {
        val state = uiState.value
        if (!state.canConfirmClose) return
        val method = requireNotNull(state.paymentMethod)
        val displayName = state.order?.displayName.orEmpty()
        val items = state.items

        screenState.update { it.copy(isClosing = true, error = null) }
        viewModelScope.launch {
            try {
                orderRepository.closeOrderWithPayment(comandaId, method)
                val summary = ComandaClosedSummary(
                    displayName = displayName,
                    items = items,
                    totalCents = state.totalCents,
                    paymentMethod = method,
                )
                screenState.value = ScreenState(phase = ComandaDetailPhase.SUCCESS, completedSale = summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screenState.update { it.copy(isClosing = false, error = ComandaDetailError(ComandaDetailErrorType.CLOSE_FAILED)) }
            }
        }
    }

    fun cancelComanda() {
        viewModelScope.launch {
            try {
                orderRepository.cancelComanda(comandaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort: the Comanda simply stays OPEN and the operator can retry.
            }
        }
    }
}
