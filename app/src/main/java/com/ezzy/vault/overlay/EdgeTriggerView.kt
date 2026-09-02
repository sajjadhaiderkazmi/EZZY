package com.ezzy.vault.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * The invisible strip that listens for the opening gesture.
 *
 * Android does not let one app read touches meant for another, so a global gesture is only
 * possible inside a window we own. This strip is that window: it is a few dp thick, sits along
 * one screen edge, and only claims the gesture once it is clearly a deliberate swipe — anything
 * shorter is ignored so the app underneath keeps behaving normally.
 */
@SuppressLint("ViewConstructor")
class EdgeTriggerView(
    context: Context,
    private val requireTwoFingers: Boolean,
    private val onTriggered: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val travelThreshold = TRAVEL_DP * density

    private var startY = 0f
    private var startX = 0f
    private var maxPointers = 0
    private var tracking = false
    private var fired = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.rawY
                startX = event.rawX
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

                val travelledUp = startY - event.rawY
                val drift = abs(event.rawX - startX)

                // Fire mid-gesture: waiting for the finger to lift makes it feel sluggish.
                if (travelledUp > travelThreshold && drift < travelledUp && hasEnoughFingers()) {
                    fired = true
                    onTriggered()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // A swipe that never reached the threshold is simply a miss: the bar stays shut.
                tracking = false
                maxPointers = 0
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hasEnoughFingers(): Boolean = if (requireTwoFingers) maxPointers >= 2 else true

    private companion object {
        /** How far up the swipe must travel before the bar opens. */
        const val TRAVEL_DP = 55f
    }
}
