# Task 19 — Layout audit & remove Fake Ads SDK

Date: 2026-07-24  
Commit: `04c7b10` (*Implement Task 19 layout audit and remove Fake Ads SDK.*)

## Summary

Demo app no longer ships a Fake Ads product path. Runtime is **AdMob-only**. Emulator/QA uses Google **test ad units**; production later swaps **publisher** `adunit` values in Remote Config without changing core/adapters.

## Architecture changes

| Before | After |
| --- | --- |
| `:ads-sdk-fake` as runtime dependency of `app-demo` | Module **removed** from `settings.gradle.kts` |
| Debug backend: Fake / AdMob Test / AdMob | Debug: **AdMob Test** / **AdMob** only |
| Fake binders + `view_fake_*.xml` placeholders | AdMob binders only (`AdMobAdBinders.kt`); fail/null → clear |
| Dual binder selection by `DemoSdkBackend` | Always AdMob renderers (`AdMobNativeRenderer`, `AdMobBannerBinder`) |

### Modules (current)

```text
app-demo
  ├─ debugImplementation → ads-debug
  ├─ implementation → ads-core
  └─ implementation → ads-sdk-admob → Google Mobile Ads SDK

ads-core (JVM tests only)
  └─ src/test/.../fake  (deterministic stubs for load/weight/turnback/refill)
```

`ads-core` business logic still has **no** Activity / View / GMA dependency.

## AdMob test vs publisher units

| Mode | When | Behavior |
| --- | --- | --- |
| `DemoSdkBackend.AdMobTest` | Debug default / emulator | `AdMobRuntimeMode.TEST` remaps every load to Google sample units (`AdMobTestAdUnits`) |
| `DemoSdkBackend.AdMob` | Release (forced) or debug “RC as-is” | Uses Remote Config `adunit` as-is — put **publisher** units here for go-live |

Legacy SharedPreferences value `"Fake"` is mapped to `AdMobTest`.

### Go-live checklist (host app)

1. Keep adapters/binders/core unchanged.
2. Replace bundled sample `list_ads[].adunit` (+ App ID) with publisher inventory in Remote Config.
3. Run release with `DemoSdkBackend.AdMob` / `AdMobRuntimeMode.PRODUCTION`.

Details: [production-weight-table.md](production-weight-table.md), [debug-user-guide.md](debug-user-guide.md).

## Layout audit (demo UI)

- Screen layouts refreshed for Splash, Language*, Onboarding, Full, Home (XML + drawables / dimens / styles).
- Theme-aware text: `@color/demo_text_primary` (readable in light and night).
- Removed Fake placeholder layouts:
  - `view_fake_banner_ufo.xml`
  - `view_fake_native_inline.xml`
  - `view_fake_native_splash.xml`
  - `view_fake_native_full.xml`

## AdMob init hardening

`AdMobSdkInitializer.awaitInitialized()` gates loads until `MobileAds.initialize` completes, avoiding early `ERROR_CODE_INVALID_REQUEST` races on cold start.

## Tests & docs touched

- Policy tests: no Fake enum; legacy `"Fake"` → `AdMobTest`; release forces `AdMob`.
- JVM orchestration tests keep stubs under `ads-core/src/test/java/com/example/adsmodule/fake/`.
- Updated: `README.md`, [final-architecture.md](final-architecture.md), [debug-user-guide.md](debug-user-guide.md), [release-checklist.md](release-checklist.md), [ads-module-test-report.md](ads-module-test-report.md).

## Verification (Task 19)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :ads-core:testDebugUnitTest :ads-sdk-admob:testDebugUnitTest :app-demo:testDebugUnitTest :app-demo:testReleaseUnitTest :app-demo:assembleDebug
```

Emulator: **Run** `app-demo` (not Build-only) with network; expect AdMob Test creatives when fill succeeds.
