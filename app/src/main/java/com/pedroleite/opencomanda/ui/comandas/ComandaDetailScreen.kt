package com.pedroleite.opencomanda.ui.comandas

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
import androidx.compose.material.icons.filled.Delete
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
import com.pedroleite.opencomanda.data.local.entity.OrderItemEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
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
    val locale = LocalLocale.current.platformLocale
    val order = uiState.order

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(order?.displayName.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (uiState.isOpen) {
                        IconButton(
                            onClick = onCancelRequested,
                            modifier = Modifier.testTag(ComandaDetailTestTags.CANCEL_ACTION),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.comanda_cancel_action),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.isOpen) {
                ComandaDetailActionBar(
                    canClose = uiState.canClose,
                    onAddProducts = viewModel::startAddingProducts,
                    onClose = viewModel::proceedToClosing,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 840.dp)
                    .align(Alignment.TopCenter),
            ) {
                if (uiState.customer != null) {
                    Text(
                        text = "${stringResource(R.string.comanda_field_customer)}: ${uiState.customer.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (uiState.items.isEmpty()) {
                    ComandaEmptyItemsState(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            ComandaItemRow(item, locale)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.quicksale_total_label), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = Money(uiState.totalCents).format(locale),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComandaDetailActionBar(
    canClose: Boolean,
    onAddProducts: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onAddProducts,
                modifier = Modifier.weight(1f).testTag(ComandaDetailTestTags.ADD_PRODUCTS_BUTTON),
            ) {
                Text(stringResource(R.string.comanda_detail_add_products_action))
            }
            Button(
                onClick = onClose,
                enabled = canClose,
                modifier = Modifier.weight(1f).testTag(ComandaDetailTestTags.CLOSE_BUTTON),
            ) {
                Text(stringResource(R.string.comanda_detail_close_action))
            }
        }
    }
}

@Composable
private fun ComandaEmptyItemsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.comanda_detail_empty_items_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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

@Composable
private fun ComandaItemRow(item: OrderItemEntity, locale: Locale, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(item.productNameSnapshot, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${formatQuantity(item.quantity, locale)} × ${Money(item.unitPriceCentsSnapshot).format(locale)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money(item.subtotalCents).format(locale), style = MaterialTheme.typography.bodyLarge)
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
                title = { Text(stringResource(R.string.comanda_detail_add_products_action)) },
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
        when {
            uiState.products.isEmpty() -> ComandaEmptyProductsState(
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
                        ComandaCategoryFilterRow(
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
                            ComandaProductRow(
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
            FilterChip(
                selected = selectedFilter == ProductCategoryFilter.All,
                onClick = { onFilterSelected(ProductCategoryFilter.All) },
                label = { Text(stringResource(R.string.category_filter_all)) },
                modifier = Modifier.testTag(ComandaDetailTestTags.CATEGORY_FILTER_ALL),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == ProductCategoryFilter.Uncategorized,
                onClick = { onFilterSelected(ProductCategoryFilter.Uncategorized) },
                label = { Text(stringResource(R.string.category_filter_uncategorized)) },
                modifier = Modifier.testTag(ComandaDetailTestTags.CATEGORY_FILTER_UNCATEGORIZED),
            )
        }
        items(activeCategories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedFilter == ProductCategoryFilter.ByCategory(category.id),
                onClick = { onFilterSelected(ProductCategoryFilter.ByCategory(category.id)) },
                label = { Text(category.name) },
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
        }
    }
}

@Composable
private fun ComandaProductRow(
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

    Card(
        onClick = onAdd,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                    contentDescription = addDescription,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                ComandaQuantityStepper(
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
private fun ComandaQuantityStepper(
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
            modifier = Modifier.widthIn(min = 28.dp).semantics { contentDescription = quantityDescription },
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onIncrement, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = increaseDescription)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComandaClosingScreen(
    uiState: ComandaDetailUiState,
    viewModel: ComandaDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
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
                title = { Text(stringResource(R.string.comanda_detail_close_action)) },
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
                    items(uiState.items, key = { it.id }) { item ->
                        ComandaItemRow(item, locale)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                        .testTag(ComandaDetailTestTags.CONFIRM_PAYMENT_BUTTON),
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
                        .semantics { contentDescription = fiadoActionDescription }
                        .testTag(ComandaDetailTestTags.FIADO_ACTION),
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComandaFiadoConfirmScreen(
    uiState: ComandaDetailUiState,
    viewModel: ComandaDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
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
                title = { Text(stringResource(R.string.comanda_fiado_confirm_title)) },
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
                Text(
                    text = uiState.order?.displayName.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (uiState.customer != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(R.string.comanda_field_customer),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(uiState.customer.name, fontWeight = FontWeight.SemiBold)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        ComandaItemRow(item, locale)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.quicksale_total_label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = Money(uiState.totalCents).format(locale),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = viewModel::confirmFiado,
                    enabled = uiState.canConfirmFiado,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ComandaDetailTestTags.CONFIRM_FIADO_BUTTON),
                ) {
                    if (uiState.isClosing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.comanda_fiado_confirm_action))
                    }
                }
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
                    text = stringResource(
                        if (summary?.isFiado == true) R.string.comanda_fiado_closed_title else R.string.comanda_closed_title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
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
                            Text(summary.customerName, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        summary.items.forEach { item -> ComandaItemRow(item, locale) }
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

                    if (!summary.isFiado && summary.paymentMethod != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = stringResource(R.string.quicksale_payment_method_label),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(paymentMethodLabel(summary.paymentMethod))
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .testTag(ComandaDetailTestTags.DONE_BUTTON),
                ) {
                    Text(stringResource(R.string.quicksale_done))
                }
            }
        }
    }
}
