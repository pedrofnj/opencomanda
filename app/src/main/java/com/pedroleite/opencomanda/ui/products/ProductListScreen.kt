package com.pedroleite.opencomanda.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.core.formatQuantity
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.ui.rememberAppContainer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag

/** Stable UI-test hooks for the category filter chips — a chip's label can collide with a
 *  product row showing that same category name, so tests target these instead of matching text. */
object ProductListTestTags {
    const val CATEGORY_FILTER_ALL = "product_filter_all"
    const val CATEGORY_FILTER_UNCATEGORIZED = "product_filter_uncategorized"
    fun categoryFilterChip(categoryId: Long) = "product_filter_category_$categoryId"
}

/** Resolves the real, app-container-backed [ProductListViewModel]. A test can instead pass its
 *  own [ProductListViewModel] instance directly into [ProductListScreen], bypassing this. */
@Composable
private fun rememberProductListViewModel(): ProductListViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { ProductListViewModel(container.productRepository, container.categoryRepository) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    onCreateProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    onManageCategories: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductListViewModel = rememberProductListViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_products)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onManageCategories) {
                        Icon(
                            imageVector = Icons.Filled.Category,
                            contentDescription = stringResource(R.string.category_manage_action),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateProduct) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.product_add_fab),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Unit
            uiState.products.isEmpty() -> ProductListEmptyState(
                onCreateProduct = onCreateProduct,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            else -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (uiState.activeCategories.isNotEmpty()) {
                    CategoryFilterRow(
                        activeCategories = uiState.activeCategories,
                        selectedFilter = uiState.filter,
                        onFilterSelected = viewModel::setFilter,
                    )
                }
                if (uiState.filteredProducts.isEmpty()) {
                    ProductListFilterEmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    ProductList(
                        products = uiState.filteredProducts,
                        categoriesById = uiState.categoriesById,
                        onProductClick = { onEditProduct(it.id) },
                        onToggleActive = { product, active -> viewModel.setActive(product.id, active) },
                        contentPadding = PaddingValues(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    activeCategories: List<CategoryEntity>,
    selectedFilter: ProductCategoryFilter,
    onFilterSelected: (ProductCategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedFilter == ProductCategoryFilter.All,
                onClick = { onFilterSelected(ProductCategoryFilter.All) },
                label = { Text(stringResource(R.string.category_filter_all)) },
                modifier = Modifier.testTag(ProductListTestTags.CATEGORY_FILTER_ALL),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == ProductCategoryFilter.Uncategorized,
                onClick = { onFilterSelected(ProductCategoryFilter.Uncategorized) },
                label = { Text(stringResource(R.string.category_filter_uncategorized)) },
                modifier = Modifier.testTag(ProductListTestTags.CATEGORY_FILTER_UNCATEGORIZED),
            )
        }
        items(activeCategories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedFilter == ProductCategoryFilter.ByCategory(category.id),
                onClick = { onFilterSelected(ProductCategoryFilter.ByCategory(category.id)) },
                label = { Text(category.name) },
                modifier = Modifier.testTag(ProductListTestTags.categoryFilterChip(category.id)),
            )
        }
    }
}

@Composable
private fun ProductListFilterEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.product_list_filter_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProductListEmptyState(onCreateProduct: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.product_list_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.product_list_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onCreateProduct) {
                Text(stringResource(R.string.product_list_empty_cta))
            }
        }
    }
}

@Composable
private fun ProductList(
    products: List<ProductEntity>,
    categoriesById: Map<Long, CategoryEntity>,
    onProductClick: (ProductEntity) -> Unit,
    onToggleActive: (ProductEntity, Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(products, key = { it.id }) { product ->
                ProductListItem(
                    product = product,
                    categoryName = product.categoryId?.let { categoriesById[it]?.name },
                    onClick = { onProductClick(product) },
                    onToggleActive = { active -> onToggleActive(product, active) },
                )
            }
        }
    }
}

@Composable
private fun ProductListItem(
    product: ProductEntity,
    categoryName: String?,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeLabel = stringResource(R.string.product_status_active)
    val inactiveLabel = stringResource(R.string.product_status_inactive)
    val locale = LocalLocale.current.platformLocale

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (product.active) null else TextDecoration.LineThrough,
                        color = if (product.active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (!product.active) {
                        Text(
                            text = inactiveLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    text = Money(product.priceCents).format(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (categoryName != null) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (product.trackStock) {
                    Text(
                        text = stringResource(
                            R.string.product_stock_label,
                            formatQuantity(product.stockQuantity, locale),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = product.active,
                onCheckedChange = onToggleActive,
                modifier = Modifier.semantics {
                    contentDescription = "${product.name}: ${if (product.active) activeLabel else inactiveLabel}"
                },
            )
        }
    }
}
