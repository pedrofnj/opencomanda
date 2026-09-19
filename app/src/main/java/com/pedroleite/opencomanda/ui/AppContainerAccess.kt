package com.pedroleite.opencomanda.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pedroleite.opencomanda.OpenComandaApplication
import com.pedroleite.opencomanda.data.AppContainer

/** Gives a Composable access to the app's manually-wired [AppContainer], for building ViewModel factories. */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as OpenComandaApplication).container
    }
}
