package com.pedroleite.opencomanda.ui.admin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.entity.AuditLogEntity
import com.pedroleite.opencomanda.data.repository.AuthRepository
import com.pedroleite.opencomanda.data.repository.UserManagementRepository
import com.pedroleite.opencomanda.domain.auth.CredentialRules
import com.pedroleite.opencomanda.ui.auth.AuthPrimaryButton
import com.pedroleite.opencomanda.ui.auth.LocalCurrentUser
import com.pedroleite.opencomanda.ui.auth.SecretField
import com.pedroleite.opencomanda.ui.auth.auditActionLabel
import com.pedroleite.opencomanda.ui.auth.toAuthMessageRes
import com.pedroleite.opencomanda.ui.components.AppFilterChip
import com.pedroleite.opencomanda.ui.components.Panel
import com.pedroleite.opencomanda.ui.rememberAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------------------------------------
// My password and PIN
// ---------------------------------------------------------------------------------------------

data class AccountState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val pinPassword: String = "",
    val newPin: String = "",
    val newPinConfirm: String = "",
    val isSaving: Boolean = false,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val pinError: Int? = null,
    @param:StringRes val notice: Int? = null,
)

class AccountViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    fun update(transform: (AccountState) -> AccountState) = _state.update { transform(it).copy(passwordError = null, pinError = null) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun changePassword() {
        val s = _state.value
        val local = when {
            !CredentialRules.isValidPassword(s.newPassword) -> R.string.auth_error_invalid_password
            s.newPassword != s.newPasswordConfirm -> R.string.auth_error_password_mismatch
            else -> null
        }
        if (local != null) return _state.update { it.copy(passwordError = local) }
        launch(onError = { st, res -> st.copy(passwordError = res) }) {
            authRepository.changeOwnPassword(s.currentPassword, s.newPassword)
            _state.update { AccountState(notice = R.string.account_password_saved) }
        }
    }

    fun changePin() {
        val s = _state.value
        val local = when {
            !CredentialRules.isValidPin(s.newPin) -> R.string.auth_error_invalid_pin
            s.newPin != s.newPinConfirm -> R.string.auth_error_pin_mismatch
            else -> null
        }
        if (local != null) return _state.update { it.copy(pinError = local) }
        launch(onError = { st, res -> st.copy(pinError = res) }) {
            authRepository.changeOwnPin(s.pinPassword, s.newPin)
            _state.update { AccountState(notice = R.string.account_pin_saved) }
        }
    }

    private fun launch(onError: (AccountState, Int) -> AccountState, block: suspend () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { onError(it.copy(isSaving = false), e.toAuthMessageRes()) }
            }
        }
    }
}

@Composable
fun AccountScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: AccountViewModel = viewModel(factory = viewModelFactory { initializer { AccountViewModel(container.authRepository) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(notice))
        viewModel.dismissNotice()
    }

    AdminScaffold(title = stringResource(R.string.account_title), onBack = onBack, modifier = modifier, snackbarHostState = snackbarHostState) {
        LocalCurrentUser.current?.let { user ->
            Text("${user.displayName} · ${user.username}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_change_password), style = MaterialTheme.typography.titleMedium)
                SecretField(state.currentPassword, { v -> viewModel.update { it.copy(currentPassword = v) } }, stringResource(R.string.account_current_password))
                SecretField(state.newPassword, { v -> viewModel.update { it.copy(newPassword = v) } }, stringResource(R.string.account_new_password), supportingText = stringResource(R.string.auth_field_password_hint))
                SecretField(state.newPasswordConfirm, { v -> viewModel.update { it.copy(newPasswordConfirm = v) } }, stringResource(R.string.auth_field_password_confirm))
                state.passwordError?.let { FormError(stringResource(it)) }
                AuthPrimaryButton(stringResource(R.string.account_change_password), viewModel::changePassword, enabled = !state.isSaving)
            }
        }
        Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_change_pin), style = MaterialTheme.typography.titleMedium)
                SecretField(state.pinPassword, { v -> viewModel.update { it.copy(pinPassword = v) } }, stringResource(R.string.account_current_password))
                SecretField(state.newPin, { v -> viewModel.update { it.copy(newPin = v) } }, stringResource(R.string.account_new_pin), numeric = true)
                SecretField(state.newPinConfirm, { v -> viewModel.update { it.copy(newPinConfirm = v) } }, stringResource(R.string.auth_field_pin_confirm), numeric = true)
                state.pinError?.let { FormError(stringResource(it)) }
                AuthPrimaryButton(stringResource(R.string.account_change_pin), viewModel::changePin, enabled = !state.isSaving)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

data class SettingsState(
    val isLoading: Boolean = true,
    val establishmentName: String = "",
    val lockTimeoutMinutes: Int = AuthRepository.DEFAULT_LOCK_TIMEOUT_MINUTES,
    val isSaving: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val notice: Int? = null,
    val newRecoveryKey: String? = null,
)

class SettingsViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = authRepository.observeSettings().first()
            _state.update {
                it.copy(
                    isLoading = false,
                    establishmentName = settings?.establishmentName.orEmpty(),
                    lockTimeoutMinutes = settings?.lockTimeoutMinutes ?: AuthRepository.DEFAULT_LOCK_TIMEOUT_MINUTES,
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(establishmentName = value, errorRes = null) }
    fun onTimeoutChange(minutes: Int) = _state.update { it.copy(lockTimeoutMinutes = minutes, errorRes = null) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }
    fun dismissRecoveryKey() = _state.update { it.copy(newRecoveryKey = null) }

    fun save() {
        val s = _state.value
        run {
            authRepository.updateSettings(s.establishmentName, s.lockTimeoutMinutes)
            _state.update { it.copy(notice = R.string.settings_saved) }
        }
    }

    fun regenerateRecoveryKey(password: String) = run {
        val key = authRepository.regenerateRecoveryKey(password)
        _state.update { it.copy(newRecoveryKey = key) }
    }

    private fun run(block: suspend () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(isSaving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorRes = e.toAuthMessageRes()) }
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container.authRepository) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isOwner = LocalCurrentUser.current?.isOwner == true
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var askPassword by remember { mutableStateOf(false) }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(notice))
        viewModel.dismissNotice()
    }

    AdminScaffold(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier, snackbarHostState = snackbarHostState) {
        if (state.isLoading) return@AdminScaffold
        OutlinedTextField(
            value = state.establishmentName,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.auth_field_establishment)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.settings_lock_timeout), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthRepository.LOCK_TIMEOUT_OPTIONS.forEach { minutes ->
                AppFilterChip(
                    selected = state.lockTimeoutMinutes == minutes,
                    onClick = { viewModel.onTimeoutChange(minutes) },
                    label = stringResource(R.string.settings_lock_timeout_option, minutes),
                )
            }
        }
        state.errorRes?.let { FormError(stringResource(it)) }
        AuthPrimaryButton(stringResource(R.string.action_save), viewModel::save, enabled = !state.isSaving)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.settings_recovery_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(if (isOwner) R.string.settings_recovery_message else R.string.settings_recovery_owner_only),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isOwner) {
            OutlinedButton(onClick = { askPassword = true }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.settings_recovery_generate))
            }
        }
    }

    if (askPassword) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPassword = false },
            title = { Text(stringResource(R.string.settings_recovery_generate)) },
            text = { SecretField(password, { password = it }, stringResource(R.string.account_current_password)) },
            confirmButton = {
                TextButton(onClick = {
                    askPassword = false
                    viewModel.regenerateRecoveryKey(password)
                }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { askPassword = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.newRecoveryKey?.let { key ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.auth_recovery_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.auth_recovery_key_message))
                    Text(key, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissRecoveryKey) { Text(stringResource(R.string.auth_recovery_key_confirm)) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Activity log
// ---------------------------------------------------------------------------------------------

class AuditLogViewModel(repository: UserManagementRepository) : ViewModel() {
    val entries: StateFlow<List<AuditLogEntity>?> = repository.observeAuditLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun AuditLogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: AuditLogViewModel = viewModel(factory = viewModelFactory { initializer { AuditLogViewModel(container.userManagementRepository) } })
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }

    AdminScaffold(title = stringResource(R.string.audit_title), onBack = onBack, modifier = modifier) {
        val list = entries ?: return@AdminScaffold
        if (list.isEmpty()) {
            Text(stringResource(R.string.audit_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AdminScaffold
        }
        Panel(modifier = Modifier.fillMaxWidth()) {
            list.forEachIndexed { index, entry ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text(auditActionLabel(entry.action), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatter.format(Date(entry.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = listOfNotNull(entry.userName ?: stringResource(R.string.audit_unknown_user), entry.details).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < list.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }
}
