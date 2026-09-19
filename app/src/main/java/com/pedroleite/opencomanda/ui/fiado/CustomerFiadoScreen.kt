package com.pedroleite.opencomanda.ui.fiado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
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
import com.pedroleite.opencomanda.ui.components.debtStatusLabel
import com.pedroleite.opencomanda.ui.rememberAppContainer
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object CustomerFiadoTestTags {
    const val NO_DEBTS_STATE = "customer_fiado_no_debts"
    fun debtRow(debtId: Long) = "customer_fiado_debt_$debtId"
}

@Composable
private fun rememberCustomerFiadoViewModel(customerId: Long): CustomerFiadoViewModel {
    val container = rememberAppContainer()
    return viewModel(
        key = "customer_fiado_$customerId",
        factory = viewModelFactory {
            initializer {
                CustomerFiadoViewModel(
                    customerId = customerId,
                    debtRepository = container.debtRepository,
                    customerRepository = container.customerRepository,
                    orderRepository = container.orderRepository,
                )
            }
        },
    )
}

/** A single customer's Fiado detail: their total outstanding balance and each individual
 *  outstanding debt, tap-through to register a payment against it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFiadoScreen(
    customerId: Long,
    onBack: () -> Unit,
    onDebtSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomerFiadoViewModel = rememberCustomerFiadoViewModel(customerId),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalLocale.current.platformLocale

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.customer?.name.orEmpty()) },
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
        when {
            uiState.notFound -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.fiado_debt_not_found), style = MaterialTheme.typography.titleMedium)
            }
            uiState.isLoading -> Unit
            else -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.fiado_customer_total_open_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Money(uiState.totalOutstandingCents).format(locale),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (uiState.hasNoOpenDebts) {
                        Box(
                            modifier = Modifier.fillMaxSize().testTag(CustomerFiadoTestTags.NO_DEBTS_STATE),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.fiado_customer_no_debts),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(uiState.debts, key = { it.debtId }) { debt ->
                                DebtRow(debt = debt, locale = locale, onClick = { onDebtSelected(debt.debtId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtRow(debt: DebtRowInfo, locale: Locale, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dateFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(CustomerFiadoTestTags.debtRow(debt.debtId)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = debt.orderDisplayName ?: dateFormat.format(Date(debt.createdAt)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(debtStatusLabel(debt.status), style = MaterialTheme.typography.labelLarge)
            }
            if (debt.orderDisplayName != null) {
                Text(
                    text = dateFormat.format(Date(debt.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.fiado_debt_original_label, Money(debt.originalAmountCents).format(locale)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.fiado_debt_paid_label, Money(debt.paidCents).format(locale)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.fiado_debt_remaining_label, Money(debt.remainingCents).format(locale)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.fiado_register_payment_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
