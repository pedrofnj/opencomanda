package com.pedroleite.opencomanda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.pedroleite.opencomanda.ui.auth.AuthGate
import com.pedroleite.opencomanda.ui.navigation.OpenComandaNavHost
import com.pedroleite.opencomanda.ui.rememberAppContainer
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenComandaApp()
        }
    }

    // Any touch, key press or trackball movement counts as activity and postpones the inactivity lock.
    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as OpenComandaApplication).container.session.touch()
    }
}

@Composable
fun OpenComandaApp() {
    OpenComandaTheme {
        AuthGate {
            val container = rememberAppContainer()
            val scope = rememberCoroutineScope()
            OpenComandaNavHost(
                onLock = container.authRepository::lock,
                onSignOut = { scope.launch { container.authRepository.signOut() } },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OpenComandaAppPreview() {
    OpenComandaApp()
}
