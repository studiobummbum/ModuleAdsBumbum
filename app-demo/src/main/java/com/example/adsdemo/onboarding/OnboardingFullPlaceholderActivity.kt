package com.example.adsdemo.onboarding

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityOnboardingFullPlaceholderBinding

abstract class OnboardingFullPlaceholderActivity : AppCompatActivity() {
    protected abstract val fullIndex: Int
    protected abstract val titleRes: Int

    private lateinit var binding: ActivityOnboardingFullPlaceholderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingFullPlaceholderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.onboardingFullTitle.setText(titleRes)
        binding.onboardingFullContinue.setOnClickListener { finishWithContinue() }
    }

    private fun finishWithContinue() {
        val sessionId = intent.getStringExtra(OnboardingFullContract.EXTRA_SESSION_ID)
        val fullSessionId = intent.getStringExtra(OnboardingFullContract.EXTRA_FULL_SESSION_ID)
        if (sessionId.isNullOrBlank() || fullSessionId.isNullOrBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val hasTarget = intent.getBooleanExtra(OnboardingFullContract.EXTRA_HAS_TARGET, false)
        val result = Intent()
            .putExtra(OnboardingFullContract.EXTRA_SESSION_ID, sessionId)
            .putExtra(OnboardingFullContract.EXTRA_FULL_SESSION_ID, fullSessionId)
            .putExtra(OnboardingFullContract.EXTRA_FULL_INDEX, fullIndex)
            .putExtra(OnboardingFullContract.EXTRA_HAS_TARGET, hasTarget)
        if (hasTarget) {
            result.putExtra(
                OnboardingFullContract.EXTRA_TARGET_PAGE,
                intent.getIntExtra(OnboardingFullContract.EXTRA_TARGET_PAGE, -1),
            )
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
