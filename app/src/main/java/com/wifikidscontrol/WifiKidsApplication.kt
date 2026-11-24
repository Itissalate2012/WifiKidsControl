package com.wifikidscontrol

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WifiKidsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize any global configs
    }
}