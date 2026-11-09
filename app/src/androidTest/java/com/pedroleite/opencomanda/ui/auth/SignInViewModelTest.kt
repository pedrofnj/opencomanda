package com.pedroleite.opencomanda.ui.auth

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pedroleite.opencomanda.data.auth.AuthTestFixture
import com.pedroleite.opencomanda.data.auth.AuthTestFixture.Companion.OWNER_PASSWORD
import com.pedroleite.opencomanda.data.auth.AuthTestFixture.Companion.OWNER_PIN
import com.pedroleite.opencomanda.data.auth.AuthTestFixture.Companion.OWNER_USERNAME
import com.pedroleite.opencomanda.data.auth.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sign-in form lives as long as the activity, so it outlasts the session it opens: after a
 * successful sign-in nothing typed may remain for whoever uses the device next.
 */
@RunWith(AndroidJUnit4::class)
class SignInViewModelTest {

    private val fixture = AuthTestFixture()
    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: SignInViewModel
    private lateinit var recoveryKey: String

    @Before
    fun setUp() = runBlocking {
        recoveryKey = fixture.setUpOwner()
        fixture.auth.signOut()
        val factory = viewModelFactory { initializer { SignInViewModel(fixture.auth) } }
        viewModel = ViewModelProvider.create(viewModelStore, factory)[SignInViewModel::class]
    }

    /** Stops the view model's work before its database goes away. */
    @After
    fun tearDown() {
        viewModelStore.clear()
        fixture.close()
    }

    private suspend fun awaitUi(condition: (SignInUiState) -> Boolean): SignInUiState =
        withTimeout(TIMEOUT_MILLIS) { viewModel.ui.first(condition) }

    @Test
    fun aSuccessfulPasswordSignInLeavesAnEmptyForm() = runBlocking {
        viewModel.usePassword()
        viewModel.onUsernameChange(OWNER_USERNAME)
        viewModel.onPasswordChange(OWNER_PASSWORD)

        viewModel.submitPassword()

        assertEquals(SignInUiState(), awaitUi { !it.isBusy && it.mode == SignInMode.Users })
        assertTrue(fixture.session.state.value is SessionState.Active)
    }

    @Test
    fun aWrongPasswordKeepsTheUsernameButNotThePassword() = runBlocking {
        viewModel.usePassword()
        viewModel.onUsernameChange(OWNER_USERNAME)
        viewModel.onPasswordChange("senha-errada-1")

        viewModel.submitPassword()

        val state = awaitUi { it.message != null }
        assertEquals(SignInMode.Password, state.mode)
        assertEquals(OWNER_USERNAME, state.username)
        assertEquals("", state.password)
    }

    @Test
    fun aSuccessfulPinSignInLeavesAnEmptyForm() = runBlocking {
        val owner = fixture.auth.observeSignInCandidates().first().single()
        viewModel.choose(owner)

        viewModel.onPinChange(OWNER_PIN)

        assertEquals(SignInUiState(), awaitUi { !it.isBusy && it.mode == SignInMode.Users })
        assertTrue(fixture.session.state.value is SessionState.Active)
    }

    @Test
    fun aSuccessfulRecoveryForgetsTheNewCredentials() = runBlocking {
        viewModel.startRecovery()
        viewModel.onRecoveryKeyChange(recoveryKey)
        viewModel.onNewPasswordChange("outra-senha-2")
        viewModel.onNewPasswordConfirmChange("outra-senha-2")
        viewModel.onNewPinChange("333333")
        viewModel.onNewPinConfirmChange("333333")

        var shownKey: String? = null
        viewModel.submitRecovery { shownKey = it }

        assertEquals(SignInUiState(), awaitUi { !it.isBusy && it.mode == SignInMode.Users })
        assertTrue(shownKey != null)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
