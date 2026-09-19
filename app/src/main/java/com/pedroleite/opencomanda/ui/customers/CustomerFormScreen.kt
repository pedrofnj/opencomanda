package com.pedroleite.opencomanda.ui.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.ui.rememberAppContainer

/** Resolves the real, app-container-backed [CustomerFormViewModel]. A test can instead pass its
 *  own [CustomerFormViewModel] instance directly into [CustomerFormScreen], bypassing this. */
@Composable
private fun rememberCustomerFormViewModel(): CustomerFormViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { CustomerFormViewModel(container.customerRepository) }
        },
    )
}

/** Stable UI-test hooks for [CustomerForm]'s fields — more robust than matching on label text,
 *  which can collide with supporting/error text rendered near the same field. */
object CustomerFormTestTags {
    const val NAME_FIELD = "customer_form_name"
    const val PHONE_FIELD = "customer_form_phone"
    const val NOTES_FIELD = "customer_form_notes"
    const val SAVE_BUTTON = "customer_form_save"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFormScreen(
    customerId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomerFormViewModel = rememberCustomerFormViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(customerId) {
        if (customerId != null) viewModel.loadForEditing(customerId)
    }

    LaunchedEffect(uiState.saveComplete) {
        if (uiState.saveComplete) onSaved()
    }

    val titleRes = if (uiState.isEditing) R.string.customer_edit_title else R.string.customer_create_title

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            CustomerForm(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onPhoneChange = viewModel::onPhoneChange,
                onNotesChange = viewModel::onNotesChange,
                onSubmit = viewModel::submit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(20.dp),
            )
        }
    }
}

@Composable
private fun CustomerForm(
    uiState: CustomerFormUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.customer_field_name)) },
            singleLine = true,
            isError = uiState.nameError,
            supportingText = if (uiState.nameError) {
                { Text(stringResource(R.string.customer_field_name_error)) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CustomerFormTestTags.NAME_FIELD),
        )

        OutlinedTextField(
            value = uiState.phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.customer_field_phone)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CustomerFormTestTags.PHONE_FIELD),
        )

        OutlinedTextField(
            value = uiState.notes,
            onValueChange = onNotesChange,
            label = { Text(stringResource(R.string.customer_field_notes)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CustomerFormTestTags.NOTES_FIELD),
        )

        if (uiState.error == CustomerFormError.SAVE_FAILED) {
            Text(
                text = stringResource(R.string.customer_save_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = onSubmit,
            enabled = uiState.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CustomerFormTestTags.SAVE_BUTTON),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.customer_save))
            }
        }
    }
}
