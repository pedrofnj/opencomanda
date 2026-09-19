package com.pedroleite.opencomanda.ui.stock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warehouse
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.formatQuantity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.ui.components.SearchField
import com.pedroleite.opencomanda.ui.rememberAppContainer
import java.util.Locale

object StockTestTags {
    const val SEARCH_FIELD = "stock_search_field"
    const val EMPTY_STATE = "stock_empty_state"
    const val GO_TO_PRODUCTS_BUTTON = "stock_go_to_products"
    const val NO_RESULTS = "stock_no_results"
    const val MODE_ADD = "stock_mode_add"
    const val MODE_REMOVE = "stock_mode_remove"
    const val MODE_SET = "stock_mode_set"
    const val AMOUNT_FIELD = "stock_amount_field"
    const val PREVIEW = "stock_preview"
    const val ERROR_TEXT = "stock_error_text"
    const val CONFIRM_BUTTON = "stock_confirm_button"
    const val DONE_BUTTON = "stock_done_button"
    fun row(productId: Long) = "stock_row_$productId"
    fun adjustButton(productId: Long) = "stock_adjust_$productId"
}

@Composable
private fun rememberStockViewModel(): StockViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { StockViewModel(container.productRepository, container.categoryRepository) }
        },
    )
}

/** The operational Stock screen: every stock-controlled product with its current quantity, plus a
 *  small flow to correct it (add, remove, or set a counted total) without opening the full
 *  Product form. */
@Composable
fun StockScreen(
    onBack: () -> Unit,
    onGoToProducts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StockViewModel = rememberStockViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.phase == StockPhase.ADJUSTING) { viewModel.backToList() }
    BackHandler(enabled = uiState.phase == StockPhase.SUCCESS) { viewModel.done() }

    if (uiState.isLoading) return
    when (uiState.phase) {
        StockPhase.LIST -> StockListScreen(uiState, viewModel, onBack, onGoToProducts, modifier)
        StockPhase.ADJUSTING -> StockAdjustScreen(uiState, viewModel, modifier)
        StockPhase.SUCCESS -> StockSuccessScreen(uiState, viewModel::done, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockListScreen(
    uiState: StockUiState,
    viewModel: StockViewModel,
    onBack: () -> Unit,
    onGoToProducts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_stock)) },
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
    ) { innerPadding ->
        if (uiState.hasNoTrackedProducts) {
            StockEmptyState(onGoToProducts, Modifier.fillMaxSize().padding(innerPadding))
            return@Scaffold
        }
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 840.dp)
                    .align(Alignment.TopCenter),
            ) {
                SearchField(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    label = stringResource(R.string.stock_search_label),
                    clearDescription = stringResource(R.string.customer_search_clear),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(StockTestTags.SEARCH_FIELD),
                )
                val visible = uiState.filteredProducts
                if (visible.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stock_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .testTag(StockTestTags.NO_RESULTS),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visible, key = { it.id }) { product ->
                            StockRow(
                                product = product,
                                categoryName = product.categoryId?.let { uiState.categoriesById[it]?.name },
                                locale = locale,
                                onAdjust = { viewModel.startAdjusting(product.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockRow(
    product: ProductEntity,
    categoryName: String?,
    locale: Locale,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outOfStock = product.stockQuantity <= 0.0
    val adjustDescription = stringResource(R.string.stock_adjust_action_for, product.name)

    Card(
        modifier = modifier.fillMaxWidth().testTag(StockTestTags.row(product.id)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!product.active) {
                    Text(
                        text = stringResource(R.string.product_status_inactive),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (categoryName != null) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // "Out of stock" is spelled out in words (never colour alone), on top of the
                // number itself — a factual state, unlike an invented "low stock" threshold.
                if (outOfStock) {
                    Text(
                        text = stringResource(R.string.stock_out_of_stock),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.stock_current_label, formatQuantity(product.stockQuantity, locale)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = onAdjust,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = adjustDescription }
                    .testTag(StockTestTags.adjustButton(product.id)),
            ) {
                Text(stringResource(R.string.stock_adjust_action))
            }
        }
    }
}

@Composable
private fun StockEmptyState(onGoToProducts: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp).testTag(StockTestTags.EMPTY_STATE), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = Icons.Filled.Warehouse,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.stock_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.stock_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGoToProducts, modifier = Modifier.testTag(StockTestTags.GO_TO_PRODUCTS_BUTTON)) {
                Text(stringResource(R.string.stock_empty_cta))
            }
        }
    }
}

@Composable
private fun stockErrorMessage(error: StockErrorType): String = when (error) {
    StockErrorType.INVALID_QUANTITY -> stringResource(R.string.product_field_stock_quantity_error)
    StockErrorType.ZERO_QUANTITY -> stringResource(R.string.stock_error_zero)
    StockErrorType.NEGATIVE_RESULT -> stringResource(R.string.stock_error_negative)
    StockErrorType.NOT_TRACKED -> stringResource(R.string.stock_error_not_tracked)
    StockErrorType.PRODUCT_NOT_FOUND -> stringResource(R.string.stock_error_not_found)
    StockErrorType.FAILED -> stringResource(R.string.stock_error_failed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockAdjustScreen(uiState: StockUiState, viewModel: StockViewModel, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val product = uiState.adjustingProduct

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stock_adjust_title)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::backToList) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 560.dp)
                    .align(Alignment.TopCenter)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (product == null) {
                    // The product stopped being stock-controlled while this screen was open.
                    Text(
                        text = stringResource(R.string.stock_error_not_tracked),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(StockTestTags.ERROR_TEXT),
                    )
                    return@Column
                }

                Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.stock_current_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (product.stockQuantity <= 0.0) {
                            stringResource(R.string.stock_out_of_stock)
                        } else {
                            formatQuantity(product.stockQuantity, locale)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(StockAdjustMode.ADD, uiState.mode, R.string.stock_mode_add, StockTestTags.MODE_ADD, viewModel)
                    ModeChip(StockAdjustMode.REMOVE, uiState.mode, R.string.stock_mode_remove, StockTestTags.MODE_REMOVE, viewModel)
                    ModeChip(StockAdjustMode.SET, uiState.mode, R.string.stock_mode_set, StockTestTags.MODE_SET, viewModel)
                }

                val error = uiState.error
                OutlinedTextField(
                    value = uiState.amountText,
                    onValueChange = viewModel::onAmountTextChange,
                    modifier = Modifier.fillMaxWidth().testTag(StockTestTags.AMOUNT_FIELD),
                    label = {
                        Text(
                            stringResource(
                                if (uiState.mode == StockAdjustMode.SET) R.string.stock_amount_label_set else R.string.stock_amount_label,
                            ),
                        )
                    },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (error != null) {
                    Text(
                        text = stockErrorMessage(error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(StockTestTags.ERROR_TEXT),
                    )
                }

                val preview = uiState.previewQuantity
                if (preview != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag(StockTestTags.PREVIEW),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.stock_preview_label), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(
                                R.string.stock_change_arrow,
                                formatQuantity(product.stockQuantity, locale),
                                formatQuantity(preview, locale),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Button(
                    onClick = viewModel::confirm,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag(StockTestTags.CONFIRM_BUTTON),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.stock_confirm_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    mode: StockAdjustMode,
    selectedMode: StockAdjustMode,
    labelRes: Int,
    tag: String,
    viewModel: StockViewModel,
) {
    FilterChip(
        selected = selectedMode == mode,
        onClick = { viewModel.onModeChange(mode) },
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun StockSuccessScreen(uiState: StockUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val change = uiState.lastChange

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
                    text = stringResource(R.string.stock_updated_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (change != null) {
                    Text(
                        text = change.productName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.stock_change_arrow,
                            formatQuantity(change.previousQuantity, locale),
                            formatQuantity(change.newQuantity, locale),
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .heightIn(min = 48.dp)
                        .testTag(StockTestTags.DONE_BUTTON),
                ) {
                    Text(stringResource(R.string.quicksale_done))
                }
            }
        }
    }
}
