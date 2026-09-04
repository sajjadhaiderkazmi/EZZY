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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ezzy.vault.MainActivity
import com.ezzy.vault.R
import com.ezzy.vault.UnlockActivity
import com.ezzy.vault.security.AppLock
import com.ezzy.vault.appContainer
import com.ezzy.vault.util.EzzySettings
import com.ezzy.vault.util.TriggerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Keeps EZZY reachable from inside other apps.
 *
 * In "Always active" mode it owns a draggable bubble that stays on screen and a foreground
 * notification that keeps it alive. In "On trigger" mode it owns nothing at all until the
 * [EzzyTileService] Quick Settings tile is tapped — which brings up that same bubble rather
 * than jumping straight to the panel, so the tap only puts a button within reach and the user
 * still chooses when to open it. That bubble times out on its own (or can be dropped on the
 * dismiss target early), and the moment nothing is left on screen the service steps aside via
 * [stopSelf], so there is no persistent button, listener or notification while idle.
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager

    private var settings: EzzySettings = EzzySettings()

    private var bubbleHost: OverlayViewHost? = null
    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    /** The drop target shown while the bubble is being dragged. */
    private var dismissHost: OverlayViewHost? = null
    private var dismissView: View? = null
    private val dismissArmed = mutableStateOf(false)

    private var autoHideJob: Job? = null

    /** Times out the button the tile summoned in "On trigger" mode — separate from [autoHideJob],
     * which times out the panel. */
    private var bubbleAutoHideJob: Job? = null

    /**
     * What the ring around the floating button is drawing. In "Always active" mode there is no
     * deadline, so it idles as a sweep; in "On trigger" mode it empties in step with
     * [bubbleAutoHideJob], which is the same countdown, so the time left is visible instead of
     * the button just vanishing.
     */
    private val bubbleRing = mutableStateOf<BubbleRing?>(null)

    /** Bumped on every restart so the ring starts its arc over rather than carrying on. */
    private var countdownToken = 0

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

            ACTION_REFRESH -> scope.launch {
                refreshSettings()
                // Only the ring can change from here, so the button is left exactly where the
                // user dragged it rather than being torn down and rebuilt in its default spot.
                // A countdown in progress is left alone: that ring is a timer, not decoration.
                if (bubbleView != null && panelView == null &&
                    bubbleRing.value !is BubbleRing.Countdown
                ) {
                    bubbleRing.value = idleRing()
                }
            }

            ACTION_SHOW_TRIGGER -> scope.launch {
                refreshSettings()
                when (settings.triggerMode) {
                    // The button is already permanent here, so tapping the tile can go
                    // straight to what it opens — there is nothing to "summon" first.
                    TriggerMode.ALWAYS_ACTIVE -> showPanel()
                    TriggerMode.ON_TRIGGER -> showTemporaryBubble()
                }
            }

            else -> scope.launch {
                refreshSettings()
                rebuildTriggers()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hidePanel(fromOnDestroy = true)
        hideDismissTarget()
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
        when (settings.triggerMode) {
            TriggerMode.ALWAYS_ACTIVE -> {
                // Nothing is counting down in this mode, so the ring simply idles.
                bubbleRing.value = idleRing()
                // Settings.canDrawOverlays() is unreliable on some OEM skins — MIUI reports
                // false even after the user has allowed it — so the real test is whether the
                // bubble could actually be placed.
                addBubble()
                if (bubbleView == null) {
                    Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG)
                        .show()
                    stopSelf()
                }
            }

            TriggerMode.ON_TRIGGER -> {
                // Nothing lives on screen in this mode by default — only the Quick Settings
                // tile brings up a button, via showTrigger() — so there is nothing here for
                // the service to stay alive for. Stepping aside now means no button, no
                // listener, and no ongoing notification until the tile is actually tapped.
                stopSelf()
            }
        }
    }

    private fun removeTriggers() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleHost?.stop()
        bubbleView = null
        bubbleHost = null
        bubbleParams = null
    }

    private fun addBubble() {
        val host = OverlayViewHost(this).also { it.start() }
        val view = host.composeView { OverlayBubble(ring = bubbleRing.value) }

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
     * What the Quick Settings tile summons in "On trigger" mode: the very same draggable
     * button "Always active" mode keeps up permanently, except this one is temporary — it
     * times out on its own, or can be dropped on the dismiss target early, and tapping it
     * opens the panel exactly like the permanent one does. The tap only puts a button within
     * reach; it never jumps straight to the panel on its own.
     */
    private fun showTemporaryBubble() {
        if (bubbleView == null) addBubble()
        if (bubbleView == null) {
            Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        restartBubbleAutoHide()
    }

    /** What the ring shows when nothing is counting down — the user's choice, in other words. */
    private fun idleRing(): BubbleRing? = if (settings.bubbleSweep) BubbleRing.Sweep else null

    private fun restartBubbleAutoHide() {
        bubbleAutoHideJob?.cancel()
        val seconds = settings.autoHide.seconds
        if (seconds <= 0) {
            // "Never": there is no deadline to draw, so the ring goes back to idling.
            bubbleRing.value = idleRing()
            return
        }
        bubbleRing.value = BubbleRing.Countdown(
            token = ++countdownToken,
            millis = seconds * 1000L,
        )
        bubbleAutoHideJob = scope.launch {
            delay(seconds * 1000L)
            // Nothing else is being hosted in this mode, so the whole service can step aside.
            stopSelf()
        }
    }

    /**
     * The circle the bubble is dropped onto to switch the bar off, the way a chat head is
     * dismissed. Purely visual: it is not touchable, and the drag listener does the hit test.
     */
    private fun showDismissTarget() {
        if (dismissView != null) return
        dismissArmed.value = false

        val host = OverlayViewHost(this).also { it.start() }
        val view = host.composeView { DismissTarget(armed = dismissArmed.value) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(DISMISS_MARGIN_DP)
        }

        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                dismissHost = host
                dismissView = view
            }
            .onFailure { host.stop() }
    }

    private fun hideDismissTarget() {
        dismissView?.let { runCatching { windowManager.removeView(it) } }
        dismissHost?.stop()
        dismissView = null
        dismissHost = null
        dismissArmed.value = false
    }

    /** True while the dragged bubble is close enough to the target to be released onto it. */
    private fun isOverDismissTarget(bubbleCenterX: Int, bubbleCenterY: Int): Boolean {
        val targetX = screenWidth() / 2f
        val targetY = screenHeight() - dp(DISMISS_MARGIN_DP) - dp(DISMISS_SIZE_DP) / 2f
        val dx = bubbleCenterX - targetX
        val dy = bubbleCenterY - targetY
        return hypot(dx, dy) <= dp(DISMISS_HIT_DP)
    }

    /**
     * What dropping the button onto the dismiss target does. In "Always active" mode that
     * button is the permanent one, so this really does turn the floating bar off. In "On
     * trigger" mode it is only the temporary one the tile just summoned, so dropping it just
     * ends this appearance early — the tile is still there for next time.
     */
    private fun dismissBubble() {
        when (settings.triggerMode) {
            TriggerMode.ALWAYS_ACTIVE -> {
                Toast.makeText(this, R.string.overlay_turned_off, Toast.LENGTH_SHORT).show()
                // Written on the application scope, not the service's: stopSelf() cancels the
                // service scope, and a half-finished write would leave the switch showing on
                // next launch.
                appContainer.scope.launch { appContainer.settings.setOverlayEnabled(false) }
                stopSelf()
            }

            TriggerMode.ON_TRIGGER -> stopSelf()
        }
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
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        showDismissTarget()
                    }
                    if (moved) {
                        val size = view.width.coerceAtLeast(dp(56))
                        params.x = (startX + dx).roundToInt()
                            .coerceIn(0, screenWidth() - size)
                        params.y = (startY + dy).roundToInt()
                            .coerceIn(0, screenHeight() - view.height.coerceAtLeast(dp(56)))
                        runCatching { windowManager.updateViewLayout(view, params) }
                        dismissArmed.value = isOverDismissTarget(
                            bubbleCenterX = params.x + size / 2,
                            bubbleCenterY = params.y + view.height.coerceAtLeast(dp(56)) / 2,
                        )
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val overTarget = moved && dismissArmed.value
                    hideDismissTarget()
                    when {
                        overTarget -> dismissBubble()
                        !moved && System.currentTimeMillis() - downAt < TAP_TIMEOUT_MS -> showPanel()
                        moved -> snapToEdge(view)
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
        // The button's own countdown no longer applies once the panel is what's on screen,
        // and its ring has nothing to say from behind the panel either.
        bubbleAutoHideJob?.cancel()
        bubbleAutoHideJob = null
        bubbleRing.value = null

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
                onInteraction = { restartAutoHide() },
                onRequestUnlock = { itemId ->
                    startActivity(
                        Intent(this, UnlockActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .apply {
                                if (itemId != null) {
                                    putExtra(UnlockActivity.EXTRA_ITEM_ID, itemId)
                                }
                            }
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
        restartAutoHide()
    }

    /** The bar closes itself after the chosen quiet period; any interaction restarts it. */
    private fun restartAutoHide() {
        autoHideJob?.cancel()
        val seconds = settings.autoHide.seconds
        if (seconds <= 0) return
        autoHideJob = scope.launch {
            delay(seconds * 1000L)
            hidePanel()
        }
    }

    /**
     * @param fromOnDestroy true when called as part of the service's own teardown — stopSelf()
     * must never be called again from inside that teardown.
     */
    private fun hidePanel(fromOnDestroy: Boolean = false) {
        autoHideJob?.cancel()
        autoHideJob = null
        // A guarded entry is confirmed for one visit of the bar, not for the rest of the day.
        AppLock.clearItemConfirmations()
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelHost?.stop()
        panelView = null
        panelHost = null

        if (fromOnDestroy) return

        if (settings.triggerMode != TriggerMode.ON_TRIGGER) {
            bubbleRing.value = idleRing()
            return
        }

        if (bubbleView != null) {
            // Closing the panel is not the same as being finished with the bar. The button the
            // tile summoned stays where it is and goes back to counting down on its own, so it
            // can be reopened, or dropped on the cross, until its time is up.
            restartBubbleAutoHide()
        } else {
            // Nothing of this mode's is left on screen, so the service steps aside.
            stopSelf()
        }
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
        const val ACTION_SHOW_TRIGGER = "com.ezzy.vault.overlay.SHOW_TRIGGER"
        const val ACTION_REFRESH = "com.ezzy.vault.overlay.REFRESH"

        private const val CHANNEL_ID = "ezzy_overlay"
        private const val NOTIFICATION_ID = 4211
        private const val TAP_TIMEOUT_MS = 400L

        /** Geometry of the drop target, shared by the window placement and the hit test. */
        private const val DISMISS_MARGIN_DP = 96
        private const val DISMISS_SIZE_DP = 64
        private const val DISMISS_HIT_DP = 60

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

        /** Shows the panel directly, regardless of trigger mode. */
        fun openPanel(context: Context) {
            runCatching {
                val intent = Intent(context, OverlayService::class.java).setAction(ACTION_OPEN_PANEL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /**
         * What the Quick Settings tile calls. In "Always active" mode the button is already
         * permanent, so this opens the panel directly; in "On trigger" mode it brings up a
         * temporary button instead of jumping straight to the panel.
         */
        fun showTrigger(context: Context) {
            runCatching {
                val intent = Intent(context, OverlayService::class.java).setAction(ACTION_SHOW_TRIGGER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /**
         * Re-reads the settings without disturbing anything on screen. Used when a preference
         * changes that only affects how the button is drawn.
         */
        fun refresh(context: Context) {
            runCatching {
                val intent = Intent(context, OverlayService::class.java).setAction(ACTION_REFRESH)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
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
