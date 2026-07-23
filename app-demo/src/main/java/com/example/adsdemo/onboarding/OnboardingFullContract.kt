package com.example.adsdemo.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.onboarding.OnboardingFullResult
import com.example.adsmodule.core.onboarding.full.FullExitSource

class OnboardingFullContract(
    private val fullIndex: Int,
) : ActivityResultContract<OnboardingFullContract.Input, OnboardingFullResult?>() {
    data class Input(
        val sessionId: OnboardingSessionId,
        val fullSessionId: FullSessionId,
        val targetLogicalPage: Int?,
    )

    override fun createIntent(context: Context, input: Input): Intent {
        val clazz = when (fullIndex) {
            1 -> OnboardingFull1Activity::class.java
            2 -> OnboardingFull2Activity::class.java
            else -> error("fullIndex must be 1 or 2")
        }
        return Intent(context, clazz)
            .putExtra(EXTRA_SESSION_ID, input.sessionId.value)
            .putExtra(EXTRA_FULL_SESSION_ID, input.fullSessionId.value)
            .putExtra(EXTRA_FULL_INDEX, fullIndex)
            .putExtra(EXTRA_HAS_TARGET, input.targetLogicalPage != null)
            .apply {
                input.targetLogicalPage?.let { putExtra(EXTRA_TARGET_PAGE, it) }
            }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): OnboardingFullResult? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return null
        val fullSessionId = intent.getStringExtra(EXTRA_FULL_SESSION_ID) ?: return null
        val index = intent.getIntExtra(EXTRA_FULL_INDEX, -1)
        if (index != fullIndex) return null
        val target = if (intent.getBooleanExtra(EXTRA_HAS_TARGET, false)) {
            intent.getIntExtra(EXTRA_TARGET_PAGE, -1).takeIf { it in 1..4 }
        } else {
            null
        }
        val exitSource = intent.getStringExtra(EXTRA_EXIT_SOURCE)
            ?.let { runCatching { FullExitSource.valueOf(it) }.getOrNull() }
            ?: FullExitSource.CLOSE_X
        return OnboardingFullResult(
            sessionId = OnboardingSessionId(sessionId),
            fullSessionId = FullSessionId(fullSessionId),
            fullIndex = index,
            targetLogicalPage = target,
            exitSource = exitSource,
        )
    }

    companion object {
        const val EXTRA_SESSION_ID = "onboarding_full_session_id"
        const val EXTRA_FULL_SESSION_ID = "onboarding_full_full_session_id"
        const val EXTRA_FULL_INDEX = "onboarding_full_index"
        const val EXTRA_TARGET_PAGE = "onboarding_full_target_page"
        const val EXTRA_HAS_TARGET = "onboarding_full_has_target"
        const val EXTRA_EXIT_SOURCE = "onboarding_full_exit_source"
    }
}
