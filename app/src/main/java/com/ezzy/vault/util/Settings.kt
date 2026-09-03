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
    val overlayEnabled: Boolean = false,
    /** The draggable button that stays on screen. */
    val bubbleTrigger: Boolean = true,
    val gestureEnabled: Boolean = false,
    val gesture: Gesture = Gesture.default,
    val gestureArea: GestureArea = GestureArea.THIRD,
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
            gestureEnabled = prefs[Keys.GESTURE_ON] ?: false,
            gesture = Gesture.from(prefs[Keys.GESTURE]),
            gestureArea = runCatching { GestureArea.valueOf(prefs[Keys.GESTURE_AREA] ?: "") }
                .getOrDefault(GestureArea.THIRD),
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
    suspend fun setGestureEnabled(value: Boolean) = put(Keys.GESTURE_ON, value)
    suspend fun setBiometricLock(value: Boolean) = put(Keys.BIOMETRIC, value)
    suspend fun setMaskSecrets(value: Boolean) = put(Keys.MASK, value)
    suspend fun setBlockScreenshots(value: Boolean) = put(Keys.NO_SCREENSHOT, value)
    suspend fun setDynamicColor(value: Boolean) = put(Keys.DYNAMIC, value)
    suspend fun setAutoLockMinutes(value: Int) = put(Keys.AUTO_LOCK, value)
    suspend fun setClipboardClearSeconds(value: Int) = put(Keys.CLIP_CLEAR, value)

    suspend fun setGesture(value: Gesture) {
        store.edit { it[Keys.GESTURE] = value.name }
    }

    suspend fun setGestureArea(value: GestureArea) {
        store.edit { it[Keys.GESTURE_AREA] = value.name }
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
        val GESTURE_ON = booleanPreferencesKey("gesture_enabled")
        val GESTURE = stringPreferencesKey("gesture")
        val GESTURE_AREA = stringPreferencesKey("gesture_area")
        val BIOMETRIC = booleanPreferencesKey("biometric_lock")
        val AUTO_LOCK = intPreferencesKey("auto_lock_minutes")
        val CLIP_CLEAR = intPreferencesKey("clipboard_clear_seconds")
        val MASK = booleanPreferencesKey("mask_secrets")
        val NO_SCREENSHOT = booleanPreferencesKey("block_screenshots")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
    }
}
