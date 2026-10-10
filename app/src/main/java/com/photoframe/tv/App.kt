package com.photoframe.tv

import android.app.Application
import com.photoframe.tv.di.AppGraph

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // Pick the UI language from the device on first launch (before any Activity inflates).
        LocaleStore.initIfUnset(this)
        graph = AppGraph(this)
    }
}
