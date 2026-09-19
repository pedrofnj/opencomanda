package com.pedroleite.opencomanda.ui.comandas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.ui.rememberAppContainer

object NewComandaTestTags {
    const val NAME_FIELD = "new_comanda_name"
    const val CUSTOMER_FIELD = "new_comanda_customer"
    const val CREATE_BUTTON = "new_comanda_create"
}

@Composable
private fun rememberNewComandaViewModel(): NewComandaViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { NewComandaViewModel(container.orderRepository, container.customerRepository) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewComandaScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewComandaViewModel = rememberNewComandaViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.createdComandaId) {
        val comandaId = uiState.createdComandaId ?: return@LaunchedEffect
        onCreated(comandaId)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comanda_new_title)) },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NewComandaTestTags.NAME_FIELD),
                label = { Text(stringResource(R.string.comanda_field_name)) },
                isError = uiState.nameError,
                supportingText = if (uiState.nameError) {
                    { Text(stringResource(R.string.comanda_field_name_error)) }
                } else {
                    null
                },
                singleLine = true,
            )

            CustomerField(
                customers = uiState.activeCustomers,
                selectedCustomerId = uiState.selectedCustomerId,
                onCustomerSelected = viewModel::onCustomerSelected,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::create,
                enabled = uiState.canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NewComandaTestTags.CREATE_BUTTON),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.comanda_create_action))
                }
            }

            if (uiState.error) {
                Text(
                    text = stringResource(R.string.comanda_create_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Optional customer picker — mirrors [com.pedroleite.opencomanda.ui.products.ProductFormScreen]'s
 *  category field: an [ExposedDropdownMenuBox] rather than a hand-rolled overlay, so the field
 *  stays visible and operable to accessibility services. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerField(
    customers: List<CustomerEntity>,
    selectedCustomerId: Long?,
    onCustomerSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val noCustomerLabel = stringResource(R.string.comanda_field_customer_none)
    val selectedCustomer = customers.find { it.id == selectedCustomerId }
    val displayText = selectedCustomer?.name ?: noCustomerLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.comanda_field_customer)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(NewComandaTestTags.CUSTOMER_FIELD),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noCustomerLabel) },
                onClick = {
                    onCustomerSelected(null)
                    expanded = false
                },
            )
            customers.forEach { customer ->
                DropdownMenuItem(
                    text = { Text(customer.name) },
                    onClick = {
                        onCustomerSelected(customer.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
