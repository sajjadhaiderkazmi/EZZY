package com.ezzy.vault.sync

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ezzy.vault.MainActivity
import com.ezzy.vault.R
import com.ezzy.vault.ui.nav.Routes

/**
 * The browser link's own notifications, apart from the quiet one the foreground service needs:
 * a heads-up the moment Chrome is connected, and a short "Syncing… → Done" each time data
 * actually moves. Status polls never show anything, so the shade stays calm.
 */
object SyncNotifier {

    private const val EVENTS_CHANNEL = "ezzy_browser_events"
    private const val ACTIVITY_CHANNEL = "ezzy_browser_activity"
    private const val CONNECTED_ID = 4822
    private const val SYNC_ID = 4823
    private const val DONE_VISIBLE_MS = 4_000L

    /** "EZZY is successfully connected with Chrome", as a heads-up. */
    fun connected(context: Context, browserName: String) {
        ensureChannels(context)
        notify(
            context,
            CONNECTED_ID,
            NotificationCompat.Builder(context, EVENTS_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("EZZY is successfully connected with Chrome")
                .setContentText("$browserName can now open your vault on this Wi-Fi.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openConnectBrowser(context))
                .build(),
        )
    }

    /** Shown while a sync with Chrome is running. */
    fun syncing(context: Context) {
        ensureChannels(context)
        notify(
            context,
            SYNC_ID,
            NotificationCompat.Builder(context, ACTIVITY_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Syncing with Chrome…")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openConnectBrowser(context))
                .build(),
        )
    }

    /** Replaces "Syncing…" with "Done", then clears itself a few seconds later. */
    fun syncDone(context: Context) {
        notify(
            context,
            SYNC_ID,
            NotificationCompat.Builder(context, ACTIVITY_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Sync done ✓")
                .setContentText("Your vault is up to date in Chrome.")
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setAutoCancel(true)
                .setTimeoutAfter(DONE_VISIBLE_MS)
                .setContentIntent(openConnectBrowser(context))
                .build(),
        )
    }

    /** Clears any sync notification, e.g. when a sync failed half way. */
    fun clearSync(context: Context) {
        NotificationManagerCompat.from(context).cancel(SYNC_ID)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        // Without the notification permission this is simply a no-op; sync itself still works.
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(EVENTS_CHANNEL, "Browser connected", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when Chrome connects to EZZY."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ACTIVITY_CHANNEL, "Browser sync activity", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows Syncing… and Done while Chrome syncs."
                setShowBadge(false)
            }
        )
    }

    private fun openConnectBrowser(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        12,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, Routes.SETTINGS_CONNECT_BROWSER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
