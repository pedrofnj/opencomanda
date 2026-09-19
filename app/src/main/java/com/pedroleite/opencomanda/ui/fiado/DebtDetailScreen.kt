package com.pedroleite.opencomanda.ui.fiado

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.data.local.entity.DebtPaymentEntity
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.components.MoneyField
import com.pedroleite.opencomanda.ui.components.paymentMethodLabel
import com.pedroleite.opencomanda.ui.rememberAppContainer
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object DebtDetailTestTags {
    const val AMOUNT_FIELD = "debt_detail_amount"
    const val PAY_ALL_BUTTON = "debt_detail_pay_all"
    const val CONFIRM_BUTTON = "debt_detail_confirm"
    const val DONE_BUTTON = "debt_detail_done"
    fun paymentMethodChip(method: PaymentMethod) = "debt_detail_payment_${method.name}"
}

@Composable
private fun rememberDebtDetailViewModel(debtId: Long): DebtDetailViewModel {
    val container = rememberAppContainer()
    return viewModel(
        key = "debt_detail_$debtId",
        factory = viewModelFactory {
            initializer {
                DebtDetailViewModel(
                    debtId = debtId,
                    debtRepository = container.debtRepository,
                    customerRepository = container.customerRepository,
                    orderRepository = container.orderRepository,
                )
            }
        },
    )
}

private fun debtDetailErrorMessage(error: DebtDetailErrorType, context: Context): String = when (error) {
    DebtDetailErrorType.ZERO_AMOUNT -> context.getString(R.string.fiado_error_zero_amount)
    DebtDetailErrorType.OVERPAYMENT -> context.getString(R.string.fiado_error_overpayment)
    DebtDetailErrorType.METHOD_REQUIRED -> context.getString(R.string.fiado_error_payment_method_required)
    DebtDetailErrorType.PAYMENT_FAILED -> context.getString(R.string.fiado_error_payment_failed)
}

/** A single Fiado debt's detail: outstanding balance, payment history, and the form to register
 *  a new payment against it — partial or in full. */
@Composable
fun DebtDetailScreen(
    debtId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebtDetailViewModel = rememberDebtDetailViewModel(debtId),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.notFound -> DebtNotFoundScreen(onBack = onBack, modifier = modifier)
        uiState.isLoading -> Unit
        else -> when (uiState.phase) {
            DebtDetailPhase.FORM -> DebtDetailFormScreen(uiState = uiState, viewModel = viewModel, onBack = onBack, modifier = modifier)
            DebtDetailPhase.SUCCESS -> DebtPaymentSuccessScreen(uiState = uiState, onDone = onBack, modifier = modifier)
        }
    }
}

@Composable
private fun DebtNotFoundScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.fiado_debt_not_found), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtDetailFormScreen(
    uiState: DebtDetailUiState,
    viewModel: DebtDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(debtDetailErrorMessage(error, context))
        viewModel.dismissError()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.orderDisplayName ?: uiState.customerName.orEmpty()) },
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.fiado_outstanding_amount_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = Money(uiState.remainingCents).format(locale),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                MoneyField(
                    label = stringResource(R.string.fiado_payment_amount_label),
                    valueCents = uiState.amountCents,
                    onValueChange = viewModel::onAmountChanged,
                    modifier = Modifier.fillMaxWidth().testTag(DebtDetailTestTags.AMOUNT_FIELD),
                )
                OutlinedButton(
                    onClick = viewModel::payAll,
                    modifier = Modifier.testTag(DebtDetailTestTags.PAY_ALL_BUTTON),
                ) {
                    Text(stringResource(R.string.fiado_pay_all_action))
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
                                modifier = Modifier.testTag(DebtDetailTestTags.paymentMethodChip(method)),
                            )
                        }
                    }
                }

                Button(
                    onClick = viewModel::confirmPayment,
                    enabled = uiState.canConfirmPayment,
                    modifier = Modifier.fillMaxWidth().testTag(DebtDetailTestTags.CONFIRM_BUTTON),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.fiado_confirm_payment_action))
                    }
                }

                HorizontalDivider()

                Text(stringResource(R.string.fiado_payment_history_title), style = MaterialTheme.typography.titleMedium)
                if (uiState.payments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.fiado_payment_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val dateFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.payments, key = { it.id }) { payment ->
                            PaymentHistoryRow(payment, dateFormat, locale)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentHistoryRow(payment: DebtPaymentEntity, dateFormat: DateFormat, locale: Locale, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(dateFormat.format(Date(payment.paidAt)), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = paymentMethodLabel(payment.method),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money(payment.amountCents).format(locale), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DebtPaymentSuccessScreen(uiState: DebtDetailUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val result = uiState.lastPayment

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
                    text = stringResource(R.string.fiado_payment_registered_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                if (result != null) {
                    Text(
                        text = Money(result.amountCents).format(locale),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (result.settled) {
                        Text(
                            text = stringResource(R.string.fiado_debt_settled_message),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.fiado_debt_remaining_label, Money(result.remainingAfterCents).format(locale)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .testTag(DebtDetailTestTags.DONE_BUTTON),
                ) {
                    Text(stringResource(R.string.quicksale_done))
                }
            }
        }
    }
}
