# Phase 04 — Fake Ads SDK

```text
Triển khai ads-sdk-fake theo interface ads-sdk-core.

Adapters:
- FakeInterstitialAdapter.
- FakeAppOpenAdapter.
- FakeNativeAdapter.
- FakeBannerAdapter.
- FakeNativeFullscreenAdapter.

Scenario per item:
- SUCCESS.
- FAIL.
- NEVER_CALLBACK.
- DELAYED_SUCCESS.
- LATE_CALLBACK.
- DUPLICATE_CALLBACK.
- SHOW_FAIL.
- impressionDelay.
- clickDelay.
- dismissDelay.
- callbackAfterCancel.
- fakeNetworkName.
- fakeRevenueMicros.

FakeLoadedAd:
- objectId.
- config/item metadata.
- createdAt.
- consumed.
- destroyed.
- deterministic events.

Yêu cầu:
- deterministic với FakeClock/TestDispatcher;
- không giữ Activity trong adapter;
- event stream quan sát được;
- reset scenario;
- request counter per adunit/item;
- destroy idempotent.

Test:
- success/fail/delay/never callback.
- cancellation và late callback.
- duplicate callback.
- show/impression/click/dismiss.
- destroy/consume.
- mixed format adapters.

Acceptance:
- Không cần AdMob SDK.
- Tất cả Fake SDK tests pass.
```
