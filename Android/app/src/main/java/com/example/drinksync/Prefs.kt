package com.example.drinksync

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)

    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPrefs.getInt(key, defaultValue)
    }

    fun saveInt(key: String, value: Int) {
        sharedPrefs.edit { putInt(key, value) }
    }
}