package com.pedroleite.opencomanda.ui.cashregister

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.data.repository.CashSessionSummary
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.components.MoneyField
import com.pedroleite.opencomanda.ui.components.paymentMethodLabel
import com.pedroleite.opencomanda.ui.rememberAppContainer
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object CashRegisterTestTags {
    const val OPEN_BUTTON = "cash_register_open"
    const val OPENING_BALANCE_FIELD = "cash_register_opening_balance"
    const val OPENING_NOTES_FIELD = "cash_register_opening_notes"
    const val CONFIRM_OPEN_BUTTON = "cash_register_confirm_open"
    const val CLOSE_BUTTON = "cash_register_close"
    const val COUNTED_CASH_FIELD = "cash_register_counted_cash"
    const val CONFIRM_CLOSE_BUTTON = "cash_register_confirm_close"
    const val DONE_BUTTON = "cash_register_done"
}

/** Resolves the real, app-container-backed [CashRegisterViewModel]. A test can instead pass its
 *  own [CashRegisterViewModel] instance directly into [CashRegisterScreen], bypassing this. */
@Composable
private fun rememberCashRegisterViewModel(): CashRegisterViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { CashRegisterViewModel(container.cashRegisterRepository) }
        },
    )
}

@Composable
fun CashRegisterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashRegisterViewModel = rememberCashRegisterViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.phase == CashRegisterPhase.OPENING) { viewModel.backToEmpty() }
    BackHandler(enabled = uiState.phase == CashRegisterPhase.CLOSING) { viewModel.backToDashboard() }

    when (uiState.phase) {
        CashRegisterPhase.EMPTY -> CashRegisterEmptyScreen(
            uiState = uiState,
            onBack = onBack,
            onOpen = viewModel::startOpening,
            modifier = modifier,
        )
        CashRegisterPhase.OPENING -> CashRegisterOpeningScreen(
            uiState = uiState,
            viewModel = viewModel,
            onBack = viewModel::backToEmpty,
            modifier = modifier,
        )
        CashRegisterPhase.DASHBOARD -> CashRegisterDashboardScreen(
            uiState = uiState,
            onBack = onBack,
            onClose = viewModel::startClosing,
            modifier = modifier,
        )
        CashRegisterPhase.CLOSING -> CashRegisterClosingScreen(
            uiState = uiState,
            viewModel = viewModel,
            onBack = viewModel::backToDashboard,
            modifier = modifier,
        )
        CashRegisterPhase.SUMMARY -> CashRegisterSummaryScreen(
            summary = uiState.closedSummary,
            onDone = viewModel::doneWithSummary,
            modifier = modifier,
        )
    }
}

private fun formatDateTime(millis: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(millis))

@Composable
private fun CashRegisterAmountRow(
    label: String,
    valueCents: Long,
    locale: Locale,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = Money(valueCents).format(locale),
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Counted vs. expected is always spelled out in words ("a mais"/"a menos"/"bateu certinho"),
 *  never left for color alone to communicate — a colorblind operator, or a black-and-white
 *  printout of the screen, still needs to be able to tell over from short. */
@Composable
private fun cashRegisterDifferenceText(diffCents: Long, locale: Locale): String = when {
    diffCents > 0 -> stringResource(R.string.cash_register_difference_positive, Money(diffCents).format(locale))
    diffCents < 0 -> stringResource(R.string.cash_register_difference_negative, Money(-diffCents).format(locale))
    else -> stringResource(R.string.cash_register_difference_zero)
}

private fun cashRegisterErrorMessage(error: CashRegisterErrorType, context: Context): String = when (error) {
    CashRegisterErrorType.OPEN_FAILED -> context.getString(R.string.cash_register_error_open_failed)
    CashRegisterErrorType.CLOSE_FAILED -> context.getString(R.string.cash_register_error_close_failed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashRegisterEmptyScreen(
    uiState: CashRegisterUiState,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_cash_register)) },
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PointOfSale,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.cash_register_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.cash_register_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onOpen, modifier = Modifier.testTag(CashRegisterTestTags.OPEN_BUTTON)) {
                    Text(stringResource(R.string.cash_register_open_action))
                }

                val lastClosed = uiState.mostRecentClosedSession
                if (lastClosed != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cash_register_last_closed_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.cash_register_closed_at_label,
                                    formatDateTime(lastClosed.closedAt ?: lastClosed.openedAt, locale),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val counted = lastClosed.closingBalanceCents
                            if (counted != null) {
                                CashRegisterAmountRow(
                                    label = stringResource(R.string.cash_register_counted_cash_label),
                                    valueCents = counted,
                                    locale = locale,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashRegisterOpeningScreen(
    uiState: CashRegisterUiState,
    viewModel: CashRegisterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(cashRegisterErrorMessage(error, context))
        viewModel.dismissError()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cash_register_open_action)) },
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
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MoneyField(
                    label = stringResource(R.string.cash_register_opening_balance_label),
                    valueCents = uiState.openingBalanceCents,
                    onValueChange = viewModel::onOpeningBalanceChanged,
                    modifier = Modifier.fillMaxWidth().testTag(CashRegisterTestTags.OPENING_BALANCE_FIELD),
                )
                OutlinedTextField(
                    value = uiState.openingNotes,
                    onValueChange = viewModel::onOpeningNotesChanged,
                    label = { Text(stringResource(R.string.cash_register_opening_notes_label)) },
                    modifier = Modifier.fillMaxWidth().testTag(CashRegisterTestTags.OPENING_NOTES_FIELD),
                )
                Button(
                    onClick = viewModel::confirmOpen,
                    enabled = uiState.canConfirmOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CashRegisterTestTags.CONFIRM_OPEN_BUTTON),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.cash_register_confirm_open_action))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashRegisterDashboardScreen(
    uiState: CashRegisterUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val session = uiState.openSession ?: return

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_cash_register)) },
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
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .testTag(CashRegisterTestTags.CLOSE_BUTTON),
                ) {
                    Text(stringResource(R.string.cash_register_close_action))
                }
            }
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
                Text(
                    text = stringResource(R.string.cash_register_opened_at_label, formatDateTime(session.openedAt, locale)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CashRegisterAmountRow(
                    label = stringResource(R.string.cash_register_opening_balance_label),
                    valueCents = session.openingBalanceCents,
                    locale = locale,
                )

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.cash_register_received_by_method_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.totalsByMethod.values.none { it > 0 }) {
                    Text(
                        text = stringResource(R.string.cash_register_no_payments_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PaymentMethod.entries.forEach { method ->
                        val amount = uiState.totalsByMethod[method]
                        if (amount != null && amount > 0) {
                            CashRegisterAmountRow(label = paymentMethodLabel(method), valueCents = amount, locale = locale)
                        }
                    }
                }

                HorizontalDivider()

                CashRegisterAmountRow(
                    label = stringResource(R.string.cash_register_total_received_label),
                    valueCents = uiState.totalReceivedCents,
                    locale = locale,
                    emphasize = true,
                )
                CashRegisterAmountRow(
                    label = stringResource(R.string.cash_register_expected_cash_label),
                    valueCents = uiState.expectedCashCents,
                    locale = locale,
                    emphasize = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashRegisterClosingScreen(
    uiState: CashRegisterUiState,
    viewModel: CashRegisterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(cashRegisterErrorMessage(error, context))
        viewModel.dismissError()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cash_register_close_action)) },
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
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CashRegisterAmountRow(
                    label = stringResource(R.string.cash_register_total_received_label),
                    valueCents = uiState.totalReceivedCents,
                    locale = locale,
                )
                CashRegisterAmountRow(
                    label = stringResource(R.string.cash_register_expected_cash_label),
                    valueCents = uiState.expectedCashCents,
                    locale = locale,
                    emphasize = true,
                )

                MoneyField(
                    label = stringResource(R.string.cash_register_counted_cash_label),
                    valueCents = uiState.countedCashCents,
                    onValueChange = viewModel::onCountedCashChanged,
                    modifier = Modifier.fillMaxWidth().testTag(CashRegisterTestTags.COUNTED_CASH_FIELD),
                )

                Column {
                    Text(
                        text = stringResource(R.string.cash_register_difference_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = cashRegisterDifferenceText(uiState.liveDifferenceCents, locale),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = viewModel::confirmClose,
                    enabled = uiState.canConfirmClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CashRegisterTestTags.CONFIRM_CLOSE_BUTTON),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.cash_register_confirm_close_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun CashRegisterSummaryScreen(
    summary: CashSessionSummary?,
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
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
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
                    text = stringResource(R.string.cash_register_closed_summary_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                if (summary != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.cash_register_closed_at_label,
                                formatDateTime(summary.session.closedAt ?: summary.session.openedAt, locale),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CashRegisterAmountRow(
                            label = stringResource(R.string.cash_register_opening_balance_label),
                            valueCents = summary.session.openingBalanceCents,
                            locale = locale,
                        )
                        PaymentMethod.entries.forEach { method ->
                            val amount = summary.receivedCentsFor(method)
                            if (amount > 0) {
                                CashRegisterAmountRow(label = paymentMethodLabel(method), valueCents = amount, locale = locale)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        CashRegisterAmountRow(
                            label = stringResource(R.string.cash_register_total_received_label),
                            valueCents = summary.totalReceivedCents,
                            locale = locale,
                            emphasize = true,
                        )
                        CashRegisterAmountRow(
                            label = stringResource(R.string.cash_register_expected_cash_label),
                            valueCents = summary.expectedCashCents,
                            locale = locale,
                        )
                        val counted = summary.session.closingBalanceCents
                        if (counted != null) {
                            CashRegisterAmountRow(
                                label = stringResource(R.string.cash_register_counted_cash_label),
                                valueCents = counted,
                                locale = locale,
                            )
                        }
                        val diff = summary.differenceCents
                        if (diff != null) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    text = stringResource(R.string.cash_register_difference_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = cashRegisterDifferenceText(diff, locale),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .testTag(CashRegisterTestTags.DONE_BUTTON),
                ) {
                    Text(stringResource(R.string.quicksale_done))
                }
            }
        }
    }
}
