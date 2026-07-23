package com.example.adsmodule.core.debug

import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.fullscreen.FullscreenLockSnapshot
import com.example.adsmodule.core.turnback.AdClickTokenSnapshot

public data class AdsDebugDashboardSnapshot(
    val sessionId: SessionId?,
    val configVersion: Long?,
    val configContentHash: String?,
    val navigation: NavigationDebugState,
    val fullscreenLock: FullscreenLockSnapshot,
    val clickTokens: AdClickTokenSnapshot,
    val readyObjectCount: Int,
    val refillInFlightCount: Int,
    val activeLoadCycleCount: Int,
    val latestEvent: DebugEvent?,
    val capturedAtMillis: Long,
)
