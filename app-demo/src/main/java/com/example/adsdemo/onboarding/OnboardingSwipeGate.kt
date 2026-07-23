package com.example.adsdemo.onboarding

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Directional swipe detector used while ViewPager2 user input is disabled.
 * Forward and backward both route through [OnboardingBoundaryCoordinator].
 */
class OnboardingSwipeGate(
    pager: ViewPager2,
    private val onForward: () -> Unit,
    private val onBackward: () -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(pager.context).scaledTouchSlop
    private val minSwipeDistance = touchSlop * 3
    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    init {
        val recycler = pager.getChildAt(0) as? RecyclerView
        recycler?.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(
                    rv: RecyclerView,
                    e: MotionEvent,
                ): Boolean {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = e.x
                            downY = e.y
                            tracking = true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!tracking) return false
                            tracking = false
                            val dx = e.x - downX
                            val dy = e.y - downY
                            if (abs(dx) < minSwipeDistance || abs(dx) < abs(dy)) {
                                return false
                            }
                            if (dx < 0) {
                                onForward()
                            } else {
                                onBackward()
                            }
                            return true
                        }
                        MotionEvent.ACTION_CANCEL -> tracking = false
                    }
                    return false
                }
            },
        )
    }
}
