package com.pedroleite.opencomanda.ui.fiado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.ui.rememberAppContainer
import java.util.Locale

object FiadoTestTags {
    const val EMPTY_STATE = "fiado_empty"
    fun customerRow(customerId: Long) = "fiado_customer_$customerId"
}

@Composable
private fun rememberFiadoViewModel(): FiadoViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { FiadoViewModel(container.debtRepository, container.customerRepository) }
        },
    )
}

/** The main Fiado screen: "who owes money?" — every customer with an outstanding balance,
 *  never a raw list of debt rows (see [FiadoViewModel]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiadoScreen(
    onBack: () -> Unit,
    onCustomerSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FiadoViewModel = rememberFiadoViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalLocale.current.platformLocale

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_fiado)) },
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
        if (uiState.isLoading) return@Scaffold
        when {
            uiState.isEmpty -> FiadoEmptyState(modifier = Modifier.fillMaxSize().padding(innerPadding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.customers, key = { it.customerId }) { summary ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        CustomerFiadoRow(
                            summary = summary,
                            locale = locale,
                            onClick = { onCustomerSelected(summary.customerId) },
                            modifier = Modifier.widthIn(max = 640.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerFiadoRow(
    summary: CustomerFiadoSummary,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(FiadoTestTags.customerRow(summary.customerId)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(summary.customerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.fiado_customer_open_amount, Money(summary.totalOutstandingCents).format(locale)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.fiado_customer_debt_count, summary.debtCount.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FiadoEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp).testTag(FiadoTestTags.EMPTY_STATE), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = Icons.Filled.Handshake,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.fiado_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.fiado_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
