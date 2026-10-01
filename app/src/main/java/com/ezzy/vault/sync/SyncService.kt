package com.ezzy.vault.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ezzy.vault.MainActivity
import com.ezzy.vault.R
import com.ezzy.vault.appContainer
import com.ezzy.vault.ui.nav.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the browser link reachable while EZZY is in the background. Android puts a backgrounded
 * app to sleep within minutes; a foreground service — and the quiet notification that comes with
 * it — is the honest way to stay awake for the browser, rather than anything hidden.
 */
class SyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val link = appContainer.browserLink
        if (intent?.action == ACTION_STOP) {
            link.stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(link.state.value)
        if (!link.needsServer || !link.ensureServerRunning()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Keep the notification's wording true to where the link stands.
        if (watching == null) {
            watching = scope.launch {
                link.state.collect { state ->
                    if (state != LinkState.Off) {
                        runCatching {
                            getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, buildNotification(state))
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground(state: LinkState) {
        val manager = getSystemService<NotificationManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.sync_channel_name),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = getString(R.string.sync_channel_description)
                    setShowBadge(false)
                }
            )
        }
        val notification = buildNotification(state)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: LinkState): Notification {
        val open = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, Routes.SETTINGS_CONNECT_BROWSER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            11,
            Intent(this, SyncService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when (state) {
            is LinkState.Linked -> "EZZY is connected with Chrome" to "Syncs automatically while both are on the same Wi-Fi."
            is LinkState.SetPin, is LinkState.WaitingForBrowser -> "Finishing Chrome setup" to "Set your PIN in EZZY to finish connecting."
            else -> "Waiting for Chrome to connect…" to "Type the address and code from EZZY into the Chrome extension."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.sync_action_pause), stop)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ezzy_browser_sync"
        private const val NOTIFICATION_ID = 4821
        private const val ACTION_STOP = "com.ezzy.vault.sync.STOP"

        /** Safe to call from anywhere; Android refuses a background start, which is fine to ignore. */
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, SyncService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SyncService::class.java)) }
        }
    }
}
