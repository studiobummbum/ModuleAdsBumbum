package com.example.adsdemo.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.adsdemo.R
import com.example.adsmodule.core.storage.StoredAdView

data class FakeNativeFullBoundViews(
    val root: View,
    val media: View,
    val cta: View,
    val clickableAssets: List<View>,
)

interface OnboardingFullNativeBinder {
    fun bind(
        container: ViewGroup,
        storedAd: StoredAdView?,
        title: String,
    ): FakeNativeFullBoundViews

    fun clear(container: ViewGroup)
}

class FakeOnboardingFullNativeBinder : OnboardingFullNativeBinder {
    override fun bind(
        container: ViewGroup,
        storedAd: StoredAdView?,
        title: String,
    ): FakeNativeFullBoundViews {
        container.removeAllViews()
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.view_fake_native_full, container, false)
        view.findViewById<TextView>(R.id.fake_native_full_title).text = title
        view.findViewById<TextView>(R.id.fake_native_full_body).text =
            if (storedAd != null) {
                "${storedAd.sourceConfigKey.value} #${storedAd.sourceListIndex} " +
                    "w=${storedAd.sourceWeight}\n${storedAd.sourceAdunit}"
            } else {
                container.context.getString(R.string.onboarding_full_ad_unavailable)
            }
        val cta = view.findViewById<View>(R.id.fake_native_full_cta)
        if (storedAd != null) {
            (cta as? TextView)?.text = storedAd.sourceAdunit
        }
        val media = view.findViewById<View>(R.id.fake_native_full_media)
        container.addView(view)
        return FakeNativeFullBoundViews(
            root = view,
            media = media,
            cta = cta,
            clickableAssets = listOf(media, cta),
        )
    }

    override fun clear(container: ViewGroup) {
        container.removeAllViews()
    }
}
