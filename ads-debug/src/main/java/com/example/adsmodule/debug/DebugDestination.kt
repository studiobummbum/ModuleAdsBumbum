package com.example.adsmodule.debug

import androidx.fragment.app.Fragment
import com.example.adsmodule.debug.ui.DashboardHomeFragment
import com.example.adsmodule.debug.ui.EventLogFragment
import com.example.adsmodule.debug.ui.FullActivityGestureSimulatorFragment
import com.example.adsmodule.debug.ui.FullscreenLockInspectorFragment
import com.example.adsmodule.debug.ui.LifecycleSimulatorFragment
import com.example.adsmodule.debug.ui.NativeLayoutGalleryFragment
import com.example.adsmodule.debug.ui.NavigationGraphInspectorFragment
import com.example.adsmodule.debug.ui.PlacementInspectorFragment
import com.example.adsmodule.debug.ui.RefillQueueInspectorFragment
import com.example.adsmodule.debug.ui.RemoteConfigEditorFragment
import com.example.adsmodule.debug.ui.SdkBackendSelectorFragment
import com.example.adsmodule.debug.ui.StorageInspectorFragment
import com.example.adsmodule.debug.ui.TurnbackSimulatorFragment
import com.example.adsmodule.debug.ui.ViewPagerBoundarySimulatorFragment
import com.example.adsmodule.debug.ui.WeightedListInspectorFragment

enum class DebugDestination(
    val title: String,
    val factory: () -> Fragment,
) {
    DASHBOARD("Dashboard", ::DashboardHomeFragment),
    REMOTE_CONFIG("Remote Config Editor", ::RemoteConfigEditorFragment),
    WEIGHTED_LIST("Weighted List Inspector", ::WeightedListInspectorFragment),
    PLACEMENT("Placement Inspector", ::PlacementInspectorFragment),
    STORAGE("Storage Inspector", ::StorageInspectorFragment),
    TURNBACK("Turnback Simulator", ::TurnbackSimulatorFragment),
    REFILL("Refill Queue Inspector", ::RefillQueueInspectorFragment),
    FULLSCREEN_LOCK("Fullscreen Lock Inspector", ::FullscreenLockInspectorFragment),
    LIFECYCLE("Lifecycle Simulator", ::LifecycleSimulatorFragment),
    NAV_GRAPH("Navigation Graph Inspector", ::NavigationGraphInspectorFragment),
    VIEWPAGER("ViewPager Boundary Simulator", ::ViewPagerBoundarySimulatorFragment),
    FULL_GESTURE("Full Activity Gesture Simulator", ::FullActivityGestureSimulatorFragment),
    EVENT_LOG("Event Log", ::EventLogFragment),
    LAYOUT_GALLERY("Native Layout Gallery", ::NativeLayoutGalleryFragment),
    SDK_BACKEND("SDK Backend (Fake / AdMob Test)", ::SdkBackendSelectorFragment),
}
