package com.pedroleite.opencomanda.ui.comandas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.ui.auth.hasPermission
import com.pedroleite.opencomanda.ui.components.AppFilterChip
import com.pedroleite.opencomanda.ui.components.Panel
import com.pedroleite.opencomanda.ui.components.ProductPickCard
import com.pedroleite.opencomanda.ui.components.WideLayoutMinWidth
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import com.pedroleite.opencomanda.data.local.entity.OrderItemEntity
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.components.paymentMethodLabel
import com.pedroleite.opencomanda.ui.products.ProductCategoryFilter
import com.pedroleite.opencomanda.ui.rememberAppContainer
import java.util.Locale

object ComandaDetailTestTags {
    const val ADD_PRODUCTS_BUTTON = "comanda_add_products"
    const val CLOSE_BUTTON = "comanda_close"
    const val CONFIRM_PAYMENT_BUTTON = "comanda_confirm_payment"
    const val FIADO_ACTION = "comanda_fiado_action"
    const val CONFIRM_FIADO_BUTTON = "comanda_confirm_fiado"
    const val CANCEL_ACTION = "comanda_cancel_action"
    const val CANCEL_CONFIRM = "comanda_cancel_confirm"
    const val CANCEL_DISMISS = "comanda_cancel_dismiss"
    const val DONE_BUTTON = "comanda_done"
    const val CATEGORY_FILTER_ALL = "comanda_filter_all"
    const val CATEGORY_FILTER_UNCATEGORIZED = "comanda_filter_uncategorized"
    fun paymentMethodChip(method: PaymentMethod) = "comanda_payment_${method.name}"
    fun categoryFilterChip(categoryId: Long) = "comanda_filter_category_$categoryId"
}

@Composable
private fun rememberComandaDetailViewModel(comandaId: Long): ComandaDetailViewModel {
    val container = rememberAppContainer()
    return viewModel(
        key = "comanda_detail_$comandaId",
        factory = viewModelFactory {
            initializer {
                ComandaDetailViewModel(
                    comandaId = comandaId,
                    orderRepository = container.orderRepository,
                    productRepository = container.productRepository,
                    categoryRepository = container.categoryRepository,
                    customerRepository = container.customerRepository,
                )
            }
        },
    )
}

@Composable
fun ComandaDetailScreen(
    comandaId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComandaDetailViewModel = rememberComandaDetailViewModel(comandaId),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.phase == ComandaDetailPhase.ADDING_PRODUCTS) {
        viewModel.backToDetail()
    }
    BackHandler(enabled = uiState.phase == ComandaDetailPhase.CLOSING) {
        viewModel.backToDetail()
    }
    BackHandler(enabled = uiState.phase == ComandaDetailPhase.FIADO_CONFIRM) {
        viewModel.backToClosing()
    }

    when {
        uiState.notFound -> ComandaNotFoundScreen(onBack = onBack, modifier = modifier)
        uiState.isLoading -> Unit
        else -> when (uiState.phase) {
            ComandaDetailPhase.DETAIL -> ComandaDetailMainScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = onBack,
                onCancelRequested = { showCancelDialog = true },
                modifier = modifier,
            )
            ComandaDetailPhase.ADDING_PRODUCTS -> ComandaAddProductsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = viewModel::backToDetail,
                modifier = modifier,
            )
            ComandaDetailPhase.CLOSING -> ComandaClosingScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = viewModel::backToDetail,
                modifier = modifier,
            )
            ComandaDetailPhase.FIADO_CONFIRM -> ComandaFiadoConfirmScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = viewModel::backToClosing,
                modifier = modifier,
            )
            ComandaDetailPhase.SUCCESS -> ComandaClosedScreen(
                summary = uiState.completedSale,
                onDone = onBack,
                modifier = modifier,
            )
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.comanda_cancel_dialog_title)) },
            text = { Text(stringResource(R.string.comanda_cancel_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelComanda()
                        onBack()
                    },
                    modifier = Modifier.testTag(ComandaDetailTestTags.CANCEL_CONFIRM),
                ) {
                    Text(stringResource(R.string.comanda_cancel_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelDialog = false },
                    modifier = Modifier.testTag(ComandaDetailTestTags.CANCEL_DISMISS),
                ) {
                    Text(stringResource(R.string.comanda_cancel_dialog_dismiss))
                }
            },
        )
    }
}

@Composable
private fun ComandaNotFoundScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.comanda_not_found), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComandaDetailMainScreen(
    uiState: ComandaDetailUiState,
    viewModel: ComandaDetailViewModel,
    onBack: () -> Unit,
    onCancelRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = uiState.order

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Wide screens: items on the left, the total and the actions in a summary panel on the right.
        val wide = maxWidth >= WideLayoutMinWidth
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(order?.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            if (uiState.customer != null) {
                                Text(
                                    text = "${stringResource(R.string.comanda_field_customer)}: ${uiState.customer.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        if (uiState.isOpen && hasPermission(Permission.SALES_CANCEL)) {
                            IconButton(
                                onClick = onCancelRequested,
                                modifier = Modifier.testTag(ComandaDetailTestTags.CANCEL_ACTION),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.comanda_cancel_action),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                if (uiState.isOpen && !wide) {
                    ComandaDetailActionBar(
                        totalCents = uiState.totalCents,
                        canClose = uiState.canClose,
                        onAddProducts = viewModel::startAddingProducts,
                        onClose = viewModel::proceedToClosing,
                    )
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (uiState.items.isEmpty()) {
                        ComandaEmptyItemsState(modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.comanda_item_count, formatQuantity(uiState.items.sumOf { it.quantity })),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            ComandaItemsPanel(uiState.items)
                        }
                    }
                }
                if (wide && uiState.isOpen) {
                    ComandaSummaryPanel(
                        totalCents = uiState.totalCents,
                        canClose = uiState.canClose,
                        onAddProducts = viewModel::startAddingProducts,
                        onClose = viewModel::proceedToClosing,
                        modifier = Modifier.width(340.dp).padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** Phones: total plus the two actions, pinned to the bottom. */
@Composable
private fun ComandaDetailActionBar(
    totalCents: Long,
    canClose: Boolean,
    onAddProducts: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TotalRow(totalCents, amountStyle = MaterialTheme.typography.headlineMedium)
                ComandaActions(canClose, onAddProducts, onClose, stacked = false)
            }
        }
    }
}

/** Wide screens: the same total and actions, in a panel beside the items. */
@Composable
private fun ComandaSummaryPanel(
    totalCents: Long,
    canClose: Boolean,
    onAddProducts: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier, contentPadding = PaddingValues(20.dp)) {
        Text(
            text = stringResource(R.string.quicksale_total_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = Money(totalCents).format(LocalLocale.current.platformLocale), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(20.dp))
        ComandaActions(canClose, onAddProducts, onClose, stacked = true)
    }
}

@Composable
private fun ComandaActions(canClose: Boolean, onAddProducts: () -> Unit, onClose: () -> Unit, stacked: Boolean) {
    val add: @Composable (Modifier) -> Unit = { m ->
        OutlinedButton(
            onClick = onAddProducts,
            modifier = m.heightIn(min = 52.dp).testTag(ComandaDetailTestTags.ADD_PRODUCTS_BUTTON),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(stringResource(R.string.comanda_detail_add_products_action), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val close: @Composable (Modifier) -> Unit = { m ->
        Button(
            onClick = onClose,
            enabled = canClose,
            modifier = m.heightIn(min = 52.dp).testTag(ComandaDetailTestTags.CLOSE_BUTTON),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(stringResource(R.string.comanda_detail_close_action))
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            close(Modifier.fillMaxWidth())
            add(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            add(Modifier.weight(1f))
            close(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TotalRow(totalCents: Long, amountStyle: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.quicksale_total_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = Money(totalCents).format(LocalLocale.current.platformLocale), style = amountStyle)
    }
}

@Composable
private fun ComandaEmptyItemsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.comanda_detail_empty_items_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.comanda_detail_empty_items_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A Comanda's lines in one white panel, divided by hairlines. */
@Composable
private fun ComandaItemsPanel(items: List<OrderItemEntity>, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Panel(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            ComandaItemRow(item, locale, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            if (index < items.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }
}

@Composable
private fun ComandaItemRow(item: OrderItemEntity, locale: Locale, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.productNameSnapshot, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${formatQuantity(item.quantity, locale)} × ${Money(item.unitPriceCentsSnapshot).format(locale)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money(item.subtotalCents).format(locale), style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComandaAddProductsScreen(
    uiState: ComandaDetailUiState,
    viewModel: ComandaDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(comandaErrorMessage(error, context))
        viewModel.dismissError()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.comanda_detail_add_products_action), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${uiState.order?.displayName.orEmpty()} · ${Money(uiState.totalCents).format(LocalLocale.current.platformLocale)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        when {
            uiState.products.isEmpty() -> ComandaEmptyProductsState(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            else -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (uiState.activeCategories.isNotEmpty()) {
                    ComandaCategoryFilterRow(
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
                            quantity = uiState.itemQuantityByProductId[product.id] ?: 0.0,
                            onAdd = { viewModel.addProduct(product.id) },
                            onIncrement = { viewModel.addProduct(product.id) },
                            onDecrement = { viewModel.decrementProduct(product.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComandaCategoryFilterRow(
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
                modifier = Modifier.testTag(ComandaDetailTestTags.CATEGORY_FILTER_ALL),
            )
        }
        item {
            AppFilterChip(
                selected = selectedFilter == ProductCategoryFilter.Uncategorized,
                onClick = { onFilterSelected(ProductCategoryFilter.Uncategorized) },
                label = stringResource(R.string.category_filter_uncategorized),
                modifier = Modifier.testTag(ComandaDetailTestTags.CATEGORY_FILTER_UNCATEGORIZED),
            )
        }
        items(activeCategories, key = { it.id }) { category ->
            AppFilterChip(
                selected = selectedFilter == ProductCategoryFilter.ByCategory(category.id),
                onClick = { onFilterSelected(ProductCategoryFilter.ByCategory(category.id)) },
                label = category.name,
                modifier = Modifier.testTag(ComandaDetailTestTags.categoryFilterChip(category.id)),
            )
        }
    }
}

@Composable
private fun ComandaEmptyProductsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        }
    }
}

private fun comandaErrorMessage(error: ComandaDetailError, context: Context): String = when (error.type) {
    ComandaDetailErrorType.INSUFFICIENT_STOCK -> context.getString(
        R.string.quicksale_error_insufficient_stock,
        error.productName.orEmpty(),
        error.availableQuantity?.let { formatQuantity(it) } ?: "0",
    )
    ComandaDetailErrorType.PRODUCT_UNAVAILABLE -> context.getString(
        R.string.quicksale_error_product_unavailable,
        error.productName.orEmpty(),
    )
    ComandaDetailErrorType.ADD_FAILED -> context.getString(R.string.comanda_error_add_failed)
    ComandaDetailErrorType.CLOSE_FAILED -> context.getString(R.string.comanda_error_close_failed)
}

/** The shared frame of the closing steps: a top bar, a snackbar for errors and a centered column —
 *  narrow enough to read comfortably on a tablet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComandaStepScaffold(
    title: String,
    subtitle: String?,
    uiState: ComandaDetailUiState,
    viewModel: ComandaDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(comandaErrorMessage(error, context))
        viewModel.dismissError()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 560.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/** "Total a receber" in large type, the figure the operator reads out to the customer. */
@Composable
private fun AmountDue(totalCents: Long, modifier: Modifier = Modifier) {
    Panel(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 22.dp, horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.comanda_amount_due_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = Money(totalCents).format(LocalLocale.current.platformLocale),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ComandaClosingScreen(
    uiState: ComandaDetailUiState,
    viewModel: ComandaDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComandaStepScaffold(
        title = stringResource(R.string.comanda_detail_close_action),
        subtitle = uiState.order?.displayName,
        uiState = uiState,
        viewModel = viewModel,
        onBack = onBack,
        modifier = modifier,
    ) {
        AmountDue(uiState.totalCents)
        ComandaItemsPanel(uiState.items)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        modifier = Modifier.testTag(ComandaDetailTestTags.paymentMethodChip(method)),
                    )
                }
            }
        }

        Button(
            onClick = viewModel::confirmClose,
            enabled = uiState.canConfirmClose,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(ComandaDetailTestTags.CONFIRM_PAYMENT_BUTTON),
            shape = MaterialTheme.shapes.medium,
        ) {
            if (uiState.isClosing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.comanda_confirm_payment))
            }
        }

        if (!hasPermission(Permission.FIADO_CHARGE)) return@ComandaStepScaffold
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.comanda_or_divider),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val fiadoActionDescription = if (uiState.canStartFiado) {
            stringResource(R.string.comanda_fiado_action)
        } else {
            stringResource(R.string.comanda_fiado_customer_required)
        }
        OutlinedButton(
            onClick = viewModel::startFiadoConfirm,
            enabled = uiState.canStartFiado,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics { contentDescription = fiadoActionDescription }
                .testTag(ComandaDetailTestTags.FIADO_ACTION),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(stringResource(R.string.comanda_fiado_action))
        }
        if (uiState.customer == null) {
            Text(
                text = stringResource(R.string.comanda_fiado_select_customer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ComandaFiadoConfirmScreen(
    uiState: ComandaDetailUiState,
    viewModel: ComandaDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComandaStepScaffold(
        title = stringResource(R.string.comanda_fiado_confirm_title),
        subtitle = uiState.order?.displayName,
        uiState = uiState,
        viewModel = viewModel,
        onBack = onBack,
        modifier = modifier,
    ) {
        AmountDue(uiState.totalCents)

        if (uiState.customer != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.comanda_field_customer), modifier = Modifier.weight(1f))
                    Text(uiState.customer.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        ComandaItemsPanel(uiState.items)

        Button(
            onClick = viewModel::confirmFiado,
            enabled = uiState.canConfirmFiado,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(ComandaDetailTestTags.CONFIRM_FIADO_BUTTON),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        ) {
            if (uiState.isClosing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            } else {
                Text(stringResource(R.string.comanda_fiado_confirm_action))
            }
        }
    }
}

@Composable
private fun ComandaClosedScreen(
    summary: ComandaClosedSummary?,
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
                    .verticalScroll(rememberScrollState())
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
                    text = stringResource(
                        if (summary?.isFiado == true) R.string.comanda_fiado_closed_title else R.string.comanda_closed_title,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )

                if (summary != null) {
                    Text(
                        text = summary.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (summary.isFiado && summary.customerName != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = stringResource(R.string.comanda_field_customer),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(summary.customerName, style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    ComandaItemsPanel(summary.items, modifier = Modifier.padding(top = 8.dp))

                    TotalRow(summary.totalCents, amountStyle = MaterialTheme.typography.headlineSmall)

                    if (!summary.isFiado && summary.paymentMethod != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = stringResource(R.string.quicksale_payment_method_label),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(paymentMethodLabel(summary.paymentMethod), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .heightIn(min = 56.dp)
                        .testTag(ComandaDetailTestTags.DONE_BUTTON),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                ) {
                    Text(stringResource(R.string.quicksale_done))
                }
            }
        }
    }
}
