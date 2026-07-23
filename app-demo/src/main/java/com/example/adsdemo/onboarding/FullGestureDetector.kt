package com.example.adsdemo.onboarding

import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Observes horizontal forward swipes without intercepting child touch handling.
 * Excludes CTA, media, close X and other clickable Native assets.
 */
class FullGestureDetector(
    host: View,
    private val isForwardDx: (dx: Float) -> Boolean,
    private val isExcluded: (event: MotionEvent) -> Boolean,
    private val onSwipeForward: () -> Unit,
    private val onDebugMotion: ((dx: Float, velocity: Float, threshold: Float) -> Unit)? = null,
) {
    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private val minSwipeDistance = touchSlop * 4
    private val minVelocity =
        ViewConfiguration.get(host.context).scaledMinimumFlingVelocity.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var excluded = false
    private var lastSwipeAt = 0L
    private var velocityTracker: VelocityTracker? = null

    var lastDx: Float = 0f
        private set
    var lastVelocity: Float = 0f
        private set
    val distanceThreshold: Float get() = minSwipeDistance.toFloat()
    val velocityThreshold: Float get() = minVelocity
    var excludedHit: Boolean = false
        private set

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                tracking = true
                excluded = isExcluded(event)
                excludedHit = excluded
                lastDx = 0f
                lastVelocity = 0f
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                onDebugMotion?.invoke(0f, 0f, distanceThreshold)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                velocityTracker?.addMovement(event)
                lastDx = event.x - downX
                onDebugMotion?.invoke(lastDx, lastVelocity, distanceThreshold)
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                tracking = false
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityX = velocityTracker?.xVelocity ?: 0f
                lastVelocity = velocityX
                val dx = event.x - downX
                val dy = event.y - downY
                lastDx = dx
                onDebugMotion?.invoke(dx, velocityX, distanceThreshold)
                velocityTracker?.recycle()
                velocityTracker = null
                if (excluded) return false
                if (abs(dx) < minSwipeDistance || abs(dx) < abs(dy)) return false
                if (!isForwardDx(dx)) return false
                val longDrag = abs(dx) >= minSwipeDistance * 2
                val flingOk = abs(velocityX) >= minVelocity && abs(dx) >= minSwipeDistance
                if (!longDrag && !flingOk) return false
                val now = SystemClock.uptimeMillis()
                if (now - lastSwipeAt < DEBOUNCE_MS) return false
                lastSwipeAt = now
                onSwipeForward()
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return false
    }

    fun cancel() {
        tracking = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    companion object {
        private const val DEBOUNCE_MS = 400L

        fun hitTest(view: View, rawX: Float, rawY: Float): Boolean {
            if (!view.isShown) return false
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val left = loc[0].toFloat()
            val top = loc[1].toFloat()
            val right = left + view.width
            val bottom = top + view.height
            return rawX in left..right && rawY in top..bottom
        }
    }
}
