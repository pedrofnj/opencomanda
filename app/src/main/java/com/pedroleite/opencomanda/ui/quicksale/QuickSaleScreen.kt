package com.pedroleite.opencomanda.ui.quicksale

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import com.pedroleite.opencomanda.ui.components.AppFilterChip
import com.pedroleite.opencomanda.ui.components.Panel
import com.pedroleite.opencomanda.ui.components.ProductPickCard
import com.pedroleite.opencomanda.ui.components.WideLayoutMinWidth
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.pedroleite.opencomanda.domain.PaymentMethod
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Wide screens keep the cart open beside the products; phones show it as a bar at the bottom.
        val wide = maxWidth >= WideLayoutMinWidth
        val cartPanelWidth = if (maxWidth >= 900.dp) 380.dp else 320.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.action_quick_sale), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                if (!wide && uiState.cartLines.isNotEmpty()) {
                    QuickSaleCartSummaryBar(
                        itemCount = uiState.cartLines.sumOf { it.quantity },
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
                else -> Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (uiState.activeCategories.isNotEmpty()) {
                            QuickSaleCategoryFilterRow(
                                activeCategories = uiState.activeCategories,
                                selectedFilter = uiState.categoryFilter,
                                onFilterSelected = viewModel::setCategoryFilter,
                            )
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            gridItems(uiState.filteredProducts, key = { it.id }) { product ->
                                ProductPickCard(
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
                    if (wide) {
                        QuickSaleCartPanel(
                            uiState = uiState,
                            onContinue = viewModel::proceedToReview,
                            modifier = Modifier
                                .width(cartPanelWidth)
                                .fillMaxHeight()
                                .padding(top = 8.dp, end = 16.dp, bottom = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Phones: the running total in a graphite bar, with the way forward in saffron. */
@Composable
private fun QuickSaleCartSummaryBar(itemCount: Double, totalCents: Long, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.comanda_item_count, formatQuantity(itemCount, locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f),
                    )
                    Text(text = Money(totalCents).format(locale), style = MaterialTheme.typography.titleLarge)
                }
                ContinueButton(onClick = onContinue)
            }
        }
    }
}

@Composable
private fun ContinueButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(QuickSaleTestTags.CONTINUE_BUTTON),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
    ) {
        Text(stringResource(R.string.quicksale_continue_sale))
        Spacer(Modifier.width(6.dp))
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/** Wide screens: the cart stays visible beside the products, so the operator sees every line and
 *  the total while still adding items. */
@Composable
private fun QuickSaleCartPanel(uiState: QuickSaleUiState, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Panel(modifier = modifier, contentPadding = PaddingValues(18.dp)) {
        Text(text = stringResource(R.string.quicksale_cart_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (uiState.cartLines.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.quicksale_cart_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.cartLines, key = { it.productId }) { line ->
                    QuickSaleReviewLine(line.productName, line.quantity, line.unitPriceCents, line.subtotalCents, locale)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.quicksale_total_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(text = Money(uiState.totalCents).format(locale), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))
        ContinueButton(onClick = onContinue, enabled = uiState.canReview, modifier = Modifier.fillMaxWidth())
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
            AppFilterChip(
                selected = selectedFilter == ProductCategoryFilter.All,
                onClick = { onFilterSelected(ProductCategoryFilter.All) },
                label = stringResource(R.string.category_filter_all),
                modifier = Modifier.testTag(QuickSaleTestTags.CATEGORY_FILTER_ALL),
            )
        }
        item {
            AppFilterChip(
                selected = selectedFilter == ProductCategoryFilter.Uncategorized,
                onClick = { onFilterSelected(ProductCategoryFilter.Uncategorized) },
                label = stringResource(R.string.category_filter_uncategorized),
                modifier = Modifier.testTag(QuickSaleTestTags.CATEGORY_FILTER_UNCATEGORIZED),
            )
        }
        items(activeCategories, key = { it.id }) { category ->
            AppFilterChip(
                selected = selectedFilter == ProductCategoryFilter.ByCategory(category.id),
                onClick = { onFilterSelected(ProductCategoryFilter.ByCategory(category.id)) },
                label = category.name,
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
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.quicksale_empty_products_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGoToProducts, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.quicksale_empty_products_cta))
            }
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
                title = { Text(stringResource(R.string.quicksale_review_title), style = MaterialTheme.typography.titleLarge) },
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
                Panel(modifier = Modifier.weight(1f, fill = false), contentPadding = PaddingValues(16.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.cartLines, key = { it.productId }) { line ->
                            QuickSaleReviewLine(line.productName, line.quantity, line.unitPriceCents, line.subtotalCents, locale)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.quicksale_total_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = Money(uiState.totalCents).format(locale), style = MaterialTheme.typography.headlineLarge)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.quicksale_payment_method_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PaymentMethod.entries.forEach { method ->
                            AppFilterChip(
                                selected = uiState.paymentMethod == method,
                                onClick = { viewModel.onPaymentMethodSelected(method) },
                                label = paymentMethodLabel(method),
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
                        .heightIn(min = 56.dp)
                        .testTag(QuickSaleTestTags.CONFIRM_BUTTON),
                    shape = MaterialTheme.shapes.medium,
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
private fun QuickSaleReviewLine(
    productName: String,
    quantity: Double,
    unitPriceCents: Long,
    subtotalCents: Long,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(productName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${formatQuantity(quantity, locale)} × ${Money(unitPriceCents).format(locale)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money(subtotalCents).format(locale), style = MaterialTheme.typography.bodyLarge)
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
                Box(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(
                    text = stringResource(R.string.quicksale_sale_completed),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )

                if (summary != null) {
                    Panel(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        summary.lines.forEach { line ->
                            QuickSaleReviewLine(line.productName, line.quantity, line.unitPriceCents, line.subtotalCents, locale)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.quicksale_total_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = Money(summary.totalCents).format(locale), style = MaterialTheme.typography.headlineSmall)
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
                            .heightIn(min = 56.dp)
                            .testTag(QuickSaleTestTags.NEW_SALE_BUTTON),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(stringResource(R.string.quicksale_new_sale))
                    }
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(QuickSaleTestTags.DONE_BUTTON),
                    ) {
                        Text(stringResource(R.string.quicksale_done))
                    }
                }
            }
        }
    }
}
