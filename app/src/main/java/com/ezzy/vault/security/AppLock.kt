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

    fun unlock() {
        _unlocked.value = true
        backgroundedAt = 0L
    }

    fun lock() {
        _unlocked.value = false
        backgroundedAt = 0L
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
                override fun onAuthenticationSucceeded(result: AuthenticationResult) = onSuccess()

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
