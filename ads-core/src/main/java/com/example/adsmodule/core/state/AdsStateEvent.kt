package com.example.adsmodule.core.state

/**
 * Intentional state-machine events. Duplicate or out-of-order callbacks map to
 * [AdsStateReducer] rejections rather than conflicting boolean flags.
 */
public sealed class AdsStateEvent {
    public data object Enable : AdsStateEvent()

    public data object StartLoad : AdsStateEvent()

    /** Stay in LOADING while the weighted loader advances to the next list item. */
    public data object ContinueLoading : AdsStateEvent()

    public data object MarkReady : AdsStateEvent()

    public data object Reserve : AdsStateEvent()

    public data object Release : AdsStateEvent()

    public data object MarkShowing : AdsStateEvent()

    public data object Consume : AdsStateEvent()

    public data object Fail : AdsStateEvent()

    public data object Expire : AdsStateEvent()

    public data object ResetToIdle : AdsStateEvent()

    public data object ResetToLoading : AdsStateEvent()
}
