package com.pedroleite.opencomanda.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.ui.components.MoneyField
import com.pedroleite.opencomanda.ui.rememberAppContainer

/** Resolves the real, app-container-backed [ProductFormViewModel]. A test can instead pass its
 *  own [ProductFormViewModel] instance directly into [ProductFormScreen], bypassing this. */
@Composable
private fun rememberProductFormViewModel(): ProductFormViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { ProductFormViewModel(container.productRepository, container.categoryRepository) }
        },
    )
}

/** Stable UI-test hooks for [ProductForm]'s fields — more robust than matching on label text,
 *  which can collide with supporting/error text rendered near the same field. */
object ProductFormTestTags {
    const val NAME_FIELD = "product_form_name"
    const val PRICE_FIELD = "product_form_price"
    const val TRACK_STOCK_TOGGLE = "product_form_track_stock_toggle"
    const val STOCK_QUANTITY_FIELD = "product_form_stock_quantity"
    const val CATEGORY_FIELD = "product_form_category"
    const val SAVE_BUTTON = "product_form_save"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductFormViewModel = rememberProductFormViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(productId) {
        if (productId != null) viewModel.loadForEditing(productId)
    }

    LaunchedEffect(uiState.saveComplete) {
        if (uiState.saveComplete) onSaved()
    }

    val titleRes = if (uiState.isEditing) R.string.product_edit_title else R.string.product_create_title

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ProductForm(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onDescriptionChange = viewModel::onDescriptionChange,
                onPriceChange = viewModel::onPriceChange,
                onHasCostChange = viewModel::onHasCostChange,
                onCostChange = viewModel::onCostChange,
                onTrackStockChange = viewModel::onTrackStockChange,
                onStockQuantityTextChange = viewModel::onStockQuantityTextChange,
                onCategoryChange = viewModel::onCategoryChange,
                onSubmit = viewModel::submit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(20.dp),
            )
        }
    }
}

@Composable
private fun ProductForm(
    uiState: ProductFormUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriceChange: (Long) -> Unit,
    onHasCostChange: (Boolean) -> Unit,
    onCostChange: (Long) -> Unit,
    onTrackStockChange: (Boolean) -> Unit,
    onStockQuantityTextChange: (String) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.product_field_name)) },
            singleLine = true,
            isError = uiState.nameError,
            supportingText = if (uiState.nameError) {
                { Text(stringResource(R.string.product_field_name_error)) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProductFormTestTags.NAME_FIELD),
        )

        OutlinedTextField(
            value = uiState.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.product_field_description)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        MoneyField(
            label = stringResource(R.string.product_field_price),
            valueCents = uiState.priceCents,
            onValueChange = onPriceChange,
            supportingText = if (uiState.isPriceZero) {
                stringResource(R.string.product_field_price_zero_hint)
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProductFormTestTags.PRICE_FIELD),
        )

        ToggleRow(
            label = stringResource(R.string.product_field_cost_toggle),
            checked = uiState.hasCost,
            onCheckedChange = onHasCostChange,
        )
        if (uiState.hasCost) {
            MoneyField(
                label = stringResource(R.string.product_field_cost),
                valueCents = uiState.costCents,
                onValueChange = onCostChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ToggleRow(
            label = stringResource(R.string.product_field_track_stock_toggle),
            checked = uiState.trackStock,
            onCheckedChange = onTrackStockChange,
            switchModifier = Modifier.testTag(ProductFormTestTags.TRACK_STOCK_TOGGLE),
        )
        if (uiState.trackStock) {
            OutlinedTextField(
                value = uiState.stockQuantityText,
                onValueChange = onStockQuantityTextChange,
                label = { Text(stringResource(R.string.product_field_stock_quantity)) },
                singleLine = true,
                isError = uiState.stockQuantityError,
                supportingText = if (uiState.stockQuantityError) {
                    { Text(stringResource(R.string.product_field_stock_quantity_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProductFormTestTags.STOCK_QUANTITY_FIELD),
            )
        }

        CategoryField(
            categories = uiState.categories,
            selectedCategoryId = uiState.categoryId,
            selectableCategories = uiState.selectableCategories,
            onCategorySelected = onCategoryChange,
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState.error == ProductFormError.SAVE_FAILED) {
            Text(
                text = stringResource(R.string.product_save_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = onSubmit,
            enabled = uiState.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProductFormTestTags.SAVE_BUTTON),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.product_save))
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = switchModifier)
    }
}

/** A read-only "field" that opens a dropdown of selectable categories on tap.
 *
 *  Uses Material3's [ExposedDropdownMenuBox] rather than a hand-rolled clickable overlay on top
 *  of a plain [OutlinedTextField]: an overlay sitting on top of the field visually looks right,
 *  but a fully-covered node is pruned from the accessibility tree, leaving the field invisible
 *  to TalkBack — [ExposedDropdownMenuBox] wires up the click target and accessibility semantics
 *  together correctly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    selectableCategories: List<CategoryEntity>,
    onCategorySelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val noCategoryLabel = stringResource(R.string.product_field_category_none)
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val displayText = when {
        selectedCategory == null -> noCategoryLabel
        selectedCategory.active -> selectedCategory.name
        else -> stringResource(R.string.product_field_category_inactive_suffix, selectedCategory.name)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.product_field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(ProductFormTestTags.CATEGORY_FIELD),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noCategoryLabel) },
                onClick = {
                    onCategorySelected(null)
                    expanded = false
                },
            )
            selectableCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
