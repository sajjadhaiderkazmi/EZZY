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
        // With an item id this is the second check on one guarded entry, not the vault door.
        val itemId = intent?.getStringExtra(EXTRA_ITEM_ID)
        AppLock.prompt(
            activity = this,
            title = if (itemId == null) "Unlock EZZY" else "Confirm it is you",
            subtitle = if (itemId == null) {
                "Confirm it is you to reach your saved details"
            } else {
                "This entry asks again every time the floating bar opens it"
            },
            onSuccess = {
                if (itemId == null) AppLock.unlock() else AppLock.confirmItem(itemId)
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

    companion object {
        const val EXTRA_ITEM_ID = "com.ezzy.vault.extra.ITEM_ID"
    }
}
