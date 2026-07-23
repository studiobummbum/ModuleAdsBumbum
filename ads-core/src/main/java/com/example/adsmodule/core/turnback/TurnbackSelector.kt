package com.example.adsmodule.core.turnback

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.sdk.AdFormat

/**
 * Pure turnback selection over READY inventory.
 *
 * Eligible formats: [AdFormat.NATIVE], [AdFormat.BANNER].
 * Comparator: sourceWeight DESC, loadedAt ASC, then stable identity keys.
 */
public object TurnbackSelector {
    public val comparator: Comparator<StoredAd> =
        compareByDescending<StoredAd> { it.sourceWeight }
            .thenBy { it.loadedAt }
            .thenBy { it.sourceConfigKey.value }
            .thenBy { it.screenInstanceId?.value.orEmpty() }
            .thenBy { it.sourceListIndex }
            .thenBy { it.objectId.value }

    public fun isEligible(storedAd: StoredAd): Boolean =
        storedAd.state == AdSlotState.READY && isEligibleFormat(storedAd.sourceType)

    public fun isEligibleFormat(format: AdFormat): Boolean =
        format == AdFormat.NATIVE || format == AdFormat.BANNER

    public fun select(candidates: Collection<StoredAd>): StoredAd? =
        candidates
            .asSequence()
            .filter(::isEligible)
            .minWithOrNull(comparator)
}
