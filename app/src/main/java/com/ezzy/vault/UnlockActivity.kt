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
        // With an item or section id this is a second check on that one guarded thing, not
        // the vault door — at most one of the two extras is ever set.
        val itemId = intent?.getStringExtra(EXTRA_ITEM_ID)
        val sectionId = intent?.getStringExtra(EXTRA_SECTION_ID)
        AppLock.prompt(
            activity = this,
            title = if (itemId == null && sectionId == null) "Unlock EZZY" else "Confirm it is you",
            subtitle = when {
                itemId != null -> "This entry asks again every time the floating bar opens it"
                sectionId != null -> "This section asks again every time the floating bar opens it"
                else -> "Confirm it is you to reach your saved details"
            },
            onSuccess = {
                when {
                    itemId != null -> AppLock.confirmItem(itemId)
                    sectionId != null -> AppLock.confirmSection(sectionId)
                    else -> AppLock.unlock()
                }
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
        const val EXTRA_SECTION_ID = "com.ezzy.vault.extra.SECTION_ID"
    }
}
