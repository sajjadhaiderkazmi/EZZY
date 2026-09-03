package com.ezzy.vault.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.ezzy.vault.util.Gesture
import com.ezzy.vault.util.SwipeDirection
import kotlin.math.abs

/**
 * The invisible strip that listens for the opening gestures on one screen edge.
 *
 * Android does not let one app read touches meant for another, so a global gesture is only
 * possible inside a window we own — this strip is that window. It fires the moment a swipe
 * matches one of [gestures] by both finger count and direction; anything else is ignored, so an
 * ordinary drag or a different finger count never opens the bar by accident.
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

    private val travelThreshold = TRAVEL_DP * resources.displayMetrics.density

    private var startX = 0f
    private var startY = 0f
    private var maxPointers = 0
    private var tracking = false
    private var fired = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
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

                val direction = directionOf(dx, dy)
                if (gestures.any { it.fingers == maxPointers && it.direction == direction }) {
                    fired = true
                    onTriggered()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // A swipe that never reached the threshold is simply a miss.
                tracking = false
                maxPointers = 0
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** The axis that moved further wins, so a slightly crooked swipe still reads cleanly. */
    private fun directionOf(dx: Float, dy: Float): SwipeDirection = when {
        abs(dy) >= abs(dx) -> if (dy < 0) SwipeDirection.UP else SwipeDirection.DOWN
        else -> if (dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
    }

    private companion object {
        /** How far the swipe must travel before the bar opens. */
        const val TRAVEL_DP = 55f
    }
}
