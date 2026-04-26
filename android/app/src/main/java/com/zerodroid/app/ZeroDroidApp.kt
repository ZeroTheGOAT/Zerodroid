package com.zerodroid.app

import android.app.Application

class ZeroDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize singletons, databases, etc.
    }
}
