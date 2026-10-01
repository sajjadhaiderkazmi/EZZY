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

/**
 * Keeps the browser link reachable while EZZY is in the background. Android puts a backgrounded
 * app to sleep within minutes; a foreground service — and the quiet notification that comes with
 * it — is the honest way to stay awake for the browser, rather than anything hidden.
 */
class SyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val link = appContainer.browserLink
        if (intent?.action == ACTION_STOP) {
            link.stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!link.needsServer || !link.ensureServerRunning()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startInForeground() {
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
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.sync_notification_title))
            .setContentText(getString(R.string.sync_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.sync_action_pause), stop)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
