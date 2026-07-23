package com.example.adsmodule.debug.ui

import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.onboarding.full.FullExitSource
import com.example.adsmodule.debug.R
import kotlinx.coroutines.launch

class RemoteConfigEditorFragment : BaseDebugInspectorFragment() {
    private var keyInput: EditText? = null
    private var jsonInput: EditText? = null

    override fun setupActions(container: LinearLayout) {
        keyInput = EditText(requireContext()).apply {
            hint = "config key"
            setText("native_language_config_1")
            minHeight = (48 * resources.displayMetrics.density).toInt()
        }
        jsonInput = EditText(requireContext()).apply {
            hint = "raw JSON override"
            minLines = 6
            setText(
                """
                {
                  "enable": true,
                  "isOrganic": true,
                  "list_ads": [
                    {"enable_ad": true, "weight": 100, "adunit": "debug-native"}
                  ]
                }
                """.trimIndent(),
            )
        }
        container.addView(keyInput)
        container.addView(jsonInput)
        addAction(container, getString(R.string.debug_apply)) {
            val key = ConfigKey(keyInput?.text?.toString().orEmpty().ifBlank { "native_language_config_1" })
            val json = jsonInput?.text?.toString().orEmpty()
            api.writeConfigOverride(key, json)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = api.refreshConfig()
                setText("refresh=${result::class.simpleName}\n\n" + placementsText())
            }
        }
        addAction(container, getString(R.string.debug_refresh)) {
            viewLifecycleOwner.lifecycleScope.launch {
                api.refreshConfig()
                render()
            }
        }
    }

    override fun render() {
        setText(placementsText())
    }

    private fun placementsText(): String = buildString {
        api.placements().forEach { placement ->
            appendLine("=== ${placement.configKey.value} enable=${placement.enable}")
            placement.originalItems.forEach {
                appendLine(
                    "  orig#${it.originalIndex} w=${it.weight} enable=${it.enableAd} " +
                        "type=${it.type} unit=${it.adunit}",
                )
            }
            appendLine("  runtimeOrder:")
            placement.runtimeOrder.forEachIndexed { index, item ->
                appendLine("    [$index] w=${item.weight} idx=${item.originalIndex} ${item.adunit}")
            }
            appendLine()
        }
    }
}

class WeightedListInspectorFragment : BaseDebugInspectorFragment() {
    override fun observe() {
        collectWhileStarted(api.weightedLoadStates()) { states ->
            setText(
                buildString {
                    if (states.isEmpty()) appendLine("(no active/recent load cycles)")
                    states.values.forEach { state ->
                        appendLine("cycle=${state.cycleId.value}")
                        appendLine("  config=${state.configKey.value}")
                        appendLine("  active=${state.isActive} terminal=${state.terminalReason}")
                        appendLine("  currentRuntime=${state.currentRuntimeIndex} elapsed=${state.elapsedMillis}")
                        appendLine("  ordered=${state.orderedItems.map { "${it.weight}:${it.adunit}" }}")
                        appendLine("  attempts=${state.attempts.map { "${it.outcome}/${it.adunit}" }}")
                        appendLine()
                    }
                },
            )
        }
    }

    override fun render() = Unit
}

class PlacementInspectorFragment : BaseDebugInspectorFragment() {
    override fun setupActions(container: LinearLayout) {
        addAction(container, getString(R.string.debug_refresh)) { render() }
    }

    override fun render() {
        setText(
            buildString {
                api.placements().forEach { p ->
                    appendLine(p.configKey.value)
                    appendLine("  enable=${p.enable} organic=${p.isOrganic}")
                    p.originalItems.forEach { item ->
                        appendLine(
                            "  #${item.originalIndex} enable=${item.enableAd} w=${item.weight} " +
                                "type=${item.type} unit=${item.adunit}",
                        )
                    }
                    appendLine("  runtime: ${p.runtimeOrder.map { "${it.weight}@${it.originalIndex}" }}")
                    appendLine()
                }
            },
        )
    }
}

class StorageInspectorFragment : BaseDebugInspectorFragment() {
    override fun setupActions(container: LinearLayout) {
        addAction(container, getString(R.string.debug_refresh)) { render() }
    }

    override fun observe() {
        collectWhileStarted(api.dashboard) { render() }
    }

    override fun render() {
        val snap = api.storageSnapshot()
        setText(
            buildString {
                appendLine("objects=${snap.objects.size} reservations=${snap.reservations.size}")
                snap.objects.forEach { obj ->
                    appendLine(
                        "${obj.objectId.value} ${obj.state} w=${obj.sourceWeight} " +
                            "${obj.sourceConfigKey.value} screen=${obj.screenInstanceId?.value} " +
                            "type=${obj.sourceType} unit=${obj.sourceAdunit}",
                    )
                }
                appendLine()
                appendLine("readySlots:")
                snap.readySlots.forEach { (slot, ids) ->
                    appendLine("  ${slot.configKey.value}/${slot.screenInstanceId?.value}=${ids.map { it.value }}")
                }
            },
        )
    }
}

class TurnbackSimulatorFragment : BaseDebugInspectorFragment() {
    override fun setupActions(container: LinearLayout) {
        addAction(container, "Preview eligible") { render() }
        addAction(container, "Borrow with fresh token") {
            val session = api.dashboard.value.sessionId ?: SessionId("demo-session")
            val (token, result) = api.simulateTurnbackBorrow(session)
            setText("token=${token.value}\nresult=$result\n\n" + previewText())
        }
    }

    override fun render() {
        setText(previewText())
    }

    private fun previewText(): String = buildString {
        appendLine("Eligible (weight DESC):")
        api.previewTurnback().forEachIndexed { index, ad ->
            appendLine(
                "[$index] w=${ad.sourceWeight} ${ad.objectId.value} " +
                    "${ad.sourceConfigKey.value} ${ad.sourceType}",
            )
        }
    }
}

class RefillQueueInspectorFragment : BaseDebugInspectorFragment() {
    override fun setupActions(container: LinearLayout) {
        addAction(container, getString(R.string.debug_refresh)) { render() }
    }

    override fun observe() {
        collectWhileStarted(api.dashboard) { render() }
    }

    override fun render() {
        val refill = api.refillSnapshot()
        val deficit = api.deficitSnapshot()
        setText(
            buildString {
                appendLine("closed=${refill.closed}")
                appendLine("activeJobs=${refill.activeJobSlots.map { "${it.configKey.value}/${it.screenInstanceId?.value}" }}")
                appendLine()
                deficit.slots.forEach { slot ->
                    appendLine(
                        "${slot.slot.configKey.value}/${slot.slot.screenInstanceId?.value} " +
                            "active=${slot.active} target=${slot.targetReadyCount} ready=${slot.readyCount} " +
                            "inFlight=${slot.inFlightCount} deficit=${slot.deficit} gen=${slot.generation}",
                    )
                }
            },
        )
    }
}

class FullscreenLockInspectorFragment : BaseDebugInspectorFragment() {
    override fun observe() {
        collectWhileStarted(api.fullscreenLockSnapshot()) { snap ->
            setText(
                buildString {
                    appendLine("owner=${snap.owner}")
                    appendLine("covered=${snap.coveredOwners}")
                    appendLine("busy=${api.lifecycleInspector().fullscreenLock.owner != null}")
                },
            )
        }
    }

    override fun render() = Unit
}

class LifecycleSimulatorFragment : BaseDebugInspectorFragment() {
    override fun setupActions(container: LinearLayout) {
        addAction(container, "Simulate ad click") {
            api.lifecycle.simulateAdClick()
            render()
        }
        addAction(container, "Background (ad click)") {
            api.lifecycle.simulateAdClickBackground()
            render()
        }
        addAction(container, "Background (home)") {
            api.lifecycle.simulateHomeBackground()
            render()
        }
        addAction(container, "Foreground") {
            api.lifecycle.simulateForeground()
            render()
        }
        addAction(container, "Toggle splash active") {
            val current = api.lifecycle.lifecycleSnapshot.value.splashActive
            api.lifecycle.setSplashActive(!current)
            render()
        }
    }

    override fun observe() {
        collectWhileStarted(api.lifecycle.lifecycleSnapshot) { render() }
    }

    override fun render() {
        val snap = api.lifecycleInspector()
        setText(
            buildString {
                appendLine("session=${snap.lifecycle.sessionId?.value}")
                appendLine("splashActive=${snap.lifecycle.splashActive}")
                appendLine("turnbackPending=${snap.lifecycle.turnbackPending}")
                appendLine("activityValid=${snap.lifecycle.activityValid}")
                appendLine("lastBackground=${snap.lifecycle.lastBackgroundReason}")
                appendLine("suppression=${snap.lifecycle.appOpenSuppression}")
                appendLine("tokens=${snap.tokens.tokens}")
                appendLine("fullscreen=${snap.fullscreenLock}")
            },
        )
    }
}

class NavigationGraphInspectorFragment : BaseDebugInspectorFragment() {
    override fun observe() {
        collectWhileStarted(api.dashboard) { render() }
    }

    override fun render() {
        val nav = api.dashboard.value.navigation
        setText(
            """
            Live navigation:
              activity=${nav.activityName}
              fragment=${nav.fragmentName}
              pager=${nav.pagerIndex}
              label=${nav.screenLabel}

            Static flow:
              SplashActivity
              → LanguageLoadingActivity
              → LanguageActivity
              → LanguageDupActivity
              → ApplyLanguageActivity (~2s)
              → OnboardingActivity Pager1..2
              → OnboardingFull1Activity → Pager3
              → OnboardingFull2Activity → Pager4
              → HomeActivity
            """.trimIndent(),
        )
    }
}

class ViewPagerBoundarySimulatorFragment : BaseDebugInspectorFragment() {
    override fun setupActions(container: LinearLayout) {
        addAction(container, "Request forward") {
            val session = api.onboardingSnapshot()?.sessionId
            if (session == null) {
                setText("No onboarding session. Open Onboarding first.")
            } else {
                val result = api.requestOnboardingForward(session)
                setText("forward=$result\n\n${api.onboardingSnapshot()}")
            }
        }
        addAction(container, "Request backward") {
            val session = api.onboardingSnapshot()?.sessionId
            if (session == null) {
                setText("No onboarding session.")
            } else {
                val result = api.requestOnboardingBackward(session)
                setText("backward=$result\n\n${api.onboardingSnapshot()}")
            }
        }
        addAction(container, getString(R.string.debug_refresh)) { render() }
    }

    override fun observe() {
        collectWhileStarted(api.dashboard) { render() }
    }

    override fun render() {
        setText("onboarding=${api.onboardingSnapshot()}")
    }
}

class FullActivityGestureSimulatorFragment : BaseDebugInspectorFragment() {
    override fun setupActions(container: LinearLayout) {
        addAction(container, "Simulate SWIPE_FORWARD") {
            runExit(FullExitSource.SWIPE_FORWARD)
        }
        addAction(container, "Simulate CLOSE_X") {
            runExit(FullExitSource.CLOSE_X)
        }
        addAction(container, "Simulate AUTO_SKIP") {
            runExit(FullExitSource.AUTO_SKIP)
        }
        addAction(container, "Race SWIPE then X") {
            val snap = api.onboardingFullSnapshot()
            val session = snap?.fullSessionId
            if (session == null) {
                setText("No active Full session.")
            } else {
                val first = api.simulateFullSwipe(session)
                val second = api.simulateFullCloseX(session)
                setText("race swipe=$first x=$second\nsnapshot=${api.onboardingFullSnapshot()}")
            }
        }
    }

    override fun render() {
        val snap = api.onboardingFullSnapshot()
        setText(
            buildString {
                appendLine("fullSnapshot=$snap")
                appendLine()
                appendLine("Gesture thresholds (demo defaults):")
                appendLine("  distanceThreshold ~= 4 * touchSlop")
                appendLine("  velocityThreshold ~= minFlingVelocity")
                appendLine("  excluded: CTA / media / close X / clickable assets")
                appendLine("  winningExit=${snap?.winningExitSource}")
                appendLine("  closeVisible=${snap?.closeVisible}")
            },
        )
    }

    private fun runExit(source: FullExitSource) {
        val session = api.onboardingFullSnapshot()?.fullSessionId
        if (session == null) {
            setText("No active Full session. Open Full 1/2 first.")
            return
        }
        val ok = when (source) {
            FullExitSource.SWIPE_FORWARD -> api.simulateFullSwipe(session)
            FullExitSource.CLOSE_X -> api.simulateFullCloseX(session)
            FullExitSource.AUTO_SKIP -> api.simulateFullAutoSkip(session)
        }
        setText("source=$source accepted=$ok\n${api.onboardingFullSnapshot()}")
    }
}

class EventLogFragment : BaseDebugInspectorFragment() {
    override fun setupActions(container: LinearLayout) {
        addAction(container, getString(R.string.debug_clear)) {
            api.eventLog.clear()
            render()
        }
    }

    override fun observe() {
        collectWhileStarted(api.eventLog.snapshot) { events ->
            setText(
                buildString {
                    if (events.isEmpty()) appendLine("(empty)")
                    events.asReversed().forEach { event ->
                        appendLine("#${event.id} [${event.category}] ${event.message}")
                        if (event.details.isNotEmpty()) {
                            appendLine("  ${event.details}")
                        }
                    }
                },
            )
        }
    }

    override fun render() = Unit
}
