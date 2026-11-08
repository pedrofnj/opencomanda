package com.pedroleite.opencomanda.ui.admin

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.pedroleite.opencomanda.data.repository.RoleWithPermissions
import com.pedroleite.opencomanda.data.repository.UserManagementRepository
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.domain.auth.PermissionPolicy
import com.pedroleite.opencomanda.domain.auth.SystemRole
import com.pedroleite.opencomanda.ui.auth.AuthPrimaryButton
import com.pedroleite.opencomanda.ui.auth.permissionLabel
import com.pedroleite.opencomanda.ui.auth.roleDisplayName
import com.pedroleite.opencomanda.ui.auth.toAuthMessageRes
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

object RolesTestTags {
    const val ADD_FAB = "roles_add"
    const val SAVE_BUTTON = "role_form_save"
    const val DELETE_BUTTON = "role_form_delete"
    const val RESTORE_DEFAULTS = "role_form_restore_defaults"
    fun roleRow(id: Long) = "roles_row_$id"
    fun permission(permission: Permission) = "role_permission_${permission.key}"
}

class RolesViewModel(repository: UserManagementRepository) : ViewModel() {
    val roles: StateFlow<List<RoleWithPermissions>?> = repository.observeRoles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun RolesScreen(onBack: () -> Unit, onCreate: () -> Unit, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: RolesViewModel = viewModel(factory = viewModelFactory { initializer { RolesViewModel(container.userManagementRepository) } })
    val roles by viewModel.roles.collectAsStateWithLifecycle()

    AdminScaffold(
        title = stringResource(R.string.roles_title),
        onBack = onBack,
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, modifier = Modifier.testTag(RolesTestTags.ADD_FAB)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.roles_add), modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) {
        val list = roles ?: return@AdminScaffold
        Panel(modifier = Modifier.fillMaxWidth()) {
            list.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(item.role.id) }
                        .testTag(RolesTestTags.roleRow(item.role.id))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(roleDisplayName(item.role.systemRole, item.role.name), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.roles_permission_count, item.permissions.size) + " · " +
                                stringResource(R.string.roles_user_count, item.userCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.isSystem) Badge(stringResource(R.string.roles_system_badge))
                }
                if (index < list.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }
}

data class RoleFormState(
    val isLoading: Boolean = false,
    val role: RoleWithPermissions? = null,
    val name: String = "",
    val permissions: Set<Permission> = emptySet(),
    val isSaving: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    val done: Boolean = false,
) {
    val systemRole: SystemRole? get() = role?.role?.systemRole

    /** Only the Administrador profile is fixed: it always holds every permission. */
    val readOnly: Boolean get() = !PermissionPolicy.isEditable(systemRole)
}

class RoleFormViewModel(private val repository: UserManagementRepository, private val roleId: Long?) : ViewModel() {
    private val _state = MutableStateFlow(RoleFormState(isLoading = roleId != null))
    val state: StateFlow<RoleFormState> = _state.asStateFlow()

    init {
        if (roleId != null) {
            viewModelScope.launch {
                val role = repository.observeRoles().first().find { it.role.id == roleId }
                _state.update {
                    if (role == null) {
                        it.copy(isLoading = false, errorRes = R.string.auth_error_generic)
                    } else {
                        it.copy(isLoading = false, role = role, name = role.role.name, permissions = role.permissions)
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, errorRes = null) }

    fun toggle(permission: Permission, granted: Boolean) = _state.update {
        it.copy(permissions = if (granted) it.permissions + permission else it.permissions - permission, errorRes = null)
    }

    /** Puts a built-in profile's checkboxes back to the initial matrix; saving applies it. */
    fun restoreDefaults() = _state.update { state ->
        val systemRole = state.systemRole ?: return@update state
        state.copy(permissions = PermissionPolicy.defaultPermissions(systemRole), errorRes = null)
    }

    fun save() {
        val form = _state.value
        if (form.isSaving || form.readOnly) return
        run {
            val existing = form.role
            if (existing == null) repository.createRole(form.name, form.permissions) else repository.updateRole(existing.role.id, form.name, form.permissions)
        }
    }

    fun delete() {
        val existing = _state.value.role ?: return
        run { repository.deleteRole(existing.role.id) }
    }

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(isSaving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(isSaving = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorRes = e.toAuthMessageRes()) }
            }
        }
    }
}

@Composable
fun RoleFormScreen(roleId: Long?, onBack: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: RoleFormViewModel = viewModel(
        key = "role-form-${roleId ?: "new"}",
        factory = viewModelFactory { initializer { RoleFormViewModel(container.userManagementRepository, roleId) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    val title = when {
        roleId == null -> stringResource(R.string.role_form_create_title)
        state.role != null -> roleDisplayName(state.role?.role?.systemRole, state.name)
        else -> stringResource(R.string.role_form_edit_title)
    }
    AdminScaffold(title = title, onBack = onBack, modifier = modifier) {
        if (state.isLoading) return@AdminScaffold
        val readOnly = state.readOnly
        val systemRole = state.systemRole
        if (readOnly) {
            Text(stringResource(R.string.role_admin_fixed_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (systemRole == null) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.role_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.role_permissions_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Panel(modifier = Modifier.fillMaxWidth()) {
            Permission.entries.forEachIndexed { index, permission ->
                val grantable = PermissionPolicy.isGrantable(systemRole, permission)
                val enabled = !readOnly && grantable && !state.isSaving
                val checked = permission in state.permissions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { viewModel.toggle(permission, it) }
                        .testTag(RolesTestTags.permission(permission))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(permissionLabel(permission), style = MaterialTheme.typography.bodyLarge)
                        if (!grantable && !readOnly) {
                            Text(
                                text = stringResource(
                                    if (permission in PermissionPolicy.adminOnlyPermissions) {
                                        R.string.role_hint_admin_only
                                    } else {
                                        R.string.role_hint_admin_manager_only
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (index < Permission.entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
        state.errorRes?.let { FormError(stringResource(it)) }
        if (!readOnly) {
            AuthPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.testTag(RolesTestTags.SAVE_BUTTON),
            )
            if (systemRole != null) {
                OutlinedButton(
                    onClick = viewModel::restoreDefaults,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(RolesTestTags.RESTORE_DEFAULTS),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.role_restore_defaults))
                }
            }
            if (state.role != null && systemRole == null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(RolesTestTags.DELETE_BUTTON),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.role_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.role_delete_confirm_title, state.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.role_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
