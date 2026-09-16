package com.pedroleite.opencomanda.ui.customers

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.ui.rememberAppContainer

/** Stable UI-test hooks — more robust than matching on label text, which can collide with a
 *  customer's own name/phone. */
object CustomerListTestTags {
    const val SEARCH_FIELD = "customer_search_field"
    const val SEARCH_CLEAR = "customer_search_clear"
    const val FILTER_ALL = "customer_filter_all"
    const val FILTER_ACTIVE = "customer_filter_active"
    const val FILTER_INACTIVE = "customer_filter_inactive"
}

/** Resolves the real, app-container-backed [CustomerListViewModel]. A test can instead pass its
 *  own [CustomerListViewModel] instance directly into [CustomerListScreen], bypassing this. */
@Composable
private fun rememberCustomerListViewModel(): CustomerListViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { CustomerListViewModel(container.customerRepository) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onBack: () -> Unit,
    onCreateCustomer: () -> Unit,
    onEditCustomer: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomerListViewModel = rememberCustomerListViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_customers)) },
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
            ExtendedFloatingActionButton(onClick = onCreateCustomer) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.customer_add_fab),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Unit
            uiState.customers.isEmpty() -> CustomerListEmptyState(
                onCreateCustomer = onCreateCustomer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            else -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 840.dp)
                        .align(Alignment.TopCenter),
                ) {
                    CustomerSearchField(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    CustomerStatusFilterRow(
                        selectedFilter = uiState.statusFilter,
                        onFilterSelected = viewModel::onStatusFilterChange,
                    )
                    if (uiState.filteredCustomers.isEmpty()) {
                        CustomerListNoResultsState(modifier = Modifier.fillMaxSize())
                    } else {
                        CustomerList(
                            customers = uiState.filteredCustomers,
                            onCustomerClick = { onEditCustomer(it.id) },
                            onToggleActive = { customer, active -> viewModel.setActive(customer.id, active) },
                            contentPadding = PaddingValues(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.testTag(CustomerListTestTags.SEARCH_FIELD),
        label = { Text(stringResource(R.string.customer_search_label)) },
        singleLine = true,
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag(CustomerListTestTags.SEARCH_CLEAR),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.customer_search_clear),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun CustomerStatusFilterRow(
    selectedFilter: CustomerStatusFilter,
    onFilterSelected: (CustomerStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedFilter == CustomerStatusFilter.ALL,
                onClick = { onFilterSelected(CustomerStatusFilter.ALL) },
                label = { Text(stringResource(R.string.customer_filter_all)) },
                modifier = Modifier.testTag(CustomerListTestTags.FILTER_ALL),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == CustomerStatusFilter.ACTIVE,
                onClick = { onFilterSelected(CustomerStatusFilter.ACTIVE) },
                label = { Text(stringResource(R.string.customer_filter_active)) },
                modifier = Modifier.testTag(CustomerListTestTags.FILTER_ACTIVE),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == CustomerStatusFilter.INACTIVE,
                onClick = { onFilterSelected(CustomerStatusFilter.INACTIVE) },
                label = { Text(stringResource(R.string.customer_filter_inactive)) },
                modifier = Modifier.testTag(CustomerListTestTags.FILTER_INACTIVE),
            )
        }
    }
}

@Composable
private fun CustomerListNoResultsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.customer_list_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CustomerListEmptyState(onCreateCustomer: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.customer_list_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.customer_list_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onCreateCustomer) {
                Text(stringResource(R.string.customer_list_empty_cta))
            }
        }
    }
}

@Composable
private fun CustomerList(
    customers: List<CustomerEntity>,
    onCustomerClick: (CustomerEntity) -> Unit,
    onToggleActive: (CustomerEntity, Boolean) -> Unit,
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
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(customers, key = { it.id }) { customer ->
                CustomerListItem(
                    customer = customer,
                    onClick = { onCustomerClick(customer) },
                    onToggleActive = { active -> onToggleActive(customer, active) },
                )
            }
        }
    }
}

@Composable
private fun CustomerListItem(
    customer: CustomerEntity,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeLabel = stringResource(R.string.customer_status_active)
    val inactiveLabel = stringResource(R.string.customer_status_inactive)

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
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (customer.active) null else TextDecoration.LineThrough,
                        color = if (customer.active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (!customer.active) {
                        Text(
                            text = inactiveLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (customer.phone != null) {
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (customer.notes != null) {
                    Text(
                        text = customer.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = customer.active,
                onCheckedChange = onToggleActive,
                modifier = Modifier.semantics {
                    contentDescription = "${customer.name}: ${if (customer.active) activeLabel else inactiveLabel}"
                },
            )
        }
    }
}
