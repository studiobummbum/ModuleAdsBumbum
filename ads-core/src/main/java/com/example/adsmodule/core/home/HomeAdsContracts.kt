package com.example.adsmodule.core.home

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.normal.NormalScreenBindSession
import com.example.adsmodule.core.normal.NormalScreenSlotState
import com.example.adsmodule.core.storage.StoredAdView

public object HomeAdsKeys {
    public val BANNER_HOME: ConfigKey = ConfigKey("banner_home_config_1")
    public val INTER_ALL: ConfigKey = ConfigKey("inter_all_config_1")
}

public data class HomeAdsSnapshot(
    val screenInstanceId: ScreenInstanceId,
    val banner: NormalScreenSlotState?,
    val bannerSession: NormalScreenBindSession?,
    val lastInterResult: HomeInterShowResult?,
    val intervalBlocked: Boolean,
    val intervalRemainingMillis: Long?,
)

public sealed class HomeInterShowResult {
    public data class Shown(
        val showRequestId: ShowRequestId,
        val objectId: ObjectId,
        val storedAd: StoredAdView,
    ) : HomeInterShowResult()

    public data class Failed(
        val reason: String,
        val showRequestId: ShowRequestId? = null,
    ) : HomeInterShowResult()

    public data class Rejected(
        val reason: String,
    ) : HomeInterShowResult()

    public data class IntervalBlocked(
        val remainingMillis: Long,
        val reason: String,
    ) : HomeInterShowResult()
}
