package com.pedroleite.opencomanda.ui.home

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pedroleite.opencomanda.data.auth.AuthenticatedUser
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.ui.auth.Avatar
import com.pedroleite.opencomanda.ui.auth.LocalCurrentUser
import com.pedroleite.opencomanda.ui.auth.LocalPermissions
import com.pedroleite.opencomanda.ui.auth.roleDisplayName
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.core.formatQuantity
import com.pedroleite.opencomanda.data.local.dao.OrderDao
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.components.InkPanel
import com.pedroleite.opencomanda.ui.components.Panel
import com.pedroleite.opencomanda.ui.components.WideLayoutMinWidth
import com.pedroleite.opencomanda.ui.navigation.Destination
import com.pedroleite.opencomanda.ui.rememberAppContainer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How many open Comandas Home previews before pointing to the full list. */
private const val PHONE_COMANDA_PREVIEW = 3
private const val WIDE_COMANDA_PREVIEW = 5

@Composable
private fun rememberHomeViewModel(): HomeViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.cashRegisterRepository, container.orderRepository) }
        },
    )
}

/**
 * OpenComanda's Home. The day at a glance comes first — money received in the open cash session —
 * then the two actions a busy owner reaches for constantly (Quick Sale, New Comanda), the Comandas
 * still open, and finally the less frequent management screens. On a wide screen the glance and
 * the actions sit on the left, the open Comandas and management on the right.
 */
@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    onOpenComanda: (Long) -> Unit = {},
    onLock: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: HomeViewModel = rememberHomeViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = LocalPermissions.current

    Scaffold(modifier = modifier) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val wide = maxWidth >= WideLayoutMinWidth
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1080.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                HomeHeader(onNavigate = onNavigate, onLock = onLock, onSignOut = onSignOut)
                // Each block only appears for someone allowed to use it. The cash card is the way into
                // the Cash Register, so it shows for anyone who can open, close or review it; the
                // takings are financial information and follow [Permission.REPORTS_VIEW].
                val cash: @Composable () -> Unit = {
                    val showTakings = Permission.REPORTS_VIEW in permissions
                    if (showTakings || Permission.CASH_OPEN in permissions || Permission.CASH_CLOSE in permissions) {
                        CashCard(
                            uiState = uiState,
                            showTakings = showTakings,
                            canOpen = Permission.CASH_OPEN in permissions,
                            onClick = { onNavigate(Destination.CashRegister) },
                        )
                    }
                }
                val actions: @Composable () -> Unit = { PrimaryActions(onNavigate, permissions) }
                val comandas: @Composable () -> Unit = {
                    if (Permission.COMANDA_MANAGE in permissions) OpenComandasSection(
                        comandas = uiState.openComandas,
                        isLoading = uiState.isLoading,
                        previewCount = if (wide) WIDE_COMANDA_PREVIEW else PHONE_COMANDA_PREVIEW,
                        onSeeAll = { onNavigate(Destination.OpenComandas) },
                        onOpenComanda = onOpenComanda,
                    )
                }
                val modules: @Composable () -> Unit = { ManagementModules(onNavigate, permissions) }

                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            cash()
                            actions()
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            comandas()
                            modules()
                        }
                    }
                } else {
                    cash()
                    actions()
                    comandas()
                    modules()
                }
            }
        }
    }
}

/** Today's date and a time-of-day greeting — nothing personal, the app has no user accounts. */
@Composable
private fun HomeHeader(
    onNavigate: (Destination) -> Unit,
    onLock: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val now = remember { LocalDateTime.now() }
    val date = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(now)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    val greeting = when (now.hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = stringResource(greeting), style = MaterialTheme.typography.headlineMedium)
        }
        LocalCurrentUser.current?.let { user ->
            AccountMenu(user = user, onNavigate = onNavigate, onLock = onLock, onSignOut = onSignOut)
        }
    }
}

/** The signed-in user's avatar; its menu holds their own account and, for those allowed, administration. */
@Composable
private fun AccountMenu(
    user: AuthenticatedUser,
    onNavigate: (Destination) -> Unit,
    onLock: () -> Unit,
    onSignOut: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val openMenu = stringResource(R.string.menu_open)
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(52.dp)
                .testTag(HomeTestTags.ACCOUNT_MENU)
                .semantics { contentDescription = openMenu },
        ) {
            Avatar(user.displayName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    roleDisplayName(user.systemRole, user.roleName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            val item: @Composable (Int, () -> Unit) -> Unit = { label, action ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            item(R.string.menu_my_account) { onNavigate(Destination.Account) }
            if (user.can(Permission.USERS_MANAGE)) {
                item(R.string.menu_users) { onNavigate(Destination.Users) }
                item(R.string.menu_roles) { onNavigate(Destination.Roles) }
                item(R.string.menu_audit) { onNavigate(Destination.AuditLog) }
            }
            if (user.can(Permission.SETTINGS_MANAGE)) {
                item(R.string.menu_settings) { onNavigate(Destination.Settings) }
            }
            HorizontalDivider()
            item(R.string.menu_lock, onLock)
            item(R.string.menu_sign_out, onSignOut)
        }
    }
}

/** The open cash session's takings, or — with no session open — an invitation to open one. The
 *  whole card leads to the Cash Register. Without [showTakings] it only tells whether the register is
 *  open, so someone who operates it but may not see the figures still has a way in. */
@Composable
private fun CashCard(
    uiState: HomeUiState,
    showTakings: Boolean,
    canOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val session = uiState.openSession
    InkPanel(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        val mutedOnInk = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.action_cash_register),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            StatusPill(
                text = if (session != null) {
                    stringResource(R.string.home_cash_open_since, formatTime(session.openedAt, locale))
                } else {
                    stringResource(R.string.home_cash_closed)
                },
                active = session != null,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (session != null && showTakings) {
            Text(
                text = stringResource(R.string.home_cash_received_label),
                style = MaterialTheme.typography.bodySmall,
                color = mutedOnInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Money(uiState.totalReceivedCents).format(locale),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.14f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MethodFigure(stringResource(R.string.payment_method_cash), uiState.receivedCentsFor(PaymentMethod.CASH), mutedOnInk, Modifier.weight(1f))
                MethodFigure(stringResource(R.string.payment_method_pix), uiState.receivedCentsFor(PaymentMethod.PIX), mutedOnInk, Modifier.weight(1f))
                MethodFigure(stringResource(R.string.home_cash_card_label), uiState.receivedByCardCents, mutedOnInk, Modifier.weight(1f))
            }
        } else if (session != null) {
            CashCardAction(stringResource(R.string.home_cash_go_cta))
        } else if (!uiState.isLoading) {
            Text(
                text = stringResource(R.string.home_cash_closed_message),
                style = MaterialTheme.typography.bodyLarge,
                color = mutedOnInk,
            )
            Spacer(Modifier.height(14.dp))
            CashCardAction(stringResource(if (canOpen) R.string.home_cash_open_cta else R.string.home_cash_go_cta))
        }
    }
}

@Composable
private fun CashCardAction(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    val onInk = MaterialTheme.colorScheme.inverseOnSurface
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(onInk.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.inversePrimary else onInk.copy(alpha = 0.5f)),
        )
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MethodFigure(label: String, cents: Long, labelColor: Color, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = labelColor)
        Text(
            text = Money(cents).format(locale),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Quick Sale and New Comanda: the two most frequent actions, side by side and large. */
@Composable
private fun PrimaryActions(onNavigate: (Destination) -> Unit, permissions: Set<Permission>) {
    val canSell = Permission.SALES_CREATE in permissions
    val canComanda = Permission.COMANDA_MANAGE in permissions
    if (!canSell && !canComanda) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (canSell) BigAction(
            label = stringResource(R.string.action_quick_sale),
            supportingText = stringResource(R.string.action_quick_sale_subtitle),
            icon = Icons.Filled.Bolt,
            container = MaterialTheme.colorScheme.tertiary,
            content = MaterialTheme.colorScheme.onTertiary,
            onClick = { onNavigate(Destination.QuickSale) },
            modifier = Modifier.weight(1f),
        )
        if (canComanda) BigAction(
            label = stringResource(R.string.action_new_comanda),
            supportingText = stringResource(R.string.action_new_comanda_subtitle),
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            onClick = { onNavigate(Destination.NewComanda) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BigAction(
    label: String,
    supportingText: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 116.dp),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** A short preview of the open Comandas; the heading opens the full list. */
@Composable
private fun OpenComandasSection(
    comandas: List<OrderDao.ComandaSummary>,
    isLoading: Boolean,
    previewCount: Int,
    onSeeAll: () -> Unit,
    onOpenComanda: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onSeeAll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.action_open_comandas), style = MaterialTheme.typography.titleLarge)
            if (comandas.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = comandas.size.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Panel(modifier = Modifier.fillMaxWidth()) {
            when {
                isLoading -> Spacer(Modifier.height(72.dp))
                comandas.isEmpty() -> Text(
                    text = stringResource(R.string.home_open_comandas_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp),
                )
                else -> {
                    val shown = comandas.take(previewCount)
                    shown.forEachIndexed { index, summary ->
                        ComandaRow(summary, onClick = { onOpenComanda(summary.order.id) })
                        if (index < shown.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComandaRow(summary: OrderDao.ComandaSummary, onClick: () -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val name = summary.order.displayName.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = comandaBadge(name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(
                    R.string.home_comanda_meta,
                    stringResource(R.string.comanda_item_count, formatQuantity(summary.itemCount, locale)),
                    formatTime(summary.order.openedAt, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = Money(summary.totalCents).format(locale), style = MaterialTheme.typography.titleMedium)
    }
}

/** Products, Stock, Customers and Fiado: used less often during service, so smaller tiles. */
@Composable
private fun ManagementModules(onNavigate: (Destination) -> Unit, permissions: Set<Permission>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (Permission.PRODUCTS_MANAGE in permissions) {
            ModuleTile(stringResource(R.string.action_products), Icons.Outlined.Sell, Modifier.weight(1f)) {
                onNavigate(Destination.Products)
            }
        }
        if (Permission.STOCK_VIEW in permissions) {
            ModuleTile(stringResource(R.string.action_stock), Icons.Outlined.Inventory2, Modifier.weight(1f)) {
                onNavigate(Destination.Stock)
            }
        }
        ModuleTile(stringResource(R.string.action_customers), Icons.Outlined.Group, Modifier.weight(1f)) {
            onNavigate(Destination.Customers)
        }
        if (Permission.FIADO_VIEW in permissions) {
            ModuleTile(stringResource(R.string.action_fiado), Icons.Outlined.Handshake, Modifier.weight(1f)) {
                onNavigate(Destination.Fiado)
            }
        }
    }
}

object HomeTestTags {
    const val ACCOUNT_MENU = "home_account_menu"
}

@Composable
private fun ModuleTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 80.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Mesa 4" → "4", "João Silva" → "JS": a short, recognizable mark for a Comanda's row. */
private fun comandaBadge(name: String): String {
    val trailingNumber = Regex("(\\d+)\\s*$").find(name)?.groupValues?.get(1)
    if (trailingNumber != null) return trailingNumber.takeLast(3)
    return name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "#" }
}

private fun formatTime(millis: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
