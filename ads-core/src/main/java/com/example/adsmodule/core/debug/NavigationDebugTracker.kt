package com.example.adsmodule.core.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Host-reported navigation state for debug dashboards.
 * Does not hold Activity/Fragment references — only plain names/ids.
 */
public class NavigationDebugTracker {
    private val mutableState = MutableStateFlow(NavigationDebugState())

    public val state: StateFlow<NavigationDebugState> = mutableState.asStateFlow()

    public fun report(
        activityName: String? = mutableState.value.activityName,
        fragmentName: String? = mutableState.value.fragmentName,
        pagerIndex: Int? = mutableState.value.pagerIndex,
        screenLabel: String? = mutableState.value.screenLabel,
    ) {
        mutableState.value = NavigationDebugState(
            activityName = activityName,
            fragmentName = fragmentName,
            pagerIndex = pagerIndex,
            screenLabel = screenLabel,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    public fun clear() {
        mutableState.value = NavigationDebugState()
    }
}

public data class NavigationDebugState(
    val activityName: String? = null,
    val fragmentName: String? = null,
    val pagerIndex: Int? = null,
    val screenLabel: String? = null,
    val updatedAtMillis: Long = 0L,
)
