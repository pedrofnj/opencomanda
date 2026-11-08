package com.pedroleite.opencomanda.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.auth.SessionState
import com.pedroleite.opencomanda.ui.rememberAppContainer
import kotlinx.coroutines.delay

object AuthTestTags {
    const val SETUP_SUBMIT = "auth_setup_submit"
    const val SIGN_IN_SUBMIT = "auth_sign_in_submit"
    const val RECOVERY_SUBMIT = "auth_recovery_submit"
    const val RECOVERY_KEY_TEXT = "auth_recovery_key_text"
    const val RECOVERY_KEY_ACK = "auth_recovery_key_ack"
    const val RECOVERY_KEY_CONTINUE = "auth_recovery_key_continue"
    const val UNLOCK_SUBMIT = "auth_unlock_submit"
    fun userCandidate(id: Long) = "auth_candidate_$id"
}

/** How often the inactivity lock is checked. */
private const val IDLE_CHECK_INTERVAL_MILLIS = 10_000L

/**
 * The app's front door. Depending on the session it shows first-run setup, sign-in, the lock screen
 * or [content] — the app itself, with [LocalPermissions] set to what the signed-in user may do.
 * While locked, [content] stays composed underneath (hidden from touch and accessibility), so the
 * same person picks up exactly where they were — a half-built sale is not lost to a short break.
 */
@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: AuthGateViewModel = viewModel(
        factory = viewModelFactory { initializer { AuthGateViewModel(container.authRepository, container.session) } },
    )
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val pendingKey by viewModel.pendingRecoveryKey.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val key = pendingKey
    when {
        key != null -> RecoveryKeyScreen(recoveryKey = key, onContinue = viewModel::acknowledgeRecoveryKey)
        state is SessionState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        state is SessionState.NeedsSetup -> SetupScreen(onRecoveryKey = viewModel::showRecoveryKey)
        state is SessionState.SignedOut -> SignInScreen(
            establishmentName = settings?.establishmentName,
            onRecoveryKey = viewModel::showRecoveryKey,
        )
        else -> {
            val user = when (val s = state) {
                is SessionState.Active -> s.user
                is SessionState.Locked -> s.user
                else -> null
            } ?: return
            val locked = state is SessionState.Locked
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)) {
                    CompositionLocalProvider(LocalPermissions provides user.permissions, LocalCurrentUser provides user) {
                        content()
                    }
                }
                if (locked) LockScreen(user = user)
            }
            val timeoutMinutes = settings?.lockTimeoutMinutes
            if (!locked && timeoutMinutes != null) {
                LaunchedEffect(user.id, timeoutMinutes) {
                    while (true) {
                        delay(IDLE_CHECK_INTERVAL_MILLIS)
                        if (viewModel.lockIfIdle(timeoutMinutes * 60_000L)) break
                    }
                }
            }
        }
    }
}

/** Shown once after setup or recovery: the key must be acknowledged before entering the app. */
@Composable
fun RecoveryKeyScreen(recoveryKey: String, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    var acknowledged by remember { mutableStateOf(false) }
    AuthPage(modifier = modifier) {
        Text(stringResource(R.string.auth_recovery_key_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.auth_recovery_key_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = recoveryKey,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp).testTag(AuthTestTags.RECOVERY_KEY_TEXT),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .toggleable(value = acknowledged, onValueChange = { acknowledged = it }, role = Role.Checkbox)
                .testTag(AuthTestTags.RECOVERY_KEY_ACK)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = acknowledged, onCheckedChange = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.auth_recovery_key_confirm), style = MaterialTheme.typography.bodyLarge)
        }
        AuthPrimaryButton(
            text = stringResource(R.string.auth_recovery_key_continue),
            onClick = onContinue,
            enabled = acknowledged,
            modifier = Modifier.testTag(AuthTestTags.RECOVERY_KEY_CONTINUE),
        )
    }
}

/** First launch: the owner creates the Administrator account. */
@Composable
fun SetupScreen(onRecoveryKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: SetupViewModel = viewModel(factory = viewModelFactory { initializer { SetupViewModel(container.authRepository) } })
    val form by viewModel.form.collectAsStateWithLifecycle()

    AuthPage(modifier = modifier) {
        Text(stringResource(R.string.auth_setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.auth_setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = form.establishmentName,
            onValueChange = viewModel::onEstablishmentNameChange,
            label = { Text(stringResource(R.string.auth_field_establishment)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text(stringResource(R.string.auth_field_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text(stringResource(R.string.auth_field_username)) },
            supportingText = { Text(stringResource(R.string.auth_field_username_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            value = form.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_field_password),
            supportingText = stringResource(R.string.auth_field_password_hint),
        )
        SecretField(form.passwordConfirm, viewModel::onPasswordConfirmChange, stringResource(R.string.auth_field_password_confirm))
        SecretField(form.pin, viewModel::onPinChange, stringResource(R.string.auth_field_pin), numeric = true)
        SecretField(form.pinConfirm, viewModel::onPinConfirmChange, stringResource(R.string.auth_field_pin_confirm), numeric = true)
        form.errorRes?.let { ErrorText(stringResource(it)) }
        AuthPrimaryButton(
            text = stringResource(R.string.auth_setup_submit),
            onClick = { viewModel.submit(onRecoveryKey) },
            enabled = !form.isSaving,
            modifier = Modifier.testTag(AuthTestTags.SETUP_SUBMIT),
        )
    }
}

/** Sign-in: pick your name and type your PIN, or use username and password, or recover the owner. */
@Composable
fun SignInScreen(establishmentName: String?, onRecoveryKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: SignInViewModel = viewModel(factory = viewModelFactory { initializer { SignInViewModel(container.authRepository) } })
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()

    AuthPage(modifier = modifier, eyebrow = establishmentName) {
        when (val mode = ui.mode) {
            SignInMode.Users -> {
                Text(stringResource(R.string.auth_signin_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.auth_signin_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        candidates.forEachIndexed { index, candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { viewModel.choose(candidate) }
                                    .testTag(AuthTestTags.userCandidate(candidate.id))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Avatar(candidate.displayName)
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(candidate.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (index < candidates.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                        }
                    }
                }
                TextButton(onClick = viewModel::usePassword, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auth_signin_with_password))
                }
            }
            is SignInMode.Pin -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(mode.candidate.displayName, large = true)
                    Text(stringResource(R.string.auth_pin_prompt, mode.candidate.displayName), style = MaterialTheme.typography.titleLarge)
                    ui.message?.let { ErrorText(it.text(), center = true) }
                    PinPad(pin = ui.pin, onPinChange = viewModel::onPinChange, enabled = !ui.isBusy)
                    TextButton(onClick = viewModel::backToUsers) { Text(stringResource(R.string.auth_back_to_users)) }
                }
            }
            SignInMode.Password -> {
                Text(stringResource(R.string.auth_signin_with_password), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = ui.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text(stringResource(R.string.auth_field_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField(ui.password, viewModel::onPasswordChange, stringResource(R.string.auth_field_password))
                ui.message?.let { ErrorText(it.text()) }
                AuthPrimaryButton(
                    text = stringResource(R.string.auth_signin_submit),
                    onClick = viewModel::submitPassword,
                    enabled = !ui.isBusy,
                    modifier = Modifier.testTag(AuthTestTags.SIGN_IN_SUBMIT),
                )
                TextButton(onClick = viewModel::backToUsers, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auth_signin_with_pin))
                }
                TextButton(onClick = viewModel::startRecovery, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auth_forgot_admin))
                }
            }
            SignInMode.Recovery -> {
                Text(stringResource(R.string.auth_recovery_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.auth_recovery_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ui.recoveryKey,
                    onValueChange = viewModel::onRecoveryKeyChange,
                    label = { Text(stringResource(R.string.auth_field_recovery_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField(ui.newPassword, viewModel::onNewPasswordChange, stringResource(R.string.account_new_password), supportingText = stringResource(R.string.auth_field_password_hint))
                SecretField(ui.newPasswordConfirm, viewModel::onNewPasswordConfirmChange, stringResource(R.string.auth_field_password_confirm))
                SecretField(ui.newPin, viewModel::onNewPinChange, stringResource(R.string.account_new_pin), numeric = true)
                SecretField(ui.newPinConfirm, viewModel::onNewPinConfirmChange, stringResource(R.string.auth_field_pin_confirm), numeric = true)
                ui.message?.let { ErrorText(it.text()) }
                AuthPrimaryButton(
                    text = stringResource(R.string.auth_recovery_submit),
                    onClick = { viewModel.submitRecovery(onRecoveryKey) },
                    enabled = !ui.isBusy,
                    modifier = Modifier.testTag(AuthTestTags.RECOVERY_SUBMIT),
                )
                TextButton(onClick = viewModel::usePassword, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
    }
}

/** Covers the app after inactivity; the same person resumes with their PIN or password. */
@Composable
fun LockScreen(user: com.pedroleite.opencomanda.data.auth.AuthenticatedUser, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: LockViewModel = viewModel(
        key = "lock-${user.id}",
        factory = viewModelFactory { initializer { LockViewModel(container.authRepository) } },
    )
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    AuthPage(modifier = modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(user.displayName, large = true)
            Text(stringResource(R.string.auth_lock_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(if (ui.usePin) R.string.auth_lock_message else R.string.auth_lock_message_password, user.displayName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            ui.message?.let { ErrorText(it.text(), center = true) }
            if (ui.usePin) {
                PinPad(pin = ui.pin, onPinChange = viewModel::onPinChange, enabled = !ui.isBusy)
            } else {
                SecretField(ui.password, viewModel::onPasswordChange, stringResource(R.string.auth_field_password))
                AuthPrimaryButton(
                    text = stringResource(R.string.auth_lock_unlock),
                    onClick = viewModel::submitPassword,
                    enabled = !ui.isBusy,
                    modifier = Modifier.testTag(AuthTestTags.UNLOCK_SUBMIT),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::toggleMethod) {
                    Text(stringResource(if (ui.usePin) R.string.auth_lock_use_password else R.string.auth_lock_use_pin))
                }
                TextButton(onClick = viewModel::switchUser) { Text(stringResource(R.string.auth_switch_user)) }
            }
        }
    }
}

/** The shared frame of every access screen: the paper ground, a centered readable column. */
@Composable
private fun AuthPage(modifier: Modifier = Modifier, eyebrow: String? = null, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = eyebrow ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun ErrorText(text: String, center: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(
        modifier = modifier
            .size(if (large) 72.dp else 44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun AuthMessage.text(): String = when (this) {
    AuthMessage.InvalidCredentials -> stringResource(R.string.auth_error_invalid_credentials)
    AuthMessage.InvalidRecoveryKey -> stringResource(R.string.auth_error_invalid_recovery_key)
    is AuthMessage.LockedUntil -> stringResource(R.string.auth_error_locked, formatClockTime(untilMillis))
    is AuthMessage.Resource -> stringResource(resId)
}
