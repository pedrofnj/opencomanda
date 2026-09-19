package com.pedroleite.opencomanda.ui.quicksale

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.domain.QuickSaleCartLine
import com.pedroleite.opencomanda.ui.components.paymentMethodLabel
import com.pedroleite.opencomanda.ui.products.ProductCategoryFilter
import com.pedroleite.opencomanda.ui.rememberAppContainer
import java.util.Locale

/** Stable UI-test hooks for controls whose label text is generic/reusable across the screen.
 *  Increment/decrement controls are instead found by their (per-product) content description,
 *  matching the pattern already established for Product/Customer row switches. */
object QuickSaleTestTags {
    const val CONTINUE_BUTTON = "quicksale_continue"
    const val CONFIRM_BUTTON = "quicksale_confirm"
    const val NEW_SALE_BUTTON = "quicksale_new_sale"
    const val DONE_BUTTON = "quicksale_done"
    const val CATEGORY_FILTER_ALL = "quicksale_filter_all"
    const val CATEGORY_FILTER_UNCATEGORIZED = "quicksale_filter_uncategorized"
    fun paymentMethodChip(method: PaymentMethod) = "quicksale_payment_${method.name}"
    fun categoryFilterChip(categoryId: Long) = "quicksale_filter_category_$categoryId"
}

/** Resolves the real, app-container-backed [QuickSaleViewModel]. A test can instead pass its
 *  own [QuickSaleViewModel] instance directly into [QuickSaleScreen], bypassing this. */
@Composable
private fun rememberQuickSaleViewModel(): QuickSaleViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer {
                QuickSaleViewModel(container.productRepository, container.categoryRepository, container.orderRepository)
            }
        },
    )
}

@Composable
fun QuickSaleScreen(
    onBack: () -> Unit,
    onGoToProducts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickSaleViewModel = rememberQuickSaleViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.phase == QuickSalePhase.SELECTING && uiState.cartLines.isNotEmpty()) {
        showDiscardDialog = true
    }
    BackHandler(enabled = uiState.phase == QuickSalePhase.REVIEW) {
        viewModel.backToSelecting()
    }

    when (uiState.phase) {
        QuickSalePhase.SELECTING -> QuickSaleSelectingScreen(
            uiState = uiState,
            viewModel = viewModel,
            onBack = { if (uiState.cartLines.isEmpty()) onBack() else showDiscardDialog = true },
            onGoToProducts = onGoToProducts,
            modifier = modifier,
        )
        QuickSalePhase.REVIEW -> QuickSaleReviewScreen(
            uiState = uiState,
            viewModel = viewModel,
            onBack = viewModel::backToSelecting,
            modifier = modifier,
        )
        QuickSalePhase.SUCCESS -> QuickSaleSuccessScreen(
            summary = uiState.completedSale,
            onNewSale = viewModel::startNewSale,
            onDone = onBack,
            modifier = modifier,
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.quicksale_discard_sale_title)) },
            text = { Text(stringResource(R.string.quicksale_discard_sale_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardSale()
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.quicksale_discard_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.quicksale_keep_selling_action))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSaleSelectingScreen(
    uiState: QuickSaleUiState,
    viewModel: QuickSaleViewModel,
    onBack: () -> Unit,
    onGoToProducts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_quick_sale)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.cartLines.isNotEmpty()) {
                QuickSaleCartSummaryBar(
                    totalCents = uiState.totalCents,
                    onContinue = viewModel::proceedToReview,
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Unit
            uiState.products.isEmpty() -> QuickSaleEmptyProductsState(
                onGoToProducts = onGoToProducts,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            else -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 840.dp)
                        .align(Alignment.TopCenter),
                ) {
                    if (uiState.activeCategories.isNotEmpty()) {
                        QuickSaleCategoryFilterRow(
                            activeCategories = uiState.activeCategories,
                            selectedFilter = uiState.categoryFilter,
                            onFilterSelected = viewModel::setCategoryFilter,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.filteredProducts, key = { it.id }) { product ->
                            QuickSaleProductRow(
                                product = product,
                                categoryName = product.categoryId?.let { uiState.categoriesById[it]?.name },
                                quantity = uiState.cartQuantityByProductId[product.id] ?: 0.0,
                                onAdd = { viewModel.addToCart(product) },
                                onIncrement = { viewModel.incrementQuantity(product.id) },
                                onDecrement = { viewModel.decrementQuantity(product.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSaleCartSummaryBar(totalCents: Long, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Surface(modifier = modifier, tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.quicksale_total_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Money(totalCents).format(locale),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.testTag(QuickSaleTestTags.CONTINUE_BUTTON),
            ) {
                Text(stringResource(R.string.quicksale_continue_sale))
            }
        }
    }
}

@Composable
private fun QuickSaleCategoryFilterRow(
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
                modifier = Modifier.testTag(QuickSaleTestTags.CATEGORY_FILTER_ALL),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == ProductCategoryFilter.Uncategorized,
                onClick = { onFilterSelected(ProductCategoryFilter.Uncategorized) },
                label = { Text(stringResource(R.string.category_filter_uncategorized)) },
                modifier = Modifier.testTag(QuickSaleTestTags.CATEGORY_FILTER_UNCATEGORIZED),
            )
        }
        items(activeCategories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedFilter == ProductCategoryFilter.ByCategory(category.id),
                onClick = { onFilterSelected(ProductCategoryFilter.ByCategory(category.id)) },
                label = { Text(category.name) },
                modifier = Modifier.testTag(QuickSaleTestTags.categoryFilterChip(category.id)),
            )
        }
    }
}

@Composable
private fun QuickSaleEmptyProductsState(onGoToProducts: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.quicksale_empty_products_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.quicksale_empty_products_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGoToProducts) {
                Text(stringResource(R.string.quicksale_empty_products_cta))
            }
        }
    }
}

@Composable
private fun QuickSaleProductRow(
    product: ProductEntity,
    categoryName: String?,
    quantity: Double,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val addDescription = stringResource(R.string.quicksale_add_action, product.name)
    // A tracked product at zero can't be added. The repository still enforces this; the row just
    // says so in words instead of letting the operator find out at confirmation.
    val outOfStock = product.trackStock && product.stockQuantity <= 0.0
    val addBlocked = outOfStock && quantity <= 0.0

    Card(
        onClick = onAdd,
        enabled = !addBlocked,
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
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = Money(product.priceCents).format(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (outOfStock) {
                    Text(
                        text = stringResource(R.string.stock_out_of_stock),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (categoryName != null) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (quantity <= 0.0) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (addBlocked) {
                        stringResource(R.string.stock_product_out_of_stock_description, product.name)
                    } else {
                        addDescription
                    },
                    modifier = Modifier.size(28.dp),
                    tint = if (addBlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            } else {
                QuickSaleQuantityStepper(
                    productName = product.name,
                    quantity = quantity,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                )
            }
        }
    }
}

@Composable
private fun QuickSaleQuantityStepper(
    productName: String,
    quantity: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val decreaseDescription = stringResource(R.string.quicksale_decrease_quantity, productName)
    val increaseDescription = stringResource(R.string.quicksale_increase_quantity, productName)
    val quantityDescription = stringResource(R.string.quicksale_quantity_label, productName, formatQuantity(quantity, locale))

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrement, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = decreaseDescription)
        }
        Text(
            text = formatQuantity(quantity, locale),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .widthIn(min = 28.dp)
                .semantics { contentDescription = quantityDescription },
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onIncrement, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = increaseDescription)
        }
    }
}

private fun quickSaleErrorMessage(error: QuickSaleErrorInfo, context: Context): String = when (error.type) {
    QuickSaleErrorType.INSUFFICIENT_STOCK -> context.getString(
        R.string.quicksale_error_insufficient_stock,
        error.productName.orEmpty(),
        error.availableQuantity?.let { formatQuantity(it) } ?: "0",
    )
    QuickSaleErrorType.PRODUCT_UNAVAILABLE -> context.getString(
        R.string.quicksale_error_product_unavailable,
        error.productName.orEmpty(),
    )
    QuickSaleErrorType.SAVE_FAILED -> context.getString(R.string.quicksale_error_save_failed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSaleReviewScreen(
    uiState: QuickSaleUiState,
    viewModel: QuickSaleViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(quickSaleErrorMessage(error, context))
        viewModel.dismissError()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quicksale_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 560.dp)
                    .align(Alignment.TopCenter)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.cartLines, key = { it.productId }) { line ->
                        QuickSaleReviewLine(line, locale)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.quicksale_total_label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = Money(uiState.totalCents).format(locale),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.quicksale_payment_method_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PaymentMethod.entries) { method ->
                            FilterChip(
                                selected = uiState.paymentMethod == method,
                                onClick = { viewModel.onPaymentMethodSelected(method) },
                                label = { Text(paymentMethodLabel(method)) },
                                modifier = Modifier.testTag(QuickSaleTestTags.paymentMethodChip(method)),
                            )
                        }
                    }
                }

                Button(
                    onClick = viewModel::confirmSale,
                    enabled = uiState.canConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(QuickSaleTestTags.CONFIRM_BUTTON),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.quicksale_confirm_sale))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSaleReviewLine(line: QuickSaleCartLine, locale: Locale, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(line.productName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${formatQuantity(line.quantity, locale)} × ${Money(line.unitPriceCents).format(locale)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money(line.subtotalCents).format(locale), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun QuickSaleSuccessScreen(
    summary: QuickSaleSummary?,
    onNewSale: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale

    Scaffold(modifier = modifier) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 560.dp)
                    .align(Alignment.TopCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.quicksale_sale_completed),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                if (summary != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        summary.lines.forEach { line -> QuickSaleReviewLine(line, locale) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.quicksale_total_label), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = Money(summary.totalCents).format(locale),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.quicksale_payment_method_label),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(paymentMethodLabel(summary.paymentMethod))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onNewSale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(QuickSaleTestTags.NEW_SALE_BUTTON),
                    ) {
                        Text(stringResource(R.string.quicksale_new_sale))
                    }
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(QuickSaleTestTags.DONE_BUTTON),
                    ) {
                        Text(stringResource(R.string.quicksale_done))
                    }
                }
            }
        }
    }
}
