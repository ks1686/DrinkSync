package com.example.drinksync.data

import android.content.Context
import androidx.core.content.edit
import com.example.drinksync.PrefsLike

/** [PrefsLike] backed by the app's private SharedPreferences file. */
class SharedPrefs(context: Context) : PrefsLike {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun saveInt(key: String, value: Int) = prefs.edit { putInt(key, value) }

    override fun saveBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    override fun saveLong(key: String, value: Long) = prefs.edit { putLong(key, value) }

    override fun saveStringSet(key: String, value: Set<String>?) = prefs.edit { putStringSet(key, value) }

    override fun saveString(key: String, value: String?) = prefs.edit { putString(key, value) }

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? =
        prefs.getStringSet(key, defaultValue)?.toSet()

    override fun getString(key: String, defaultValue: String?): String? = prefs.getString(key, defaultValue)

    private companion object {
        const val FILE_NAME = "DrinkSyncPrefs"
    }
}
