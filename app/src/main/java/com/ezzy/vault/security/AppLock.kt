package com.ezzy.vault.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the vault is currently open. The lock is re-armed when the app goes to the
 * background for longer than the configured grace period, and by the overlay on demand.
 */
object AppLock {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var backgroundedAt = 0L

    /**
     * Entries that have been confirmed one at a time, on top of the vault being open at all.
     * Logins and documents are worth a second check before the floating bar puts them on screen
     * over somebody else's app, so the bar clears this every time it closes.
     */
    private val _confirmedItems = MutableStateFlow<Set<String>>(emptySet())
    val confirmedItems: StateFlow<Set<String>> = _confirmedItems.asStateFlow()

    fun confirmItem(itemId: String) {
        _confirmedItems.value = _confirmedItems.value + itemId
    }

    /**
     * Locked sections confirmed for the current visit of the floating bar, on the same terms as
     * [confirmedItems] — a separate set, since a category id and an item id are drawn from
     * different id spaces and confirming one should never accidentally confirm the other.
     */
    private val _confirmedSections = MutableStateFlow<Set<String>>(emptySet())
    val confirmedSections: StateFlow<Set<String>> = _confirmedSections.asStateFlow()

    fun confirmSection(categoryId: String) {
        _confirmedSections.value = _confirmedSections.value + categoryId
    }

    fun clearItemConfirmations() {
        _confirmedItems.value = emptySet()
        _confirmedSections.value = emptySet()
    }

    fun unlock() {
        _unlocked.value = true
        backgroundedAt = 0L
    }

    fun lock() {
        _unlocked.value = false
        backgroundedAt = 0L
        clearItemConfirmations()
    }

    fun onBackgrounded() {
        if (_unlocked.value) backgroundedAt = System.currentTimeMillis()
    }

    /** Re-locks if the app has been away longer than [graceMillis]. */
    fun onForegrounded(graceMillis: Long) {
        val since = backgroundedAt
        if (since != 0L && System.currentTimeMillis() - since >= graceMillis) lock()
        backgroundedAt = 0L
    }

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system biometric sheet, falling back to the device PIN/pattern so a phone
     * without a fingerprint sensor is still usable.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (!canAuthenticate(activity)) {
            // No enrolled credential at all — the vault would be unopenable otherwise.
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errString.toString())
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
        )
    }

    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
}
