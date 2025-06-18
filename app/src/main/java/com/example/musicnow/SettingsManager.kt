package com.example.musicnow

import android.content.Context

object SettingsManager {
    private const val PREFS_NAME = "musicnow_settings"
    private const val KEY_BASE_URL = "base_url"
    private const val DEFAULT_BASE_URL = "https://x5b6vl-77-222-97-40.ru.tuna.am/"

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }
} 