package com.example.adsmodule.admob

import android.app.Activity
import com.example.adsmodule.sdk.AdPresentationHost

/**
 * Activity host supplied only for a single show call.
 * Adapters must not retain this beyond the show invocation.
 */
public class ActivityPresentationHost(
    public val activity: Activity,
) : AdPresentationHost

public fun AdPresentationHost?.requireActivityOrNull(): Activity? =
    (this as? ActivityPresentationHost)?.activity
