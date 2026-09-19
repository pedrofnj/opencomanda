package com.pedroleite.opencomanda.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the Products list is currently narrowed down. */
sealed class ProductCategoryFilter {
    data object All : ProductCategoryFilter()
    data object Uncategorized : ProductCategoryFilter()
    data class ByCategory(val categoryId: Long) : ProductCategoryFilter()
}

data class ProductListUiState(
    val isLoading: Boolean = true,
    val products: List<ProductEntity> = emptyList(),
    /** Every category, active or not — needed so a product's category name still resolves even
     *  if that category has since been deactivated (its products keep the association). */
    val categories: List<CategoryEntity> = emptyList(),
    val filter: ProductCategoryFilter = ProductCategoryFilter.All,
) {
    val categoriesById: Map<Long, CategoryEntity> get() = categories.associateBy { it.id }

    val activeCategories: List<CategoryEntity> get() = categories.filter { it.active }

    val filteredProducts: List<ProductEntity>
        get() = when (val f = filter) {
            ProductCategoryFilter.All -> products
            ProductCategoryFilter.Uncategorized -> products.filter { it.categoryId == null }
            is ProductCategoryFilter.ByCategory -> products.filter { it.categoryId == f.categoryId }
        }
}

/** Backs the Products list screen with the live set of products and categories from Room. */
class ProductListViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<ProductCategoryFilter>(ProductCategoryFilter.All)

    val uiState: StateFlow<ProductListUiState> = combine(
        productRepository.getAll(),
        categoryRepository.getAll(),
        filter,
    ) { products, categories, filter ->
        ProductListUiState(isLoading = false, products = products, categories = categories, filter = filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProductListUiState(),
    )

    fun setActive(productId: Long, active: Boolean) {
        viewModelScope.launch {
            productRepository.setActive(productId, active)
        }
    }

    fun setFilter(filter: ProductCategoryFilter) {
        this.filter.value = filter
    }
}
