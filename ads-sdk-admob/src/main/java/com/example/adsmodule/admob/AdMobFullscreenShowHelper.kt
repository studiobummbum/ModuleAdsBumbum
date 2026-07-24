package com.example.adsmodule.admob

import android.app.Activity
import com.example.adsmodule.sdk.AdShowEvent
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

internal object AdMobFullscreenShowHelper {
    fun show(
        showRequestId: String,
        activity: Activity?,
        attachCallback: (FullScreenContentCallback) -> Unit,
        present: (Activity) -> Unit,
    ): Flow<AdShowEvent> = callbackFlow {
        if (activity == null || activity.isFinishing) {
            trySend(AdShowEvent.Fail(showRequestId, "Missing Activity presentation host"))
            close()
            return@callbackFlow
        }

        val terminal = AtomicBoolean(false)
        fun emitTerminal(event: AdShowEvent) {
            if (terminal.compareAndSet(false, true)) {
                trySend(event)
                close()
            }
        }

        attachCallback(
            object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    trySend(AdShowEvent.Shown(showRequestId))
                }

                override fun onAdImpression() {
                    trySend(AdShowEvent.Impression(showRequestId))
                }

                override fun onAdClicked() {
                    trySend(AdShowEvent.Click(showRequestId))
                }

                override fun onAdDismissedFullScreenContent() {
                    emitTerminal(AdShowEvent.Dismiss(showRequestId))
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    emitTerminal(
                        AdShowEvent.Fail(
                            showRequestId = showRequestId,
                            reason = adError.message.ifBlank { "show failed code=${adError.code}" },
                        ),
                    )
                }
            },
        )

        try {
            AdMobMainThread.post {
                try {
                    present(activity)
                } catch (t: Throwable) {
                    emitTerminal(
                        AdShowEvent.Fail(
                            showRequestId = showRequestId,
                            reason = t.message ?: t.javaClass.simpleName,
                        ),
                    )
                }
            }
        } catch (t: Throwable) {
            emitTerminal(
                AdShowEvent.Fail(
                    showRequestId = showRequestId,
                    reason = t.message ?: t.javaClass.simpleName,
                ),
            )
        }

        awaitClose { }
    }
}
