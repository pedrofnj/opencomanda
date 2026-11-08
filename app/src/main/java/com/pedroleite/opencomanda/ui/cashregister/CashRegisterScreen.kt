package com.pedroleite.opencomanda.ui.cashregister

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.ui.auth.hasPermission
import com.pedroleite.opencomanda.ui.components.InkPanel
import com.pedroleite.opencomanda.ui.components.Panel
import com.pedroleite.opencomanda.ui.components.WideLayoutMinWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = Money(valueCents).format(locale),
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
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

/** The frame every Cash Register step shares: a top bar with an optional subtitle, a snackbar and
 *  a centered, scrollable column that stays readable on a tablet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashRegisterScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    maxContentWidth: Dp = 560.dp,
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = maxContentWidth)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun CashRegisterEmptyScreen(
    uiState: CashRegisterUiState,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale

    CashRegisterScaffold(
        title = stringResource(R.string.action_cash_register),
        onBack = onBack,
        modifier = modifier,
        maxContentWidth = 480.dp,
    ) {
        if (uiState.isLoading) return@CashRegisterScaffold
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.inverseSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PointOfSale,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
            Text(
                text = stringResource(R.string.cash_register_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.cash_register_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (hasPermission(Permission.CASH_OPEN)) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag(CashRegisterTestTags.OPEN_BUTTON),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.cash_register_open_action))
                }
            }
        }

        val lastClosed = uiState.mostRecentClosedSession
        if (lastClosed != null) {
            Panel(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentPadding = PaddingValues(16.dp)) {
                Text(
                    text = stringResource(R.string.cash_register_last_closed_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.cash_register_closed_at_label,
                        formatDateTime(lastClosed.closedAt ?: lastClosed.openedAt, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val counted = lastClosed.closingBalanceCents
                if (counted != null) {
                    Spacer(Modifier.height(8.dp))
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

    CashRegisterScaffold(
        title = stringResource(R.string.cash_register_open_action),
        onBack = onBack,
        modifier = modifier,
        maxContentWidth = 480.dp,
        snackbarHostState = snackbarHostState,
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
                .heightIn(min = 56.dp)
                .testTag(CashRegisterTestTags.CONFIRM_OPEN_BUTTON),
            shape = MaterialTheme.shapes.medium,
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

@Composable
private fun CashRegisterDashboardScreen(
    uiState: CashRegisterUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val session = uiState.openSession ?: return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Wide screens: the drawer card and the per-method breakdown side by side.
        val wide = maxWidth >= WideLayoutMinWidth
        CashRegisterScaffold(
            title = stringResource(R.string.action_cash_register),
            subtitle = stringResource(R.string.cash_register_opened_at_label, formatDateTime(session.openedAt, locale)),
            onBack = onBack,
            maxContentWidth = if (wide) 1040.dp else 560.dp,
            bottomBar = {
                if (hasPermission(Permission.CASH_CLOSE)) Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = onClose,
                                modifier = Modifier
                                    .widthIn(max = 560.dp)
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag(CashRegisterTestTags.CLOSE_BUTTON),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                ),
                            ) {
                                Text(stringResource(R.string.cash_register_close_action))
                            }
                        }
                    }
                }
            },
        ) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    DrawerCard(uiState, Modifier.weight(1f))
                    ReceivedByMethodPanel(uiState, Modifier.weight(1f))
                }
            } else {
                DrawerCard(uiState)
                ReceivedByMethodPanel(uiState)
            }
        }
    }
}

/** What should be in the physical drawer right now: the opening float plus cash sales. */
@Composable
private fun DrawerCard(uiState: CashRegisterUiState, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val mutedOnInk = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f)
    InkPanel(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.cash_register_expected_cash_label),
            style = MaterialTheme.typography.bodyMedium,
            color = mutedOnInk,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = Money(uiState.expectedCashCents).format(locale), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.14f))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.cash_register_opening_balance_label), style = MaterialTheme.typography.bodySmall, color = mutedOnInk)
                Text(Money(uiState.openSession?.openingBalanceCents ?: 0L).format(locale), style = MaterialTheme.typography.titleSmall)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.cash_register_cash_sales_label), style = MaterialTheme.typography.bodySmall, color = mutedOnInk)
                Text(
                    text = "+ " + Money(uiState.totalsByMethod[PaymentMethod.CASH] ?: 0L).format(locale),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/** Takings per payment method, each with a bar scaled to the largest one, plus the session total. */
@Composable
private fun ReceivedByMethodPanel(uiState: CashRegisterUiState, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val received = PaymentMethod.entries.mapNotNull { method ->
        uiState.totalsByMethod[method]?.takeIf { it > 0 }?.let { method to it }
    }.sortedByDescending { it.second }
    val largest = received.maxOfOrNull { it.second } ?: 0L

    Panel(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cash_register_received_by_method_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        if (received.isEmpty()) {
            Text(
                text = stringResource(R.string.cash_register_no_payments_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                received.forEach { (method, amount) ->
                    MethodBar(
                        label = paymentMethodLabel(method),
                        amountText = Money(amount).format(locale),
                        fraction = if (largest > 0) amount.toFloat() / largest else 0f,
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.surfaceContainer)
        CashRegisterAmountRow(
            label = stringResource(R.string.cash_register_total_received_label),
            valueCents = uiState.totalReceivedCents,
            locale = locale,
            emphasize = true,
        )
    }
}

@Composable
private fun MethodBar(label: String, amountText: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(amountText, style = MaterialTheme.typography.titleSmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

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

    CashRegisterScaffold(
        title = stringResource(R.string.cash_register_close_action),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        DrawerCard(uiState)

        Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            CashRegisterAmountRow(
                label = stringResource(R.string.cash_register_total_received_label),
                valueCents = uiState.totalReceivedCents,
                locale = locale,
            )
        }

        MoneyField(
            label = stringResource(R.string.cash_register_counted_cash_label),
            valueCents = uiState.countedCashCents,
            onValueChange = viewModel::onCountedCashChanged,
            modifier = Modifier.fillMaxWidth().testTag(CashRegisterTestTags.COUNTED_CASH_FIELD),
        )

        DifferenceBox(uiState.liveDifferenceCents)

        Button(
            onClick = viewModel::confirmClose,
            enabled = uiState.canConfirmClose,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(CashRegisterTestTags.CONFIRM_CLOSE_BUTTON),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            } else {
                Text(stringResource(R.string.cash_register_confirm_close_action))
            }
        }
    }
}

/** Counted minus expected, in words and with a tinted background: saffron when over, rust-toned
 *  when short, teal when it matches — the words carry the meaning, the color only reinforces it. */
@Composable
private fun DifferenceBox(diffCents: Long, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        diffCents > 0 -> colors.tertiaryContainer to colors.onTertiaryContainer
        diffCents < 0 -> colors.errorContainer to colors.onErrorContainer
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.cash_register_difference_label), style = MaterialTheme.typography.labelLarge)
            Text(text = cashRegisterDifferenceText(diffCents, locale), style = MaterialTheme.typography.titleLarge)
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

    CashRegisterScaffold(title = "", onBack = null, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = stringResource(R.string.cash_register_closed_summary_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (summary != null) {
                Text(
                    text = stringResource(
                        R.string.cash_register_closed_at_label,
                        formatDateTime(summary.session.closedAt ?: summary.session.openedAt, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (summary != null) {
            Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceContainer)
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
                }
            }
            val diff = summary.differenceCents
            if (diff != null) {
                DifferenceBox(diff)
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 56.dp)
                .testTag(CashRegisterTestTags.DONE_BUTTON),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        ) {
            Text(stringResource(R.string.quicksale_done))
        }
    }
}
