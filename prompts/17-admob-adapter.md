# Phase 17 — Google Mobile Ads adapters

```text
Chỉ bắt đầu khi Phase 16 pass.

Thêm module:
:ads-sdk-admob

Tạo:
- AdMobInterstitialAdapter.
- AdMobAppOpenAdapter.
- AdMobNativeAdapter.
- AdMobBannerAdapter.
- AdMobNativeFullscreenAdapter/renderer.

Quy tắc:
- implement interface ads-sdk-core;
- không chứa weight/list/storage/turnback logic;
- Activity chỉ truyền tại thời điểm show;
- không giữ Activity singleton;
- NativeAd destroy đúng lifecycle;
- AdView destroy đúng lifecycle;
- callback chuyển thành domain events;
- click callback tạo lifecycle event/token;
- dùng Google test ad units cho demo;
- production ad unit chỉ từ Remote Config;
- không tự động click ad trong test.

SDK selector debug:
- Fake.
- AdMob Test.

Smoke test:
- load/show test ad;
- no production ID;
- core tests không phụ thuộc SDK.

Acceptance:
- Thay adapter không đổi ads-core.
```
