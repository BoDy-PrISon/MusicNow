package com.example.musicnow


import android.app.Application
import android.content.Context

class MusicRecognizerApp : Application() {
    companion object {
        lateinit var appContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}