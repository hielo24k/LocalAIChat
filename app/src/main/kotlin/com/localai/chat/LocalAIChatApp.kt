package com.localai.chat

import android.app.Application
import com.localai.chat.di.ServiceLocator

class LocalAIChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        ServiceLocator.release()
    }
}
