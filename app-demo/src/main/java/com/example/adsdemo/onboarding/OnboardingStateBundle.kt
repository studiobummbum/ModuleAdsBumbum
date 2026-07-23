package com.example.adsdemo.onboarding

import android.os.Bundle
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.onboarding.OnboardingNavigationEffect
import com.example.adsmodule.core.onboarding.OnboardingPendingFull
import com.example.adsmodule.core.onboarding.OnboardingSavedState

internal object OnboardingStateBundle {
    private const val KEY_SESSION_ID = "onboarding_session_id"
    private const val KEY_ACTIVE_PAGES = "onboarding_active_pages"
    private const val KEY_CURRENT_PAGE = "onboarding_current_page"
    private const val KEY_PENDING_TARGET = "onboarding_pending_target"
    private const val KEY_HAS_PENDING_TARGET = "onboarding_has_pending_target"
    private const val KEY_FULL1_DONE = "onboarding_full1_done"
    private const val KEY_FULL2_DONE = "onboarding_full2_done"
    private const val KEY_PENDING_FULL_INDEX = "onboarding_pending_full_index"
    private const val KEY_PENDING_FULL_SESSION = "onboarding_pending_full_session"
    private const val KEY_PENDING_FULL_TARGET = "onboarding_pending_full_target"
    private const val KEY_HAS_PENDING_FULL_TARGET = "onboarding_has_pending_full_target"
    private const val KEY_PENDING_EFFECT = "onboarding_pending_effect"
    private const val KEY_CLAIMED_EFFECTS = "onboarding_claimed_effects"
    private const val KEY_LANGUAGE_TAG = "onboarding_language_tag"

    fun write(
        outState: Bundle,
        saved: OnboardingSavedState,
        languageTag: String?,
    ) {
        outState.putString(KEY_SESSION_ID, saved.sessionId.value)
        outState.putIntArray(KEY_ACTIVE_PAGES, saved.activePages.toIntArray())
        outState.putInt(KEY_CURRENT_PAGE, saved.currentLogicalPage)
        outState.putBoolean(KEY_HAS_PENDING_TARGET, saved.pendingTargetLogicalPage != null)
        saved.pendingTargetLogicalPage?.let { outState.putInt(KEY_PENDING_TARGET, it) }
        outState.putBoolean(KEY_FULL1_DONE, saved.full1Completed)
        outState.putBoolean(KEY_FULL2_DONE, saved.full2Completed)
        val pendingFull = saved.pendingFull
        if (pendingFull != null) {
            outState.putInt(KEY_PENDING_FULL_INDEX, pendingFull.fullIndex)
            outState.putString(KEY_PENDING_FULL_SESSION, pendingFull.fullSessionId.value)
            outState.putBoolean(KEY_HAS_PENDING_FULL_TARGET, pendingFull.targetLogicalPage != null)
            pendingFull.targetLogicalPage?.let { outState.putInt(KEY_PENDING_FULL_TARGET, it) }
        } else {
            outState.putInt(KEY_PENDING_FULL_INDEX, -1)
        }
        outState.putString(KEY_PENDING_EFFECT, saved.pendingEffect?.name)
        outState.putStringArray(
            KEY_CLAIMED_EFFECTS,
            saved.claimedEffects.map { it.name }.toTypedArray(),
        )
        languageTag?.let { outState.putString(KEY_LANGUAGE_TAG, it) }
    }

    fun read(bundle: Bundle?): OnboardingSavedState? {
        if (bundle == null) return null
        val sessionId = bundle.getString(KEY_SESSION_ID) ?: return null
        val active = bundle.getIntArray(KEY_ACTIVE_PAGES)?.toList() ?: return null
        if (active.isEmpty()) return null
        val pendingFullIndex = bundle.getInt(KEY_PENDING_FULL_INDEX, -1)
        val pendingFull = if (pendingFullIndex == 1 || pendingFullIndex == 2) {
            val fullSession = bundle.getString(KEY_PENDING_FULL_SESSION) ?: return null
            val target = if (bundle.getBoolean(KEY_HAS_PENDING_FULL_TARGET)) {
                bundle.getInt(KEY_PENDING_FULL_TARGET)
            } else {
                null
            }
            OnboardingPendingFull(
                fullIndex = pendingFullIndex,
                fullSessionId = FullSessionId(fullSession),
                targetLogicalPage = target,
            )
        } else {
            null
        }
        val pendingEffectName = bundle.getString(KEY_PENDING_EFFECT)
        val pendingEffect = pendingEffectName?.let {
            runCatching { OnboardingNavigationEffect.valueOf(it) }.getOrNull()
        }
        val claimed = bundle.getStringArray(KEY_CLAIMED_EFFECTS)
            ?.mapNotNull { runCatching { OnboardingNavigationEffect.valueOf(it) }.getOrNull() }
            ?.toSet()
            .orEmpty()
        val pendingTarget = if (bundle.getBoolean(KEY_HAS_PENDING_TARGET)) {
            bundle.getInt(KEY_PENDING_TARGET)
        } else {
            null
        }
        return OnboardingSavedState(
            sessionId = OnboardingSessionId(sessionId),
            activePages = active,
            currentLogicalPage = bundle.getInt(KEY_CURRENT_PAGE),
            pendingTargetLogicalPage = pendingTarget,
            full1Completed = bundle.getBoolean(KEY_FULL1_DONE),
            full2Completed = bundle.getBoolean(KEY_FULL2_DONE),
            pendingFull = pendingFull,
            pendingEffect = pendingEffect,
            claimedEffects = claimed,
        )
    }

    fun languageTag(bundle: Bundle?): String? = bundle?.getString(KEY_LANGUAGE_TAG)
}
