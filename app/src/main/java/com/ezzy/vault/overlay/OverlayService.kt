package com.ezzy.vault.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ezzy.vault.MainActivity
import com.ezzy.vault.R
import com.ezzy.vault.UnlockActivity
import com.ezzy.vault.appContainer
import com.ezzy.vault.util.EzzySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Keeps EZZY reachable from inside other apps.
 *
 * It owns up to three windows: a draggable bubble, an invisible edge strip that watches for the
 * opening swipe, and the panel itself. Only the panel is interactive when it is open; the
 * triggers stay out of the way so the app underneath behaves exactly as it did before.
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager

    private var settings: EzzySettings = EzzySettings()

    private var bubbleHost: OverlayViewHost? = null
    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var gestureView: View? = null

    private var panelHost: OverlayViewHost? = null
    private var panelView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService<WindowManager>() ?: error("WindowManager unavailable")
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_OPEN_PANEL -> scope.launch {
                refreshSettings()
                showPanel()
            }

            else -> scope.launch {
                refreshSettings()
                rebuildTriggers()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hidePanel()
        removeTriggers()
        scope.cancel()
        super.onDestroy()
    }

    // ---- Foreground notification -----------------------------------------

    private fun startInForeground() {
        val manager = getSystemService<NotificationManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_channel_name),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = getString(R.string.overlay_channel_description)
                    setShowBadge(false)
                }
            )
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_turn_off), stop)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---- Triggers ---------------------------------------------------------

    private suspend fun refreshSettings() {
        settings = appContainer.settings.settings.first()
    }

    private fun rebuildTriggers() {
        removeTriggers()
        // Settings.canDrawOverlays() is unreliable on some OEM skins — MIUI reports false even
        // after the user has allowed it — so the real test is whether addView succeeds.
        if (settings.bubbleTrigger) addBubble()
        if (settings.gestureEnabled) addGestureCatcher()

        if ((settings.bubbleTrigger || settings.gestureEnabled) &&
            bubbleView == null &&
            gestureView == null
        ) {
            // A trigger was asked for but nothing could be placed on screen, so the permission
            // really is missing. Say so — silently doing nothing is the worst outcome here.
            Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun removeTriggers() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleHost?.stop()
        bubbleView = null
        bubbleHost = null
        bubbleParams = null

        gestureView?.let { runCatching { windowManager.removeView(it) } }
        gestureView = null
    }

    private fun addBubble() {
        val host = OverlayViewHost(this).also { it.start() }
        val view = host.composeView { OverlayBubble() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth() - dp(72)
            y = screenHeight() / 3
        }

        view.setOnTouchListener(BubbleDragListener(params))
        runCatching { windowManager.addView(view, params) }
            .onFailure {
                host.stop()
                return
            }

        bubbleHost = host
        bubbleView = view
        bubbleParams = params
    }

    /**
     * Places the gesture window across the lower part of the screen.
     *
     * It has to be full width and genuinely tall: a four-finger gesture only registers when
     * every finger lands inside the window, since fingers outside it go to the app below. It is
     * also offset above the navigation area so the system's own Home swipe still works.
     */
    private fun addGestureCatcher() {
        val view = GestureCatcherView(
            context = this,
            gesture = settings.gesture,
            onTriggered = { showPanel() },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (screenHeight() * settings.gestureArea.fraction).roundToInt(),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }

        runCatching { windowManager.addView(view, params) }.onSuccess { gestureView = view }
    }

    /** Drags the bubble, and treats a short, still press as a tap that opens the panel. */
    private inner class BubbleDragListener(
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {

        private val slop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var downAt = 0L

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    downAt = System.currentTimeMillis()
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        params.x = (startX + dx).roundToInt()
                            .coerceIn(0, screenWidth() - view.width.coerceAtLeast(dp(56)))
                        params.y = (startY + dy).roundToInt()
                            .coerceIn(0, screenHeight() - view.height.coerceAtLeast(dp(56)))
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved && System.currentTimeMillis() - downAt < TAP_TIMEOUT_MS) {
                        showPanel()
                    } else if (moved) {
                        snapToEdge(view)
                    }
                }
            }
            return true
        }

        private fun snapToEdge(view: View) {
            val width = view.width.coerceAtLeast(dp(56))
            val toLeft = params.x + width / 2 < screenWidth() / 2
            params.x = if (toLeft) dp(8) else screenWidth() - width - dp(8)
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    // ---- Panel ------------------------------------------------------------

    private fun showPanel() {
        if (panelView != null) return

        val host = OverlayViewHost(this).also { it.start() }
        val view = host.composeView {
            OverlayPanel(
                maskSecrets = settings.maskSecrets,
                requireUnlock = settings.biometricLock,
                clipboardClearSeconds = settings.clipboardClearSeconds,
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                onDismiss = { hidePanel() },
                onOpenApp = { route ->
                    hidePanel()
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MainActivity.EXTRA_ROUTE, route)
                    )
                },
                onRequestUnlock = {
                    startActivity(
                        Intent(this, UnlockActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )
        }

        var flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (settings.blockScreenshots) flags = flags or WindowManager.LayoutParams.FLAG_SECURE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Wrapped so the Back key closes the bar; a raw ComposeView cannot intercept it.
        val root = OverlayRootView(this) { hidePanel() }
        root.attachTo(host)
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        runCatching { windowManager.addView(root, params) }
            .onFailure {
                host.stop()
                return
            }

        panelHost = host
        panelView = root
    }

    private fun hidePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelHost?.stop()
        panelView = null
        panelHost = null
    }

    // ---- Small helpers ----------------------------------------------------

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    companion object {
        const val ACTION_STOP = "com.ezzy.vault.overlay.STOP"
        const val ACTION_OPEN_PANEL = "com.ezzy.vault.overlay.OPEN_PANEL"

        private const val CHANNEL_ID = "ezzy_overlay"
        private const val NOTIFICATION_ID = 4211
        private const val TAP_TIMEOUT_MS = 400L

        /**
         * @return null on success, or a message explaining why the bar could not be started.
         *
         * From Android 12 a foreground service may not be started while the app counts as
         * backgrounded, and an activity-result callback runs in exactly that window — so this
         * never throws at the call site.
         */
        fun start(context: Context): String? = runCatching {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            null
        }.getOrElse { error ->
            error.message ?: "Android refused to start the floating bar service"
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
