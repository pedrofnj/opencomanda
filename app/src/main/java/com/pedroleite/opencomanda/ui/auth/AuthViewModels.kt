package com.pedroleite.opencomanda.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.auth.SessionManager
import com.pedroleite.opencomanda.data.auth.SessionState
import com.pedroleite.opencomanda.data.repository.AuthRepository
import com.pedroleite.opencomanda.data.repository.RecoveryResult
import com.pedroleite.opencomanda.data.repository.SecuritySettings
import com.pedroleite.opencomanda.data.repository.SignInCandidate
import com.pedroleite.opencomanda.data.repository.SignInResult
import com.pedroleite.opencomanda.domain.auth.CredentialRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What an access screen tells the person after a failed attempt. */
sealed interface AuthMessage {
    data object InvalidCredentials : AuthMessage
    data object InvalidRecoveryKey : AuthMessage
    data class LockedUntil(val untilMillis: Long) : AuthMessage
    data class Resource(@param:StringRes val resId: Int) : AuthMessage
}

class AuthGateViewModel(
    private val authRepository: AuthRepository,
    private val session: SessionManager,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = session.state

    val settings: StateFlow<SecuritySettings?> = authRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pendingRecoveryKey = MutableStateFlow<String?>(null)

    /** A recovery key that must be shown (and acknowledged) before the app opens. */
    val pendingRecoveryKey: StateFlow<String?> = _pendingRecoveryKey.asStateFlow()

    init {
        viewModelScope.launch { authRepository.initialize() }
    }

    fun showRecoveryKey(key: String) {
        _pendingRecoveryKey.value = key
    }

    fun acknowledgeRecoveryKey() {
        _pendingRecoveryKey.value = null
    }

    fun lockIfIdle(timeoutMillis: Long): Boolean = session.lockIfIdle(timeoutMillis)
}

data class SetupForm(
    val establishmentName: String = "",
    val displayName: String = "",
    val username: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val pin: String = "",
    val pinConfirm: String = "",
    val isSaving: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
)

class SetupViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _form = MutableStateFlow(SetupForm())
    val form: StateFlow<SetupForm> = _form.asStateFlow()

    fun onEstablishmentNameChange(value: String) = _form.update { it.copy(establishmentName = value, errorRes = null) }
    fun onDisplayNameChange(value: String) = _form.update { it.copy(displayName = value, errorRes = null) }
    fun onUsernameChange(value: String) = _form.update { it.copy(username = value, errorRes = null) }
    fun onPasswordChange(value: String) = _form.update { it.copy(password = value, errorRes = null) }
    fun onPasswordConfirmChange(value: String) = _form.update { it.copy(passwordConfirm = value, errorRes = null) }
    fun onPinChange(value: String) = _form.update { it.copy(pin = value, errorRes = null) }
    fun onPinConfirmChange(value: String) = _form.update { it.copy(pinConfirm = value, errorRes = null) }

    fun submit(onRecoveryKey: (String) -> Unit) {
        val form = _form.value
        if (form.isSaving) return
        val localError = when {
            form.establishmentName.isBlank() || form.displayName.isBlank() -> R.string.auth_error_blank_name
            !CredentialRules.isValidUsername(form.username) -> R.string.auth_error_invalid_username
            !CredentialRules.isValidPassword(form.password) -> R.string.auth_error_invalid_password
            form.password != form.passwordConfirm -> R.string.auth_error_password_mismatch
            !CredentialRules.isValidPin(form.pin) -> R.string.auth_error_invalid_pin
            form.pin != form.pinConfirm -> R.string.auth_error_pin_mismatch
            else -> null
        }
        if (localError != null) {
            _form.update { it.copy(errorRes = localError) }
            return
        }
        _form.update { it.copy(isSaving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                val key = authRepository.completeSetup(form.establishmentName, form.displayName, form.username, form.password, form.pin)
                _form.value = SetupForm()
                onRecoveryKey(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.update { it.copy(isSaving = false, errorRes = e.toAuthMessageRes()) }
            }
        }
    }
}

sealed interface SignInMode {
    data object Users : SignInMode
    data class Pin(val candidate: SignInCandidate) : SignInMode
    data object Password : SignInMode
    data object Recovery : SignInMode
}

data class SignInUiState(
    val mode: SignInMode = SignInMode.Users,
    val pin: String = "",
    val username: String = "",
    val password: String = "",
    val recoveryKey: String = "",
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val newPin: String = "",
    val newPinConfirm: String = "",
    val isBusy: Boolean = false,
    val message: AuthMessage? = null,
)

/**
 * Lives as long as the activity, so it outlasts the session it opens: on every successful sign-in
 * the form goes back to its empty initial state, leaving nothing typed behind for whoever uses the
 * device next.
 */
class SignInViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val candidates: StateFlow<List<SignInCandidate>> = authRepository.observeSignInCandidates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(SignInUiState())
    val ui: StateFlow<SignInUiState> = _ui.asStateFlow()

    fun choose(candidate: SignInCandidate) = _ui.update { SignInUiState(mode = SignInMode.Pin(candidate)) }
    fun usePassword() = _ui.update { SignInUiState(mode = SignInMode.Password) }
    fun backToUsers() = _ui.update { SignInUiState(mode = SignInMode.Users) }
    fun startRecovery() = _ui.update { SignInUiState(mode = SignInMode.Recovery) }

    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value, message = null) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, message = null) }
    fun onRecoveryKeyChange(value: String) = _ui.update { it.copy(recoveryKey = value, message = null) }
    fun onNewPasswordChange(value: String) = _ui.update { it.copy(newPassword = value, message = null) }
    fun onNewPasswordConfirmChange(value: String) = _ui.update { it.copy(newPasswordConfirm = value, message = null) }
    fun onNewPinChange(value: String) = _ui.update { it.copy(newPin = value, message = null) }
    fun onNewPinConfirmChange(value: String) = _ui.update { it.copy(newPinConfirm = value, message = null) }

    /** The sixth digit submits on its own — no extra tap at the counter. */
    fun onPinChange(value: String) {
        val mode = _ui.value.mode as? SignInMode.Pin ?: return
        if (_ui.value.isBusy) return
        _ui.update { it.copy(pin = value, message = null) }
        if (value.length == CredentialRules.PIN_LENGTH) {
            _ui.update { it.copy(isBusy = true) }
            viewModelScope.launch {
                val result = runCatchingSignIn { authRepository.signInWithPin(mode.candidate.id, value) }
                _ui.update { if (result == null) SignInUiState() else it.copy(isBusy = false, pin = "", message = result) }
            }
        }
    }

    fun submitPassword() {
        val state = _ui.value
        if (state.isBusy) return
        _ui.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            val result = runCatchingSignIn { authRepository.signInWithPassword(state.username, state.password) }
            _ui.update { if (result == null) SignInUiState() else it.copy(isBusy = false, password = "", message = result) }
        }
    }

    fun submitRecovery(onRecoveryKey: (String) -> Unit) {
        val state = _ui.value
        if (state.isBusy) return
        val localError = when {
            !CredentialRules.isValidPassword(state.newPassword) -> R.string.auth_error_invalid_password
            state.newPassword != state.newPasswordConfirm -> R.string.auth_error_password_mismatch
            !CredentialRules.isValidPin(state.newPin) -> R.string.auth_error_invalid_pin
            state.newPin != state.newPinConfirm -> R.string.auth_error_pin_mismatch
            else -> null
        }
        if (localError != null) {
            _ui.update { it.copy(message = AuthMessage.Resource(localError)) }
            return
        }
        _ui.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                when (val result = authRepository.recoverOwnerAccess(state.recoveryKey, state.newPassword, state.newPin)) {
                    is RecoveryResult.Success -> {
                        onRecoveryKey(result.newRecoveryKey)
                        null
                    }
                    RecoveryResult.InvalidKey -> AuthMessage.InvalidRecoveryKey
                    is RecoveryResult.LockedOut -> AuthMessage.LockedUntil(result.untilMillis)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuthMessage.Resource(e.toAuthMessageRes())
            }
            _ui.update { if (message == null) SignInUiState() else it.copy(isBusy = false, message = message) }
        }
    }
}

data class LockUiState(
    val usePin: Boolean = true,
    val pin: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    val message: AuthMessage? = null,
)

class LockViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(LockUiState())
    val ui: StateFlow<LockUiState> = _ui.asStateFlow()

    fun toggleMethod() = _ui.update { LockUiState(usePin = !it.usePin) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, message = null) }

    fun onPinChange(value: String) {
        if (_ui.value.isBusy) return
        _ui.update { it.copy(pin = value, message = null) }
        if (value.length == CredentialRules.PIN_LENGTH) unlock(value, usePin = true)
    }

    fun submitPassword() = unlock(_ui.value.password, usePin = false)

    fun switchUser() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun unlock(secret: String, usePin: Boolean) {
        if (_ui.value.isBusy) return
        _ui.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            val result = runCatchingSignIn { authRepository.unlock(secret, usePin) }
            _ui.update { it.copy(isBusy = false, pin = "", password = "", message = result) }
        }
    }
}

/** Runs a sign-in and returns the message to show, or null on success (the session then opens). */
private suspend fun runCatchingSignIn(block: suspend () -> SignInResult): AuthMessage? = try {
    when (val result = block()) {
        is SignInResult.Success -> null
        SignInResult.InvalidCredentials -> AuthMessage.InvalidCredentials
        is SignInResult.LockedOut -> AuthMessage.LockedUntil(result.untilMillis)
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AuthMessage.Resource(e.toAuthMessageRes())
}
