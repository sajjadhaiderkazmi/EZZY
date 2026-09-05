package com.ezzy.vault

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.ezzy.vault.data.backup.BackupManager
import com.ezzy.vault.data.crypto.AttachmentStore
import com.ezzy.vault.data.crypto.DatabaseKey
import com.ezzy.vault.data.db.EzzyDatabase
import com.ezzy.vault.data.model.Seed
import com.ezzy.vault.data.repo.VaultRepository
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.security.SecureShare
import com.ezzy.vault.util.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled service locator. The app is small enough that a DI framework would add more
 * moving parts than it removes, and the encrypted database has to be constructed lazily anyway
 * so that the Keystore is only touched once something actually reads from the vault.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val databaseKey = DatabaseKey(appContext)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(appContext)
    val attachmentStore = AttachmentStore(appContext)

    val database: EzzyDatabase by lazy { EzzyDatabase.open(appContext, databaseKey.passphrase()) }
    val repository: VaultRepository by lazy { VaultRepository(database, attachmentStore) }
    val backupManager: BackupManager by lazy { BackupManager(repository) }

    /** Drops the key first: without it the remaining database bytes are just noise. */
    suspend fun eraseEverything() {
        runCatching { database.clearAllTables() }
        attachmentStore.deleteAll()
        SecureShare.clear(appContext)
        databaseKey.destroy()
        appContext.deleteDatabase(EzzyDatabase.NAME)
    }
}

class EzzyApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // A keystore or database failure here must not take the launcher icon down with it.
        container.scope.launch {
            runCatching {
                container.repository.seedIfEmpty()
                // Then catch the built-in types up to this build, for the installs that were
                // seeded long before it. Only ever runs when the seed itself has moved on.
                if (container.settings.seedRevision() < Seed.REVISION) {
                    container.repository.refreshBuiltInTemplates()
                    container.settings.setSeedRevision(Seed.REVISION)
                }
            }
        }
        // A copy or share staged in the previous run is plain bytes sitting in the cache. The
        // receiving app has long since read it, so the first thing this run does is wipe it.
        SecureShare.clear(this)
        registerActivityLifecycleCallbacks(LockWatcher())
    }

    /**
     * Re-arms the lock when the last EZZY screen leaves the foreground. The overlay does its own
     * check, so a vault opened from the sidebar does not keep the main app unlocked forever.
     */
    private inner class LockWatcher : ActivityLifecycleCallbacks {
        private var started = 0

        override fun onActivityStarted(activity: Activity) {
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            started--
            if (started <= 0) {
                started = 0
                AppLock.onBackgrounded()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}

/** Convenience accessor used by view models and the overlay service. */
val Context.appContainer: AppContainer
    get() = (applicationContext as EzzyApp).container
