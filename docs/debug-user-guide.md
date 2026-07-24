# Debug user guide

Date: 2026-07-24 (Phase 18)  
Module: `:ads-debug` — **debug builds only**.

## Availability

- Wired via `debugImplementation(project(":ads-debug"))` in `app-demo`.
- Release classpath must **not** include `AdsDebugDashboardActivity` or demo `DebugNavInstaller` (asserted by `ReleaseDebugExclusionTest`).
- Open the dashboard from the debug navigation entry on a **debuggable** build.

## SDK backend switch

Destination: **SDK Backend (AdMob Test / AdMob)**.

| Backend | Behavior |
| --- | --- |
| AdMob Test | Remaps every load to Google sample / **test** ad units (`AdMobRuntimeMode.TEST`). Default for debug/emulator. |
| AdMob | Uses Remote Config `adunit` as-is (`AdMobRuntimeMode.PRODUCTION`). Put **publisher** units here for production. Release builds force this. |

Preference is stored by `DemoSdkBackendStore`. Changing backend typically requires process restart so `AdsDemoGraph` rebuilds adapters. Legacy stored value `Fake` is treated as AdMob Test.

Bundled demo RC already contains Google sample units — see [production-weight-table.md](production-weight-table.md).

## Dashboard destinations

| Destination | Use |
| --- | --- |
| Dashboard | High-level snapshot (fullscreen lock, stages, etc.) |
| Remote Config Editor | Inspect / override current config snapshot |
| Weighted List Inspector | Original vs runtime weight order and load state |
| Placement Inspector | Per-placement enable, organic, items |
| Storage Inspector | READY inventory + source metadata |
| Turnback Simulator | Trigger / observe turnback borrow |
| Refill Queue Inspector | Deficit slots and whole-list refill |
| Fullscreen Lock Inspector | Owner / covered owners |
| Lifecycle Simulator | Foreground / App Open suppression helpers |
| Navigation Graph Inspector | Screen flow snapshot |
| ViewPager Boundary Simulator | Onboarding pager / Full boundaries |
| Full Activity Gesture Simulator | Swipe / X / auto-skip gates |
| Event Log | Ring-buffer debug events |
| Native Layout Gallery | Native layout variants |
| SDK Backend | AdMob Test vs AdMob (publisher RC units) |

## What to verify in debug

1. Weight order matches `list_ads` after enable filter (DESC + index tie-break).
2. Storage objects show full source metadata (`sourceConfigKey`, `sourceWeight`, `screenInstanceId`, …).
3. After turnback pop, refill reloads the **whole** source list (not hard exact-adunit reload).
4. Splash skip timer starts only after Inter/App Open **show** success.
5. Full 1/2: swipe does not steal CTA/media gestures; first exit wins.
6. System Back on Full uses CLOSE_X policy (after X delay).
7. Emulator fill uses real AdMob test creatives (not placeholder Fake UI).

## Safety

- Do not ship `:ads-debug` on release variants.
- Do not hardcode production `ca-app-pub-…` units into the debug gallery — put publisher units in Remote Config when going live.

## Related docs

- Task 19 remove Fake + layout audit: [task-19-admob-only-layout-audit.md](task-19-admob-only-layout-audit.md)
- Architecture: [final-architecture.md](final-architecture.md)
- Sample / publisher units: [production-weight-table.md](production-weight-table.md)
