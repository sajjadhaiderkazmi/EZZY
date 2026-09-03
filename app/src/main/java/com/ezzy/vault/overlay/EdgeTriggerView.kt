package com.ezzy.vault.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.ezzy.vault.util.Gesture
import com.ezzy.vault.util.GestureKind
import com.ezzy.vault.util.SwipeDirection
import kotlin.math.abs

/**
 * The invisible strip that listens for the opening gestures on one screen edge.
 *
 * Android does not let one app read touches meant for another, so a global gesture is only
 * possible inside a window we own — this strip is that window. It fires only when a touch
 * matches one of [gestures] on every count: number of fingers, whether it travelled, and which
 * way. Anything else is ignored, so a stray drag or the wrong finger count never opens the bar.
 *
 * Touches that land here do not reach the app underneath. That is unavoidable — detecting a
 * touch is consuming it — which is why the strip is thin and its length is configurable.
 */
@SuppressLint("ViewConstructor")
class EdgeTriggerView(
    context: Context,
    private val gestures: List<Gesture>,
    private val onTriggered: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val travelThreshold = TRAVEL_DP * density
    private val tapSlop = ViewConfiguration.get(context).scaledTouchSlop * 1.5f
    private val tapTimeout = ViewConfiguration.getTapTimeout() + 120L

    private val swipes = gestures.filter { it.kind == GestureKind.SWIPE }
    private val taps = gestures.filter { it.kind == GestureKind.TAP }
    private val doubleTaps = gestures.filter { it.kind == GestureKind.DOUBLE_TAP }

    private var startX = 0f
    private var startY = 0f
    private var downAt = 0L
    private var maxPointers = 0
    private var tracking = false
    private var fired = false

    private var lastTapFingers = 0
    private var lastTapAt = 0L

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                downAt = System.currentTimeMillis()
                maxPointers = 1
                tracking = true
                fired = false
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, event.pointerCount)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking || fired) return true
                maxPointers = maxOf(maxPointers, event.pointerCount)

                val dx = event.rawX - startX
                val dy = event.rawY - startY

                // Fire mid-swipe rather than on lift: waiting for the fingers to leave the
                // glass makes the bar feel a beat behind the gesture.
                if (maxOf(abs(dx), abs(dy)) <= travelThreshold) return true

                val direction = if (dy < 0) SwipeDirection.UP else SwipeDirection.DOWN
                if (abs(dy) < abs(dx)) {
                    // Mostly sideways: that belongs to whatever is underneath, not to us.
                    tracking = false
                    return true
                }
                if (swipes.any { it.fingers == maxPointers && it.direction == direction }) {
                    fired = true
                    onTriggered()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tracking && !fired && event.actionMasked == MotionEvent.ACTION_UP) {
                    handleTap(event)
                }
                tracking = false
                maxPointers = 0
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** A lift that never moved and never lingered counts as a tap by [maxPointers] fingers. */
    private fun handleTap(event: MotionEvent) {
        val moved = maxOf(abs(event.rawX - startX), abs(event.rawY - startY))
        val heldFor = System.currentTimeMillis() - downAt
        if (moved > tapSlop || heldFor > tapTimeout) {
            lastTapFingers = 0
            return
        }

        val fingers = maxPointers
        val now = System.currentTimeMillis()

        if (doubleTaps.any { it.fingers == fingers } &&
            lastTapFingers == fingers &&
            now - lastTapAt <= DOUBLE_TAP_WINDOW_MS
        ) {
            lastTapFingers = 0
            fired = true
            onTriggered()
            return
        }

        if (taps.any { it.fingers == fingers }) {
            fired = true
            onTriggered()
            return
        }

        // Remember it only if a double tap of this width is something we are waiting for.
        lastTapFingers = if (doubleTaps.any { it.fingers == fingers }) fingers else 0
        lastTapAt = now
    }

    private companion object {
        /** How far a swipe must travel before the bar opens. */
        const val TRAVEL_DP = 55f
        const val DOUBLE_TAP_WINDOW_MS = 400L
    }
}
