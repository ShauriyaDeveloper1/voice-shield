package com.sagar.voice_shield.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "voiceshield_prefs")

class PreferencesManager(private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_USER_PHONE = stringPreferencesKey("user_phone")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_SPEAKER_PROTECTION_ENABLED = booleanPreferencesKey("speaker_protection_enabled")
    }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { it[KEY_IS_LOGGED_IN] ?: false }
    val userId: Flow<String?> = dataStore.data.map { it[KEY_USER_ID] }
    val userName: Flow<String?> = dataStore.data.map { it[KEY_USER_NAME] }
    val userEmail: Flow<String?> = dataStore.data.map { it[KEY_USER_EMAIL] }
    val userPhone: Flow<String?> = dataStore.data.map { it[KEY_USER_PHONE] }
    val authToken: Flow<String?> = dataStore.data.map { it[KEY_AUTH_TOKEN] }
    val speakerProtectionEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_SPEAKER_PROTECTION_ENABLED] ?: false }

    suspend fun saveLoginData(token: String, id: String, name: String?, email: String?, phone: String?) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
            prefs[KEY_USER_ID] = id
            prefs[KEY_USER_NAME] = name ?: ""
            prefs[KEY_USER_EMAIL] = email ?: ""
            prefs[KEY_USER_PHONE] = phone ?: ""
            prefs[KEY_IS_LOGGED_IN] = true
        }
    }

    suspend fun clearLoginData() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_PHONE)
            prefs[KEY_IS_LOGGED_IN] = false
        }
    }

    suspend fun setSpeakerProtection(enabled: Boolean) {
        dataStore.edit { it[KEY_SPEAKER_PROTECTION_ENABLED] = enabled }
    }
}
