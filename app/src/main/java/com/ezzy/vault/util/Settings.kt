package com.ezzy.vault.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ezzy_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class EzzySettings(
    /** Optional, shown in the Home greeting only — never required, never synced anywhere. */
    val displayName: String = "",
    /** False until the welcome screen has been through once, whether or not a name was given. */
    val onboarded: Boolean = false,
    val overlayEnabled: Boolean = false,
    val triggerMode: TriggerMode = TriggerMode.default,
    /** False until the user has been asked which mode they want. */
    val triggerModeChosen: Boolean = false,
    val autoHide: AutoHide = AutoHide.default,
    /** Off until the user opts in — a fresh install must not open on a lock screen. */
    val biometricLock: Boolean = false,
    val autoLockMinutes: Int = 1,
    val clipboardClearSeconds: Int = 45,
    val maskSecrets: Boolean = true,
    val blockScreenshots: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** The arc that circles the floating button while it is up with nothing to count down. */
    val bubbleSweep: Boolean = true,
)

class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<EzzySettings> = store.data.map { prefs ->
        EzzySettings(
            displayName = prefs[Keys.DISPLAY_NAME] ?: "",
            onboarded = prefs[Keys.ONBOARDED] ?: false,
            overlayEnabled = prefs[Keys.OVERLAY] ?: false,
            triggerMode = TriggerMode.from(prefs[Keys.MODE]),
            triggerModeChosen = prefs[Keys.MODE_CHOSEN] ?: false,
            autoHide = AutoHide.from(prefs[Keys.AUTO_HIDE]),
            biometricLock = prefs[Keys.BIOMETRIC] ?: false,
            autoLockMinutes = prefs[Keys.AUTO_LOCK] ?: 1,
            clipboardClearSeconds = prefs[Keys.CLIP_CLEAR] ?: 45,
            maskSecrets = prefs[Keys.MASK] ?: true,
            blockScreenshots = prefs[Keys.NO_SCREENSHOT] ?: true,
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: "") }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC] ?: false,
            bubbleSweep = prefs[Keys.BUBBLE_SWEEP] ?: true,
        )
    }

    suspend fun setOverlayEnabled(value: Boolean) = put(Keys.OVERLAY, value)

    suspend fun setDisplayName(value: String) {
        store.edit { it[Keys.DISPLAY_NAME] = value.trim() }
    }

    /**
     * One write, so the app never observes a half-finished welcome (a name already stored while
     * the welcome screen is still the visible destination).
     */
    suspend fun completeOnboarding(name: String) {
        store.edit {
            it[Keys.DISPLAY_NAME] = name.trim()
            it[Keys.ONBOARDED] = true
        }
    }

    suspend fun setBiometricLock(value: Boolean) = put(Keys.BIOMETRIC, value)
    suspend fun setMaskSecrets(value: Boolean) = put(Keys.MASK, value)
    suspend fun setBlockScreenshots(value: Boolean) = put(Keys.NO_SCREENSHOT, value)
    suspend fun setDynamicColor(value: Boolean) = put(Keys.DYNAMIC, value)
    suspend fun setBubbleSweep(value: Boolean) = put(Keys.BUBBLE_SWEEP, value)
    suspend fun setAutoLockMinutes(value: Int) = put(Keys.AUTO_LOCK, value)
    suspend fun setClipboardClearSeconds(value: Int) = put(Keys.CLIP_CLEAR, value)

    /** Records the choice and that it has been made, so the prompt is shown only once. */
    suspend fun setTriggerMode(value: TriggerMode) {
        store.edit {
            it[Keys.MODE] = value.name
            it[Keys.MODE_CHOSEN] = true
        }
    }

    suspend fun setAutoHide(value: AutoHide) {
        store.edit { it[Keys.AUTO_HIDE] = value.name }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        store.edit { it[Keys.THEME] = value.name }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        store.edit { it[key] = value }
    }

    private suspend fun put(key: Preferences.Key<Int>, value: Int) {
        store.edit { it[key] = value }
    }

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val OVERLAY = booleanPreferencesKey("overlay_enabled")
        val MODE = stringPreferencesKey("trigger_mode")
        val MODE_CHOSEN = booleanPreferencesKey("trigger_mode_chosen")
        val AUTO_HIDE = stringPreferencesKey("auto_hide")
        val BIOMETRIC = booleanPreferencesKey("biometric_lock")
        val AUTO_LOCK = intPreferencesKey("auto_lock_minutes")
        val CLIP_CLEAR = intPreferencesKey("clipboard_clear_seconds")
        val MASK = booleanPreferencesKey("mask_secrets")
        val NO_SCREENSHOT = booleanPreferencesKey("block_screenshots")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val BUBBLE_SWEEP = booleanPreferencesKey("bubble_sweep")
    }
}
