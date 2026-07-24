# Ads Module — Test Report (Phase 16 + Phase 18 re-verify)

Date: 2026-07-24 (Phase 18 gates)  
Workbook: `docs/Module Ads - Dac ta ky thuat tieng Viet v2.7-full-swipe-close-corrected.xlsx` (sheet **06 Kịch bản kiểm thử**)  
Skill: `.cursor/skills/android-ads-module-engineering/references/07-test.md`

## Summary

| Gate | Result |
| --- | --- |
| Unit tests (`ads-core`, `ads-sdk-core`, `ads-sdk-admob`, `app-demo` debug + release) | **PASS** |
| `:app-demo:lintDebug` | **PASS** |
| `:app-demo:assembleDebug` | **PASS** |
| `:app-demo:assembleRelease` | **PASS** (no custom signingConfig) |
| `:app-demo:connectedDebugAndroidTest` | **PASS** (24 tests on `Pixel_7a(AVD) - 17`) |
| `validate_ads_config.py` on bundled assets | **0 errors**, 28 warnings (placeholder adunits) |

## Harness

| Piece | Location |
| --- | --- |
| Deterministic IDs | `ads-core/.../testsupport/SequentialIdGenerator.kt` |
| Mutable clock | `ads-core/.../testsupport/AdvanceableClock.kt` |
| Deterministic adapters in JVM tests | `ads-core/src/test/.../fake` (+ `FakeSdkTestSupport`) — not a product module |
| JSON fixtures (RC-001, mixed, splash skip) | `ads-core/.../config/TestConfigFixtures.kt` |
| Release unit tests enabled (AGP 9) | `gradle.properties` → `android.onlyEnableUnitTestForTheTestedBuildType=false` |

## Excel matrix → tests

| ID | Coverage | Primary test(s) | Status |
| --- | --- | --- | --- |
| RC-001 | Curly quote / missing comma / valid parse | `OriginalAdsConfigParserTest` (curly/missing comma + fixtures); bundled parse via `BundledConfigDataSourceTest` | PASS |
| RC-002 | Single `list_ads`, no `candidate_sets` | `OriginalAdsConfigValidatorTest.replacementSchemaAndPlacementWeightAreForbidden`; `CoreContractsTest` | PASS |
| RC-003 | Native splash / banner UFO independent | `ConfigKeyRegistryTest`; bundled assets | PASS |
| RC-004 | Original field names retained | `CoreContractsTest.originalConfig_serializesWithOriginalRemoteConfigFields` | PASS |
| RC-005 | Item weight Number | `OriginalAdsConfigValidatorTest.missingStringNullDecimalOverflowAndNegativeWeightsAreErrors` | PASS |
| RC-006..008 | Mixed type adapters | `WeightedListLoaderTest.mixedTypes_*`; `FakeMixedFormatAdaptersTest` | PASS |
| LOAD-001..006 | Weight order, tie-break, one in-flight, fallback, success stop, disabled | `WeightedListLoaderTest` | PASS |
| STORE-001..002 | Source metadata + weight | `AdStorageTest`; `WeightedListLoaderTest.success_preservesSourceMetadataAndDebugState` | PASS |
| TURN-001..002 | Highest weight / stable tie-break | `TurnbackSelectorTest`; `AdStorageTest.atomicBorrowTurnback_*` | PASS |
| Refill (07-test) | Whole list, not exact adunit, new weight, dedupe | `TurnbackRefillIntegrationTest`; `WholeListRefillSchedulerTest`; `RefillDeficitStoreTest` | PASS |
| MIX-003 | Inter+AppOpen fail → Native weight 80 | `WeightedListLoaderTest.mixedTypes_interAndAppOpenFail_nativeSuccessStoresWeight80` | PASS |
| SKIP-001 | READY does not start timer | `SplashFlowCoordinatorTest.readyDoesNotStartSkipTimer` | PASS |
| SKIP-002 | Inter show → `time_skip` → Native Full | `SplashFlowCoordinatorTest.interstitialShownStartsSkipTimerAndOpensNativeFull` | PASS |
| SKIP-003 | App Open show → skip timer | `SplashFlowCoordinatorTest.appOpenShownStartsSkipTimer` | PASS |
| SKIP-005/008 | Dismiss / race first-wins | `SplashFlowCoordinatorTest.timerAndDismissRace_nativeFullOnce` | PASS |
| SKIP-006 | Show fail → no skip timer | `SplashFlowCoordinatorTest.showFailFallsBackWithoutSkipTimer` | PASS |
| SKIP-007 | Skip disabled / organic mismatch | `SplashFlowCoordinatorTest.skipDisabled_*`; `skipOrganicMismatch_*` | PASS |
| Native primary path | `type=native` → Native Full, no skip timer | `SplashFlowCoordinatorTest.nativePrimary_advancesToNativeFullWithoutSkipTimer` | PASS |
| Splash recreation | Reattach keeps session + running timer | `SplashFlowCoordinatorTest.recreationAttach_keepsSessionAndSkipTimer` | PASS |
| Native Full X / auto_skip | Delay, auto after X, race, stale | `NativeFullSplashControllerTest` | PASS |
| Native Full UI | Close top-end, starts invisible; missing session finishes | `NativeFullSplashActivityTest` (androidTest) | PASS |
| FULL-001..010 | Swipe / X / auto-skip / race / gesture / recreation / stale | `OnboardingFullCoordinatorTest`; `FullExitGateTest`; `CloseDelayAndAutoSkipControllerTest`; `OnboardingFullActivityTest` | PASS |
| NAV-001..010 | Language order, pager boundaries, recreation | `LanguageFlowCoordinatorTest`; `OnboardingBoundaryCoordinatorTest`; androidTest Language/Onboarding/Home | PASS |
| Home / App Open | Interval, banner, resume suppressions | `HomeAdsCoordinatorTest`; `AppOpenResumeCoordinatorTest`; `HomeActivityTest` | PASS |
| Stale / duplicate callbacks | Loader, storage, fake SDK, fullscreen lock | `WeightedListLoaderTest.staleSuccess_*`; `AdsStateStoreTest`; `FakeCancellationAndCallbackTest`; `FullscreenShowCoordinatorTest` | PASS |
| Release debug exclusion | Debug classes present on debug; absent on release | `DebugClasspathInclusionTest` (testDebug); `ReleaseDebugExclusionTest` (testRelease) | PASS |
| Fullscreen / lifecycle | Lock, token, suppression | `FullscreenLifecycleIntegrationTest`; `AdsLifecycleCoordinatorTest` | PASS |

## Commands run

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :ads-core:testDebugUnitTest :ads-sdk-core:testDebugUnitTest :ads-sdk-admob:testDebugUnitTest :app-demo:testDebugUnitTest :app-demo:testReleaseUnitTest
.\gradlew.bat :app-demo:lintDebug :app-demo:assembleDebug :app-demo:assembleRelease
.\gradlew.bat :app-demo:connectedDebugAndroidTest
py -3 .cursor/skills/android-ads-module-engineering/scripts/validate_ads_config.py ads-core/src/main/assets/original-remote-config
```

## Notes

- Phase 16 did **not** add product features; only tests, fixtures, harness, AGP release unit-test enablement, and this report.
- Phase 18 re-ran all gates, added release docs under `docs/`, and hardened `OnboardingFragmentLifecycleTest.destroyView_releasesBoundAd` to seed READY inventory (no AdMob network dependency).
- Native Full close-delay / auto-skip **behavior** is asserted in unit tests (`NativeFullSplashControllerTest`). Instrumentation covers layout contract (top-end X, initial invisible) and missing-session finish.
- System Back policy at Full 1/2 remains an open decision (demo intercepts + logs); see [open-decisions.md](open-decisions.md).
- **Not production-ready** while open decisions (weights, ad units, audience, System Back) and demo Fake/AdMob Test wiring remain.
