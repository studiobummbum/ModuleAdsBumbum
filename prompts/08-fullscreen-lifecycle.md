# Phase 08 — Fullscreen coordinator và lifecycle

```text
Tạo:
- GlobalFullscreenLock.
- FullscreenShowCoordinator.
- AdsLifecycleCoordinator.
- BackgroundReason.
- AppOpenSuppression.
- ForegroundSessionTracker.

Fullscreen formats:
- Interstitial.
- App Open.
- Native Full Splash.
- Native Full Onboarding.
- Inter Onboarding.

Lock:
- acquire atomic.
- owner metadata.
- release đúng showRequestId.
- release idempotent.
- stale callback không release lock mới.
- không giữ Activity lâu dài.

Lifecycle:
- AD_CLICK.
- USER_BACKGROUND.
- SYSTEM_INTERRUPTION.
- UNKNOWN.

App Open không show khi:
- fullscreen lock bận;
- Splash active;
- click token tồn tại;
- turnback pending;
- Activity invalid.

Tạo Lifecycle Simulator API cho debug.

Test:
- concurrent lock.
- stale release.
- show fail/dismiss.
- click background/foreground.
- App Open suppression.
- user Home không tạo turnback.
- token expire.

Acceptance:
- Lifecycle/fullscreen tests pass.
```
