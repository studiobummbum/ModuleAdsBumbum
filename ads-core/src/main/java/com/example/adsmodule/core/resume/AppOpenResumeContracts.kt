package com.example.adsmodule.core.resume

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.lifecycle.AppOpenSuppressionResult
import com.example.adsmodule.core.storage.StoredAdView

public object AppOpenResumeKeys {
    public val APPOPEN_RESUME: ConfigKey = ConfigKey("appopen_resume_config_1")
}

public sealed class AppOpenResumeResult {
    public data class Shown(
        val showRequestId: ShowRequestId,
        val objectId: ObjectId,
        val storedAd: StoredAdView,
    ) : AppOpenResumeResult()

    public data class Suppressed(
        val suppression: AppOpenSuppressionResult,
    ) : AppOpenResumeResult()

    public data class Skipped(
        val reason: String,
    ) : AppOpenResumeResult()

    public data class Failed(
        val reason: String,
        val showRequestId: ShowRequestId? = null,
    ) : AppOpenResumeResult()

    public data class Rejected(
        val reason: String,
    ) : AppOpenResumeResult()
}
