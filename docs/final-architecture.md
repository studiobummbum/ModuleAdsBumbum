# Final architecture

Date: 2026-07-24 (Phase 18)  
Stack: Kotlin, XML + ViewBinding, Activity / Fragment / ViewPager2, Coroutines + StateFlow / SharedFlow, multi-module Gradle. **No Jetpack Compose.**

## Module graph

```text
app-demo
  ├─ debugImplementation → ads-debug
  ├─ implementation → ads-core
  ├─ implementation → ads-sdk-fake
  └─ implementation → ads-sdk-admob → Google Mobile Ads SDK

ads-debug → ads-core
ads-core  → ads-sdk-core   (API contracts only; no Activity/View/GMA)
ads-sdk-fake  → ads-sdk-core
ads-sdk-admob → ads-sdk-core + play-services-ads
```

### Dependency rules

| Module | May depend on | Must not depend on |
| --- | --- | --- |
| `ads-core` | `ads-sdk-core`, coroutines, kotlinx.serialization | Concrete Activities, Views, GMA |
| `ads-sdk-core` | coroutines | GMA, app UI |
| `ads-sdk-fake` / `ads-sdk-admob` | `ads-sdk-core` (+ GMA for AdMob) | `ads-core` business logic |
| `ads-debug` | `ads-core` | Release classpath of `app-demo` |
| `app-demo` | all of the above (debug UI only via `debugImplementation`) | — |

## Core runtime pieces

| Component | Role |
| --- | --- |
| `OriginalRemoteConfigRepository` | Bundled + in-memory + last-known-good configs |
| `OriginalAdsConfigParser` / `OriginalAdsConfigValidator` | Parse/validate original Remote Config fields |
| `WeightedListLoader` | Filter `enable_ad`, sort weight DESC, one SDK request per `ConfigKey` |
| `AdStorage` | READY inventory with full `StoredAd` source metadata |
| `TurnbackSelector` + `AtomicBorrowService` | Highest-weight Native/Banner turnback; atomic pop |
| `WholeListRefillScheduler` + `RefillDeficitStore` | Refill whole `list_ads` of `sourceConfigKey`; dedupe by slot |
| `GlobalFullscreenLock` | At most one top fullscreen owner; cover/supersede for splash Native Full |
| `NormalScreenAdCoordinator` | Bind/unbind by `configKey` + `screenInstanceId` |
| Flow coordinators | Splash, Language, Onboarding boundary/full, Home, App Open resume |
| `AdsDebugApi` / analytics | Debug snapshots and in-memory analytics (demo) |

## Data flows

### Load

```text
Remote Config key
→ one list_ads
→ enable_ad=true, weight DESC (tie → original index)
→ sequential load (mutex per ConfigKey)
→ first success → StoredAd(source* + screenInstanceId + sdkHandle)
```

### Turnback + refill

```text
Atomic borrow (turnback)
→ keep source metadata
→ enqueue whole-list refill for sourceConfigKey + screenInstanceId
→ new success may be a different list item / weight
```

### Splash stage

```text
SplashActivity
→ load placements
→ show Inter/App Open (or Native primary)
→ on Shown: start splash_skip timer (not on READY/load/impression alone)
→ time_skip OR dismiss (first-wins) → NativeFullSplashActivity over current ad
→ Native Full X / auto_skip → LanguageLoadingActivity once
```

## Screen flow (exact)

```text
SplashActivity
→ LanguageLoadingActivity
→ LanguageActivity
→ LanguageDupActivity
→ ApplyLanguageActivity (~2s locale apply, not ads timeout)
→ OnboardingActivity (ViewPager2, 4 Fragments)
     Pager 1 → Pager 2 → OnboardingFull1Activity → Pager 3
     → OnboardingFull2Activity → Pager 4 → Home
```

Rules:

- One `OnboardingActivity`; four pager Fragments; each pager has its own `screenInstanceId` and Native object.
- Forward from Pager 2 must go through Full 1; from Pager 3 through Full 2.
- Full 1 returns to Pager 3; Full 2 to Pager 4.
- Recreation / turnback must not reset `currentPager` incorrectly.
- Full 1/2 exits: `SWIPE_FORWARD` / `CLOSE_X` / `AUTO_SKIP` → shared `finishAndContinueOnce` (CAS first-wins). Swipe excludes CTA/media/clickable assets.

## Demo wiring note

- Debug: select Fake / AdMob Test / AdMob via debug dashboard (`DemoSdkBackendStore`).
- Release: forced `DemoSdkBackend.AdMob` (RC units as-is; Fake not selectable).
- Bundled sample inventory + weight matrix: [production-weight-table.md](production-weight-table.md).
- Closed policies (System Back, audience, units): [open-decisions.md](open-decisions.md).
