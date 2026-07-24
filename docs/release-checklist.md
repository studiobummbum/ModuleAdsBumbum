# Release checklist

Date: 2026-07-24 (Phase 18)  
Scope: audit / fix only — no new product features in this phase.

## Audit items

| # | Check | Expected | Status (Phase 18 static) |
| --- | --- | --- | --- |
| 1 | Dependency direction | `ads-core` → `ads-sdk-core` only; no Activity/View/GMA in core | PASS |
| 2 | No Compose | No Compose deps/imports | PASS |
| 3 | No alternate RC schema | No `candidates` / `candidate_sets` | PASS |
| 4 | Weight only on item | `list_ads[].weight` only | PASS |
| 5 | One in-flight / list | Mutex per `ConfigKey` in `WeightedListLoader` | PASS |
| 6 | StoredAd source metadata | All required fields present | PASS |
| 7 | Normal screen đúng config | Slot = configKey + screenInstanceId | PASS |
| 8 | Turnback highest weight | Native/Banner READY, weight DESC | PASS |
| 9 | Whole-list refill | Refill source `list_ads`; dedupe by slot | PASS |
| 10 | No Native reuse | Per-screen instance; consume / no illegal reuse | PASS |
| 11 | Fullscreen lock | `GlobalFullscreenLock` | PASS |
| 12 | Splash skip from show success | Timer on `FullscreenShowEvent.Shown` | PASS |
| 13 | Exact Activity flow | Splash → … → Onboarding → Home | PASS |
| 14 | One OnboardingActivity + 4 fragments | ViewPager2 | PASS |
| 15 | Full 1/2 targets | Full1→Pager3, Full2→Pager4 | PASS |
| 16 | Swipe/X/auto-skip first-wins | `finishAndContinueOnce` / CAS | PASS |
| 17 | CTA/media gesture exclusion | `FullGestureDetector` excluded views | PASS |
| 18 | Stale callback cleanup | Gates / deactivate / ignore stale | PASS |
| 19 | Debug excluded from release | `debugImplementation` + release unit tests | PASS |
| 20 | Tests / lint / build | Commands below must PASS | **PASS** (2026-07-24) |

Phase 18 gate results: unit tests (incl. `:ads-sdk-admob`) PASS; `lintDebug` PASS; `assembleDebug` / `assembleRelease` PASS; `connectedDebugAndroidTest` 24/24 PASS. Config validator: 0 errors, 28 warnings (placeholder adunits).

## Gradle gates

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :ads-core:testDebugUnitTest :ads-sdk-core:testDebugUnitTest :ads-sdk-admob:testDebugUnitTest :app-demo:testDebugUnitTest :app-demo:testReleaseUnitTest
.\gradlew.bat :app-demo:lintDebug :app-demo:assembleDebug :app-demo:assembleRelease
.\gradlew.bat :app-demo:connectedDebugAndroidTest
```

Optional config validation:

```powershell
python .cursor/skills/android-ads-module-engineering/scripts/validate_ads_config.py ads-core/src/main/assets/original-remote-config
```

## Before shipping a host app

Open decisions are closed — see [open-decisions.md](open-decisions.md) and [production-weight-table.md](production-weight-table.md).

- [ ] All Gradle gates green
- [ ] Replace Google sample `adunit` values with publisher inventory in production Remote Config
- [ ] Confirm AdMob app ID / account outside this demo
- [ ] Confirm audience injection (`PAID` / `ORGANIC` / `UNKNOWN`) from attribution
- [ ] Host release signing configured
- [ ] Wire real analytics remote adapter if required

**Do not claim production-ready while any Gradle gate fails.**

## Task 19 follow-up (2026-07-24)

- Fake Ads SDK removed from runtime; demo is AdMob-only.
- Emulator QA: AdMob Test (Google sample units). Production: publisher units in Remote Config.
- Full change log: [task-19-admob-only-layout-audit.md](task-19-admob-only-layout-audit.md).
