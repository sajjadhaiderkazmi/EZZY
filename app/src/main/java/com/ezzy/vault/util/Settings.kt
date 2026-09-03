package com.ezzy.vault.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ezzy_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class EzzySettings(
    val overlayEnabled: Boolean = false,
    val bubbleTrigger: Boolean = true,
    /** Empty means no strips are placed at all and only the bubble opens the bar. */
    val gestures: Set<Gesture> = Gesture.default,
    val stripLength: StripLength = StripLength.MEDIUM,
    /** Off until the user opts in — a fresh install must not open on a lock screen. */
    val biometricLock: Boolean = false,
    val autoLockMinutes: Int = 1,
    val clipboardClearSeconds: Int = 45,
    val maskSecrets: Boolean = true,
    val blockScreenshots: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<EzzySettings> = store.data.map { prefs ->
        EzzySettings(
            overlayEnabled = prefs[Keys.OVERLAY] ?: false,
            bubbleTrigger = prefs[Keys.BUBBLE] ?: true,
            gestures = Gesture.from(prefs[Keys.GESTURES]),
            stripLength = runCatching { StripLength.valueOf(prefs[Keys.STRIP_LENGTH] ?: "") }
                .getOrDefault(StripLength.MEDIUM),
            biometricLock = prefs[Keys.BIOMETRIC] ?: false,
            autoLockMinutes = prefs[Keys.AUTO_LOCK] ?: 1,
            clipboardClearSeconds = prefs[Keys.CLIP_CLEAR] ?: 45,
            maskSecrets = prefs[Keys.MASK] ?: true,
            blockScreenshots = prefs[Keys.NO_SCREENSHOT] ?: true,
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: "") }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC] ?: false,
        )
    }

    suspend fun setOverlayEnabled(value: Boolean) = put(Keys.OVERLAY, value)
    suspend fun setBubbleTrigger(value: Boolean) = put(Keys.BUBBLE, value)
    suspend fun setBiometricLock(value: Boolean) = put(Keys.BIOMETRIC, value)
    suspend fun setMaskSecrets(value: Boolean) = put(Keys.MASK, value)
    suspend fun setBlockScreenshots(value: Boolean) = put(Keys.NO_SCREENSHOT, value)
    suspend fun setDynamicColor(value: Boolean) = put(Keys.DYNAMIC, value)
    suspend fun setAutoLockMinutes(value: Int) = put(Keys.AUTO_LOCK, value)
    suspend fun setClipboardClearSeconds(value: Int) = put(Keys.CLIP_CLEAR, value)

    suspend fun setGestures(value: Set<Gesture>) {
        store.edit { prefs -> prefs[Keys.GESTURES] = value.mapTo(mutableSetOf()) { it.name } }
    }

    suspend fun setGestureEnabled(gesture: Gesture, enabled: Boolean) {
        store.edit { prefs ->
            val current = Gesture.from(prefs[Keys.GESTURES]).toMutableSet()
            if (enabled) current += gesture else current -= gesture
            prefs[Keys.GESTURES] = current.mapTo(mutableSetOf()) { it.name }
        }
    }

    suspend fun setStripLength(value: StripLength) {
        store.edit { it[Keys.STRIP_LENGTH] = value.name }
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
        val OVERLAY = booleanPreferencesKey("overlay_enabled")
        val BUBBLE = booleanPreferencesKey("trigger_bubble")
        val GESTURES = stringSetPreferencesKey("gestures")
        val STRIP_LENGTH = stringPreferencesKey("strip_length")
        val BIOMETRIC = booleanPreferencesKey("biometric_lock")
        val AUTO_LOCK = intPreferencesKey("auto_lock_minutes")
        val CLIP_CLEAR = intPreferencesKey("clipboard_clear_seconds")
        val MASK = booleanPreferencesKey("mask_secrets")
        val NO_SCREENSHOT = booleanPreferencesKey("block_screenshots")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
    }
}
