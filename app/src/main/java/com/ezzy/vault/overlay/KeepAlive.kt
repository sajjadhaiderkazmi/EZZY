package com.ezzy.vault.overlay

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.getSystemService
import com.ezzy.vault.appContainer
import com.ezzy.vault.util.TriggerMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Brings the floating bar back whenever Android, or a phone maker's battery saver, has taken
 * it down while the user still has it switched on.
 *
 * Three things do that between them: the phone starting up, the app being updated, and a
 * quiet check every quarter of an hour. Each one only reads the user's own setting and starts
 * the bar if it should be up and is not — none of them ever turns it on by itself.
 */
object KeepAlive {

    const val ACTION_WATCHDOG = "com.ezzy.vault.overlay.WATCHDOG"
    private const val WATCHDOG_INTERVAL_MS = 15 * 60_000L

    /** Starts the bar if the user has it on in "Always active" and it is not up right now. */
    suspend fun ensureRunning(context: Context) {
        val settings = context.appContainer.settings.settings.first()
        val wanted = settings.overlayEnabled && settings.triggerMode == TriggerMode.ALWAYS_ACTIVE
        if (!wanted) {
            cancelWatchdog(context)
            return
        }
        if (!OverlayService.isRunning) OverlayService.start(context)
        scheduleWatchdog(context)
    }

    /** Fire-and-forget form of [ensureRunning], for places that cannot suspend. */
    fun ensureRunningAsync(context: Context) {
        val app = context.applicationContext
        app.appContainer.scope.launch { runCatching { ensureRunning(app) } }
    }

    /**
     * The next check. An exact, Doze-proof alarm on purpose: it is also what lets the bar's
     * service be started again from the background on Android 12 and later.
     */
    fun scheduleWatchdog(context: Context, delayMs: Long = WATCHDOG_INTERVAL_MS) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val at = SystemClock.elapsedRealtime() + delayMs
        val operation = watchdogIntent(context)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation)
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation)
            }
        }
    }

    fun cancelWatchdog(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(watchdogIntent(context))
    }

    private fun watchdogIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, KeepAliveReceiver::class.java).setAction(ACTION_WATCHDOG),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // ---- Phone settings that decide whether the bar survives ----------------

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    /** Opens the "don't optimise EZZY's battery" prompt, or the nearest screen to it. */
    fun openBatterySettings(context: Context): Boolean = launchFirst(
        context,
        listOf(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetails(context),
        ),
    )

    /**
     * Opens the phone maker's own "auto-start" or "background launch" list, where it has one.
     * These are what Xiaomi, Oppo, Realme, Vivo, Infinix and others use to kill apps that are
     * otherwise allowed to run — Android's own battery setting does not reach them.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.MainActivity",
        ).map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)) }
        return launchFirst(context, candidates + appDetails(context))
    }

    private fun appDetails(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /** Tries each screen in turn; a phone without one simply moves on to the next. */
    private fun launchFirst(context: Context, intents: List<Intent>): Boolean = intents.any { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}

/**
 * Wakes up on the phone starting, the app being updated, the clock changing and the bar's own
 * watchdog. Puts task reminders back with Android (it forgets them all on a restart) and brings
 * the floating bar back if it should be up.
 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        app.appContainer.scope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    "android.intent.action.QUICKBOOT_POWERON",
                    "com.htc.intent.action.QUICKBOOT_POWERON",
                    -> runCatching { app.appContainer.taskRepository.rescheduleAll() }
                }
                runCatching { KeepAlive.ensureRunning(app) }
            } finally {
                pending.finish()
            }
        }
    }
}
