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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.ui.rememberAppContainer

/** Resolves the real, app-container-backed [CategoryManagementViewModel]. A test can instead
 *  pass its own instance directly into [CategoryManagementScreen], bypassing this. */
@Composable
private fun rememberCategoryManagementViewModel(): CategoryManagementViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { CategoryManagementViewModel(container.categoryRepository) }
        },
    )
}

/** Stable UI-test hooks — more robust than matching on label text. */
object CategoryManagementTestTags {
    const val NAME_FIELD = "category_dialog_name"
    const val SAVE_BUTTON = "category_dialog_save"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryManagementViewModel = rememberCategoryManagementViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_list_title)) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = viewModel::openCreateDialog) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.category_add_fab),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Unit
            uiState.categories.isEmpty() -> CategoryListEmptyState(
                onCreateCategory = viewModel::openCreateDialog,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            else -> CategoryList(
                categories = uiState.categories,
                onEditCategory = viewModel::openEditDialog,
                onToggleActive = { category, active -> viewModel.setActive(category.id, active) },
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (uiState.dialog.isOpen) {
        CategoryDialog(
            dialogState = uiState.dialog,
            onNameChange = viewModel::onNameInputChange,
            onConfirm = viewModel::save,
            onDismiss = viewModel::dismissDialog,
        )
    }

    uiState.activationError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissActivationError,
            title = { Text(stringResource(R.string.category_activation_error_title)) },
            text = {
                Text(
                    when (error) {
                        CategoryFormError.DUPLICATE_NAME -> stringResource(R.string.category_activation_duplicate_error)
                        CategoryFormError.SAVE_FAILED -> stringResource(R.string.category_save_error)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissActivationError) {
                    Text(stringResource(R.string.category_activation_error_dismiss))
                }
            },
        )
    }
}

@Composable
private fun CategoryListEmptyState(onCreateCategory: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Sell,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.category_list_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.category_list_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onCreateCategory) {
                Text(stringResource(R.string.category_list_empty_cta))
            }
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<CategoryEntity>,
    onEditCategory: (CategoryEntity) -> Unit,
    onToggleActive: (CategoryEntity, Boolean) -> Unit,
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
            items(categories, key = { it.id }) { category ->
                CategoryListItem(
                    category = category,
                    onEdit = { onEditCategory(category) },
                    onToggleActive = { active -> onToggleActive(category, active) },
                )
            }
        }
    }
}

@Composable
private fun CategoryListItem(
    category: CategoryEntity,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeLabel = stringResource(R.string.category_status_active)
    val inactiveLabel = stringResource(R.string.category_status_inactive)
    val editDescription = stringResource(R.string.category_edit_action, category.name)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (category.active) null else TextDecoration.LineThrough,
                    color = if (category.active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!category.active) {
                    Text(
                        text = inactiveLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editDescription }) {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
            }
            Switch(
                checked = category.active,
                onCheckedChange = onToggleActive,
                modifier = Modifier.semantics {
                    contentDescription = "${category.name}: ${if (category.active) activeLabel else inactiveLabel}"
                },
            )
        }
    }
}

@Composable
private fun CategoryDialog(
    dialogState: CategoryDialogState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val titleRes = if (dialogState.isEditing) R.string.category_edit_title else R.string.category_create_title
    val errorText = when (dialogState.error) {
        CategoryFormError.DUPLICATE_NAME -> stringResource(R.string.category_duplicate_error)
        CategoryFormError.SAVE_FAILED -> stringResource(R.string.category_save_error)
        null -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialogState.nameInput,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.category_field_name)) },
                    singleLine = true,
                    isError = dialogState.nameError,
                    supportingText = if (dialogState.nameError) {
                        { Text(stringResource(R.string.category_field_name_error)) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CategoryManagementTestTags.NAME_FIELD),
                )
                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(CategoryManagementTestTags.SAVE_BUTTON),
            ) {
                Text(stringResource(R.string.category_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
