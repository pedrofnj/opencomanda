package com.pedroleite.opencomanda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pedroleite.opencomanda.ui.navigation.OpenComandaNavHost
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenComandaApp()
        }
    }
}

@Composable
fun OpenComandaApp() {
    OpenComandaTheme {
        OpenComandaNavHost()
    }
}

@Preview(showBackground = true)
@Composable
fun OpenComandaAppPreview() {
    OpenComandaApp()
}
