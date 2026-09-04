package com.foleyit.itflow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.foleyit.itflow.data.api.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "itflow_prefs")

class AppPreferences(context: Context) {
    private val ctx = context.applicationContext

    companion object {
        val SERVER_URL        = stringPreferencesKey("server_url")
        val USER_NAME         = stringPreferencesKey("user_name")
        val USER_EMAIL        = stringPreferencesKey("user_email")
        val USER_ID           = intPreferencesKey("user_id")
        val USER_TYPE         = intPreferencesKey("user_type")
        val TRUSTED_CERT_SHA  = stringPreferencesKey("trusted_cert_sha")
        val BIOMETRIC_LOCK    = booleanPreferencesKey("biometric_lock")
        val THEME_MODE        = stringPreferencesKey("theme_mode")
        val COLOR_SEED        = stringPreferencesKey("color_seed")
    }

    private val masterKey = MasterKey.Builder(ctx)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        ctx,
        "itflow_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val serverUrl: Flow<String>        = ctx.dataStore.data.map { it[SERVER_URL] ?: "" }
    val userName: Flow<String?>        = ctx.dataStore.data.map { it[USER_NAME] }
    val userEmail: Flow<String?>       = ctx.dataStore.data.map { it[USER_EMAIL] }
    val trustedCertSha: Flow<String?>  = ctx.dataStore.data.map { it[TRUSTED_CERT_SHA] }
    val biometricLock: Flow<Boolean>   = ctx.dataStore.data.map { it[BIOMETRIC_LOCK] ?: false }
    // "system" (follow device setting), "light", or "dark" — never Material You dynamic/
    // wallpaper-derived color, always one of the fixed brand seeds below.
    val themeMode: Flow<String>        = ctx.dataStore.data.map { it[THEME_MODE] ?: "system" }
    // One of ColorSeed's ids ("foleyit"/"teal"/"sunset"/"forest"/"violet"); "foleyit" is default.
    val colorSeed: Flow<String>        = ctx.dataStore.data.map { it[COLOR_SEED] ?: "foleyit" }

    val authToken: Flow<String?> = flow {
        emit(securePrefs.getString("auth_token", null))
    }

    suspend fun saveServerUrl(url: String) {
        ctx.dataStore.edit { it[SERVER_URL] = url.trimEnd('/') }
    }

    suspend fun saveAuthData(token: String, user: UserInfo) {
        securePrefs.edit().putString("auth_token", token).apply()
        ctx.dataStore.edit {
            it[USER_NAME]  = user.name
            it[USER_EMAIL] = user.email
            it[USER_ID]    = user.id
            it[USER_TYPE]  = user.type
        }
    }

    suspend fun clearAuth() {
        securePrefs.edit().remove("auth_token").apply()
        ctx.dataStore.edit {
            it.remove(USER_NAME)
            it.remove(USER_EMAIL)
            it.remove(USER_ID)
            it.remove(USER_TYPE)
        }
    }

    suspend fun saveTrustedCert(sha256: String) {
        ctx.dataStore.edit { it[TRUSTED_CERT_SHA] = sha256 }
    }

    suspend fun clearTrustedCert() {
        ctx.dataStore.edit { it.remove(TRUSTED_CERT_SHA) }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        ctx.dataStore.edit { it[BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        ctx.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setColorSeed(seed: String) {
        ctx.dataStore.edit { it[COLOR_SEED] = seed }
    }
}
