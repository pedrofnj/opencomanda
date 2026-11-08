package com.pedroleite.opencomanda.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.auth.AuthenticatedUser
import com.pedroleite.opencomanda.data.auth.PermissionDeniedException
import com.pedroleite.opencomanda.data.repository.AuthError
import com.pedroleite.opencomanda.data.repository.AuthException
import com.pedroleite.opencomanda.domain.auth.AuditAction
import com.pedroleite.opencomanda.domain.auth.CredentialRules
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.domain.auth.SystemRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * What the signed-in user may do, for drawing the UI. Defaults to everything so a screen composed
 * on its own (previews, UI tests) shows every action; the app provides the real set from the
 * session. Hiding an action is a convenience — the repositories enforce the same rules.
 */
val LocalPermissions = staticCompositionLocalOf<Set<Permission>> { Permission.entries.toSet() }

/** The signed-in user, or null outside a session (previews, UI tests). */
val LocalCurrentUser = compositionLocalOf<AuthenticatedUser?> { null }

@Composable
fun hasPermission(permission: Permission): Boolean = permission in LocalPermissions.current

/** Shows [content] only when the user has [permission]; otherwise a plain "no access" screen. */
@Composable
fun RequirePermission(permission: Permission, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (hasPermission(permission)) content() else NoAccessScreen(onBack = onBack)
}

/** Like [RequirePermission], passing when the user has any of [permissions]. */
@Composable
fun RequireAnyPermission(permissions: Set<Permission>, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (permissions.any { it in LocalPermissions.current }) content() else NoAccessScreen(onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoAccessScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.auth_no_access_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.auth_no_access_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A password or PIN field with a show/hide toggle. */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(if (numeric) input.filter { it.isDigit() }.take(CredentialRules.PIN_LENGTH) else input)
        },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) R.string.auth_hide_secret else R.string.auth_show_secret),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Six dots that fill as digits are typed, plus a large numeric keypad — usable with a thumb. */
@Composable
fun PinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val progress = stringResource(R.string.auth_pin_progress, pin.length)
    Column(
        modifier = modifier.widthIn(max = 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.semantics { contentDescription = progress },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(CredentialRules.PIN_LENGTH) { index ->
                val filled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                )
            }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "" -> Box(Modifier.size(width = 88.dp, height = 64.dp))
                            "<" -> IconButton(
                                onClick = { onPinChange(pin.dropLast(1)) },
                                enabled = enabled && pin.isNotEmpty(),
                                modifier = Modifier.size(width = 88.dp, height = 64.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.auth_pin_delete))
                            }
                            else -> {
                                val description = stringResource(R.string.auth_pin_digit, key)
                                FilledTonalButton(
                                    onClick = { if (pin.length < CredentialRules.PIN_LENGTH) onPinChange(pin + key) },
                                    enabled = enabled,
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier
                                        .size(width = 88.dp, height = 64.dp)
                                        .semantics { contentDescription = description },
                                ) {
                                    Text(key, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A full-width primary button with the app's standard height and shape. */
@Composable
fun AuthPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text)
    }
}

/** What a failed account operation should tell the person, as a string resource. */
fun Throwable.toAuthMessageRes(): Int = when (this) {
    is PermissionDeniedException -> R.string.auth_error_permission_denied
    is AuthException -> when (error) {
        AuthError.BLANK_NAME -> R.string.auth_error_blank_name
        AuthError.INVALID_USERNAME -> R.string.auth_error_invalid_username
        AuthError.DUPLICATE_USERNAME -> R.string.auth_error_duplicate_username
        AuthError.INVALID_PASSWORD -> R.string.auth_error_invalid_password
        AuthError.INVALID_PIN -> R.string.auth_error_invalid_pin
        AuthError.WRONG_PASSWORD -> R.string.auth_error_wrong_password
        AuthError.OWNER_PROTECTED -> R.string.auth_error_owner_protected
        AuthError.SELF_PROTECTED -> R.string.auth_error_self_protected
        AuthError.ROLE_IN_USE -> R.string.auth_error_role_in_use
        AuthError.SYSTEM_ROLE_READ_ONLY -> R.string.auth_error_system_role
        AuthError.PERMISSION_NOT_GRANTABLE -> R.string.auth_error_not_grantable
        AuthError.DUPLICATE_ROLE_NAME -> R.string.auth_error_duplicate_role
        AuthError.SETUP_ALREADY_DONE, AuthError.USER_NOT_FOUND, AuthError.ROLE_NOT_FOUND -> R.string.auth_error_generic
    }
    else -> R.string.auth_error_generic
}

/** Built-in profiles are shown in the device language; custom ones by the name the admin gave them. */
@Composable
fun roleDisplayName(systemRole: SystemRole?, storedName: String): String = when (systemRole) {
    SystemRole.ADMIN -> stringResource(R.string.role_admin)
    SystemRole.MANAGER -> stringResource(R.string.role_manager)
    SystemRole.SELLER -> stringResource(R.string.role_seller)
    null -> storedName
}

@Composable
fun permissionLabel(permission: Permission): String = stringResource(
    when (permission) {
        Permission.SALES_CREATE -> R.string.perm_sales_create
        Permission.COMANDA_MANAGE -> R.string.perm_comanda_manage
        Permission.SALES_CANCEL -> R.string.perm_sales_cancel
        Permission.STOCK_VIEW -> R.string.perm_stock_view
        Permission.STOCK_ADJUST -> R.string.perm_stock_adjust
        Permission.PRODUCTS_MANAGE -> R.string.perm_products_manage
        Permission.FIADO_VIEW -> R.string.perm_fiado_view
        Permission.FIADO_CHARGE -> R.string.perm_fiado_charge
        Permission.FIADO_RECEIVE -> R.string.perm_fiado_receive
        Permission.CASH_OPEN -> R.string.perm_cash_open
        Permission.CASH_CLOSE -> R.string.perm_cash_close
        Permission.REPORTS_VIEW -> R.string.perm_reports_view
        Permission.USERS_MANAGE -> R.string.perm_users_manage
        Permission.SETTINGS_MANAGE -> R.string.perm_settings_manage
    },
)

@Composable
fun auditActionLabel(action: AuditAction): String = stringResource(
    when (action) {
        AuditAction.SETUP_COMPLETED -> R.string.audit_action_setup_completed
        AuditAction.LOGIN_SUCCEEDED -> R.string.audit_action_login_succeeded
        AuditAction.LOGIN_FAILED -> R.string.audit_action_login_failed
        AuditAction.ACCOUNT_LOCKED -> R.string.audit_action_account_locked
        AuditAction.LOGOUT -> R.string.audit_action_logout
        AuditAction.RECOVERY_USED -> R.string.audit_action_recovery_used
        AuditAction.RECOVERY_FAILED -> R.string.audit_action_recovery_failed
        AuditAction.RECOVERY_KEY_REGENERATED -> R.string.audit_action_recovery_key_regenerated
        AuditAction.PASSWORD_CHANGED -> R.string.audit_action_password_changed
        AuditAction.PIN_CHANGED -> R.string.audit_action_pin_changed
        AuditAction.USER_CREATED -> R.string.audit_action_user_created
        AuditAction.USER_UPDATED -> R.string.audit_action_user_updated
        AuditAction.USER_DEACTIVATED -> R.string.audit_action_user_deactivated
        AuditAction.USER_REACTIVATED -> R.string.audit_action_user_reactivated
        AuditAction.PASSWORD_RESET -> R.string.audit_action_password_reset
        AuditAction.PIN_RESET -> R.string.audit_action_pin_reset
        AuditAction.ROLE_CREATED -> R.string.audit_action_role_created
        AuditAction.ROLE_UPDATED -> R.string.audit_action_role_updated
        AuditAction.ROLE_DELETED -> R.string.audit_action_role_deleted
        AuditAction.SETTINGS_CHANGED -> R.string.audit_action_settings_changed
        AuditAction.PERMISSION_DENIED -> R.string.audit_action_permission_denied
        AuditAction.CASH_OPENED -> R.string.audit_action_cash_opened
        AuditAction.CASH_CLOSED -> R.string.audit_action_cash_closed
        AuditAction.COMANDA_CANCELLED -> R.string.audit_action_comanda_cancelled
        AuditAction.STOCK_ADJUSTED -> R.string.audit_action_stock_adjusted
    },
)

/** "14:32" in the device's locale — for lockout messages. */
@Composable
fun formatClockTime(millis: Long): String {
    val locale = LocalLocale.current.platformLocale
    return clockTime(millis, locale)
}

fun clockTime(millis: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "João Silva" → "JS": an avatar mark for a user. */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
