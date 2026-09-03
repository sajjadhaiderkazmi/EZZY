package com.ezzy.vault.overlay

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ezzy.vault.MainActivity
import com.ezzy.vault.R
import com.ezzy.vault.appContainer
import com.ezzy.vault.ui.nav.Routes
import com.ezzy.vault.util.EzzySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Puts EZZY next to Wi-Fi, Bluetooth and Hotspot in Quick Settings.
 *
 * This is the sole way in while the floating bar is in "On trigger" mode: nothing sits on
 * screen until this tile is tapped. The tap does not jump straight to the panel — it brings up
 * the same draggable button "Always active" mode keeps up permanently, except this one is
 * temporary, closing itself after the chosen quiet period (or sooner, if dropped on the
 * dismiss target). Tapping that button is what actually opens the panel. In "Always active"
 * mode the button is already on screen, so the tile just opens the panel directly there.
 *
 * The tile always renders as inactive rather than reflecting whether something is currently
 * on screen — it is a "do something" tile, like a screen-recorder shortcut, not a live on/off
 * toggle, and a permanently-lit tile would misread as "something is happening" when usually
 * nothing is.
 */
class EzzyTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    /** Kept in sync by [onStartListening] so [onClick] never has to block on a read. */
    private var latest: EzzySettings = EzzySettings()

    override fun onStartListening() {
        super.onStartListening()
        listenJob = scope.launch {
            appContainer.settings.settings.collect { settings ->
                latest = settings
                render(settings)
            }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        // Sensitive action behind a locked device: TileService's own unlock flow handles both
        // cases (already unlocked runs immediately), which is the documented safe pattern.
        unlockAndRun {
            if (latest.overlayEnabled) {
                OverlayService.showTrigger(this)
            } else {
                // Nothing to trigger yet — send the user to turn the bar on first.
                openSettings()
            }
        }
    }

    private fun render(settings: EzzySettings) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.label = getString(R.string.app_name)
        // Always inactive — see the class doc for why this never switches to STATE_ACTIVE.
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Tile.subtitle does not exist on the framework before Q — setting it on an older
            // device would throw, not just render blank.
            tile.subtitle = getString(
                if (settings.overlayEnabled) {
                    R.string.tile_subtitle_on
                } else {
                    R.string.tile_subtitle_off
                }
            )
        }
        tile.updateTile()
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_ROUTE, Routes.SETTINGS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
