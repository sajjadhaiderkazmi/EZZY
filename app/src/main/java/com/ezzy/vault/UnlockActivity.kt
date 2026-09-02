package com.ezzy.vault

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.ezzy.vault.security.AppLock

/**
 * A window-less activity whose only job is to show the system biometric sheet on behalf of the
 * floating bar. The overlay is not an Activity, and the biometric prompt requires one, so the
 * bar starts this, it authenticates, and it finishes immediately either way.
 */
class UnlockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLock.prompt(
            activity = this,
            title = "Unlock EZZY",
            subtitle = "Confirm it is you to reach your saved details",
            onSuccess = {
                AppLock.unlock()
                finish()
            },
            onFailure = { finish() },
        )
    }

    override fun finish() {
        super.finish()
        // No content view, so suppress the default window animation as well.
        overridePendingTransition(0, 0)
    }
}
