package com.pedroleite.opencomanda

import android.app.Application
import com.pedroleite.opencomanda.data.AppContainer

class OpenComandaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
