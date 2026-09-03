package com.ezzy.vault.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.ezzy.vault.util.Gesture
import kotlin.math.abs

/**
 * The invisible area that listens for the opening gesture.
 *
 * Android does not let one app read touches meant for another, so a gesture outside EZZY is
 * only possible inside a window we own — this view is that window. It has to be roomy: a
 * four-finger gesture only registers if every finger lands inside it, because fingers that land
 * outside are delivered to the app below instead, and that is exactly why a thin strip could
 * never see four of them.
 *
 * Touches that land here do not reach the app underneath. That is unavoidable — seeing a touch
 * is taking it — which is why the area's height is the user's to choose.
 */
@SuppressLint("ViewConstructor")
class GestureCatcherView(
    context: Context,
    private val gesture: Gesture,
    private val onTriggered: () -> Unit,
) : View(context) {

    private val travelThreshold = TRAVEL_DP * resources.displayMetrics.density
    private val tapSlop = ViewConfiguration.get(context).scaledTouchSlop * 2f
    private val tapTimeout = ViewConfiguration.getTapTimeout() + 150L

    private var startX = 0f
    private var startY = 0f
    private var downAt = 0L
    private var maxPointers = 0
    private var tracking = false
    private var fired = false

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
                if (!tracking || fired || gesture != Gesture.FOUR_DOWN) return true
                maxPointers = maxOf(maxPointers, event.pointerCount)

                val dx = event.rawX - startX
                val dy = event.rawY - startY

                // Fire mid-swipe rather than on lift, so the bar keeps up with the fingers.
                if (dy <= travelThreshold || abs(dx) > dy) return true
                if (maxPointers >= REQUIRED_SWIPE_FINGERS) {
                    fired = true
                    onTriggered()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tracking && !fired &&
                    event.actionMasked == MotionEvent.ACTION_UP &&
                    gesture == Gesture.TWO_DOUBLE_TAP
                ) {
                    handleDoubleTap(event)
                }
                tracking = false
                maxPointers = 0
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** A second still, quick two-finger tap inside the window opens the bar. */
    private fun handleDoubleTap(event: MotionEvent) {
        val moved = maxOf(abs(event.rawX - startX), abs(event.rawY - startY))
        val heldFor = System.currentTimeMillis() - downAt
        if (moved > tapSlop || heldFor > tapTimeout || maxPointers < REQUIRED_TAP_FINGERS) {
            lastTapAt = 0L
            return
        }

        val now = System.currentTimeMillis()
        if (lastTapAt != 0L && now - lastTapAt <= DOUBLE_TAP_WINDOW_MS) {
            lastTapAt = 0L
            fired = true
            onTriggered()
        } else {
            lastTapAt = now
        }
    }

    private companion object {
        /** How far a swipe must travel before the bar opens. */
        const val TRAVEL_DP = 60f

        /**
         * Fingers are counted with "at least", not "exactly": on a wide area a fifth finger
         * often brushes the glass, and refusing the gesture for that is only frustrating.
         */
        const val REQUIRED_SWIPE_FINGERS = 4
        const val REQUIRED_TAP_FINGERS = 2
        const val DOUBLE_TAP_WINDOW_MS = 450L
    }
}
