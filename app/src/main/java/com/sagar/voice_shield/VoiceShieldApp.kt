package com.sagar.voice_shield

import android.app.Application

class VoiceShieldApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
