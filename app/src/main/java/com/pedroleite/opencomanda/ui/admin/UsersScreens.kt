package com.pedroleite.opencomanda.ui.admin

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.repository.ManagedUser
import com.pedroleite.opencomanda.data.repository.RoleWithPermissions
import com.pedroleite.opencomanda.data.repository.UserManagementRepository
import com.pedroleite.opencomanda.domain.auth.CredentialRules
import com.pedroleite.opencomanda.ui.auth.AuthPrimaryButton
import com.pedroleite.opencomanda.ui.auth.Avatar
import com.pedroleite.opencomanda.ui.auth.LocalCurrentUser
import com.pedroleite.opencomanda.ui.auth.SecretField
import com.pedroleite.opencomanda.ui.auth.roleDisplayName
import com.pedroleite.opencomanda.ui.auth.toAuthMessageRes
import com.pedroleite.opencomanda.ui.components.Panel
import com.pedroleite.opencomanda.ui.rememberAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object UsersTestTags {
    const val ADD_FAB = "users_add"
    const val SAVE_BUTTON = "user_form_save"
    fun userRow(id: Long) = "users_row_$id"
}

class UsersViewModel(repository: UserManagementRepository) : ViewModel() {
    val users: StateFlow<List<ManagedUser>?> = repository.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun UsersScreen(onBack: () -> Unit, onCreate: () -> Unit, onEdit: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: UsersViewModel = viewModel(factory = viewModelFactory { initializer { UsersViewModel(container.userManagementRepository) } })
    val users by viewModel.users.collectAsStateWithLifecycle()

    AdminScaffold(
        title = stringResource(R.string.users_title),
        onBack = onBack,
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, modifier = Modifier.testTag(UsersTestTags.ADD_FAB)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.users_add), modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) {
        val list = users ?: return@AdminScaffold
        Panel(modifier = Modifier.fillMaxWidth()) {
            list.forEachIndexed { index, user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onEdit(user.id) }
                        .testTag(UsersTestTags.userRow(user.id))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Avatar(user.displayName)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${user.username} · ${roleDisplayName(user.systemRole, user.roleName)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        user.isOwner -> Badge(stringResource(R.string.users_owner_badge), emphasized = true)
                        !user.active -> Badge(stringResource(R.string.users_inactive_badge))
                        user.lockedUntil != null && user.lockedUntil > System.currentTimeMillis() ->
                            Badge(stringResource(R.string.users_locked_badge))
                    }
                }
                if (index < list.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }
}

@Composable
internal fun Badge(text: String, emphasized: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

data class UserFormState(
    val isLoading: Boolean = false,
    val editing: ManagedUser? = null,
    val displayName: String = "",
    val username: String = "",
    val password: String = "",
    val pin: String = "",
    val roleId: Long? = null,
    val active: Boolean = true,
    val isSaving: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val notice: Int? = null,
    val saved: Boolean = false,
)

class UserFormViewModel(private val repository: UserManagementRepository, private val userId: Long?) : ViewModel() {

    val roles: StateFlow<List<RoleWithPermissions>> = repository.observeRoles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(UserFormState(isLoading = userId != null))
    val state: StateFlow<UserFormState> = _state.asStateFlow()

    init {
        if (userId != null) {
            viewModelScope.launch {
                val user = repository.getUser(userId)
                _state.update {
                    if (user == null) {
                        it.copy(isLoading = false, errorRes = R.string.auth_error_generic)
                    } else {
                        it.copy(isLoading = false, editing = user, displayName = user.displayName, username = user.username, roleId = user.roleId, active = user.active)
                    }
                }
            }
        }
    }

    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value, errorRes = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, errorRes = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, errorRes = null) }
    fun onPinChange(value: String) = _state.update { it.copy(pin = value, errorRes = null) }
    fun onRoleChange(roleId: Long) = _state.update { it.copy(roleId = roleId, errorRes = null) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun save() {
        val form = _state.value
        if (form.isSaving) return
        val roleId = form.roleId ?: run {
            _state.update { it.copy(errorRes = R.string.auth_error_generic) }
            return
        }
        run(onSuccess = { it.copy(saved = true) }) {
            val editing = form.editing
            if (editing == null) {
                repository.createUser(form.displayName, form.username, form.password, form.pin, roleId)
            } else {
                repository.updateUser(editing.id, form.displayName, roleId)
            }
        }
    }

    fun setActive(active: Boolean) {
        val editing = _state.value.editing ?: return
        run(onSuccess = { it.copy(active = active) }) { repository.setUserActive(editing.id, active) }
    }

    fun resetPassword(newPassword: String) {
        val editing = _state.value.editing ?: return
        run(onSuccess = { it.copy(notice = R.string.user_reset_done) }) { repository.resetPassword(editing.id, newPassword) }
    }

    fun resetPin(newPin: String) {
        val editing = _state.value.editing ?: return
        run(onSuccess = { it.copy(notice = R.string.user_reset_done) }) { repository.resetPin(editing.id, newPin) }
    }

    private fun run(onSuccess: (UserFormState) -> UserFormState, block: suspend () -> Unit) {
        _state.update { it.copy(isSaving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { onSuccess(it.copy(isSaving = false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorRes = e.toAuthMessageRes()) }
            }
        }
    }
}

private enum class ResetKind { PASSWORD, PIN }

@Composable
fun UserFormScreen(userId: Long?, onBack: () -> Unit, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: UserFormViewModel = viewModel(
        key = "user-form-${userId ?: "new"}",
        factory = viewModelFactory { initializer { UserFormViewModel(container.userManagementRepository, userId) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val currentUserId = LocalCurrentUser.current?.id
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var resetKind by remember { mutableStateOf<ResetKind?>(null) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(notice))
        viewModel.dismissNotice()
    }

    val editing = state.editing
    AdminScaffold(
        title = stringResource(if (userId == null) R.string.user_form_create_title else R.string.user_form_edit_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        if (state.isLoading) return@AdminScaffold
        val isOwner = editing?.isOwner == true
        val isSelf = editing != null && editing.id == currentUserId

        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text(stringResource(R.string.auth_field_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text(stringResource(R.string.auth_field_username)) },
            supportingText = { Text(stringResource(R.string.auth_field_username_hint)) },
            singleLine = true,
            enabled = editing == null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editing == null) {
            SecretField(state.password, viewModel::onPasswordChange, stringResource(R.string.auth_field_password), supportingText = stringResource(R.string.auth_field_password_hint))
            SecretField(state.pin, viewModel::onPinChange, stringResource(R.string.auth_field_pin), numeric = true)
        }

        Text(stringResource(R.string.user_field_role), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Panel(modifier = Modifier.fillMaxWidth()) {
            roles.forEach { role ->
                val roleLocked = isOwner || isSelf
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(
                            selected = state.roleId == role.role.id,
                            enabled = !roleLocked,
                            role = Role.RadioButton,
                            onClick = { viewModel.onRoleChange(role.role.id) },
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.roleId == role.role.id, onClick = null, enabled = !roleLocked)
                    Text(
                        text = roleDisplayName(role.role.systemRole, role.role.name),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        if (editing != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .toggleable(
                        value = state.active,
                        enabled = !isOwner && !isSelf && !state.isSaving,
                        role = Role.Switch,
                        onValueChange = viewModel::setActive,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.user_field_active), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = state.active, onCheckedChange = null, enabled = !isOwner && !isSelf)
            }
            if (!isOwner || isSelf) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { resetKind = ResetKind.PASSWORD }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.user_reset_password))
                    }
                    OutlinedButton(onClick = { resetKind = ResetKind.PIN }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.user_reset_pin))
                    }
                }
            }
        }

        state.errorRes?.let { FormError(stringResource(it)) }
        AuthPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.testTag(UsersTestTags.SAVE_BUTTON),
        )
    }

    val kind = resetKind
    if (kind != null && editing != null) {
        CredentialResetDialog(
            title = stringResource(if (kind == ResetKind.PASSWORD) R.string.user_reset_password_title else R.string.user_reset_pin_title, editing.displayName),
            numeric = kind == ResetKind.PIN,
            onDismiss = { resetKind = null },
            onConfirm = { value ->
                resetKind = null
                if (kind == ResetKind.PASSWORD) viewModel.resetPassword(value) else viewModel.resetPin(value)
            },
        )
    }
}

@Composable
private fun CredentialResetDialog(title: String, numeric: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = (if (numeric) CredentialRules.isValidPin(value) else CredentialRules.isValidPassword(value)) && value == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecretField(
                    value = value,
                    onValueChange = { value = it },
                    label = stringResource(if (numeric) R.string.account_new_pin else R.string.account_new_password),
                    numeric = numeric,
                    supportingText = if (numeric) null else stringResource(R.string.auth_field_password_hint),
                )
                SecretField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = stringResource(if (numeric) R.string.auth_field_pin_confirm else R.string.auth_field_password_confirm),
                    numeric = numeric,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = valid) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
