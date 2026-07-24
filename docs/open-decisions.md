# Closed decisions

Date: 2026-07-24  
Former open decisions from Phase 18 — **closed** with the policies below.

## 1. System Back at Full1 / Full2 — CLOSED

**Policy:** System Back == `CLOSE_X`.

- Honors `time_delay_X_button` (ignored until Close X is ready).
- Uses the same first-wins gate as swipe / X / auto-skip (`finishAndContinueOnce` with `FullExitSource.CLOSE_X`).
- Advances to the pending target pager (Full1 → 3, Full2 → 4).

**Code:** `OnboardingFullCoordinator.onSystemBack` → `onCloseClicked`; `OnboardingFullActivity` OnBackPressedCallback.

## 2. Production weight table — CLOSED

**Policy:** Bundled matrix in [production-weight-table.md](production-weight-table.md) is the signed-off template (typically weight 100 then 90 per list).

Ops may change weights only via Remote Config `list_ads[].weight`.

## 3. Production ad units — CLOSED (demo inventory)

**Policy:**

- Bundled RC `adunit` fields use Google **official sample** units (see weight table doc).
- Debug default backend: `AdMobTest` (remap to samples).
- Release backend: forced `AdMob` (`AdMobRuntimeMode.PRODUCTION` — RC units as-is).
- Fake is **debug-only** (release store ignores Fake).
- Real publisher inventory: replace RC `adunit` + keep `AdMob` backend; never hardcode publisher units in `AdMobTestAdUnits`.

## 4. Organic / Paid / Unknown — CLOSED

**Policy** (`AudienceEligibility`, applies to every placement / skip / turnback / page ads flag):

| Audience | Eligible when |
| --- | --- |
| `PAID` | Always (ignores `isOrganic`) |
| `ORGANIC` | Only if `isOrganic == true` |
| `UNKNOWN` | Never (fail-closed) |

No per-placement overrides in this module. Demo graph defaults to `AudienceType.PAID`.

## Packaging / signing notes (closed for this demo)

| Item | Decision |
| --- | --- |
| Fake on release | Not selectable; runtime forced to AdMob |
| `:ads-debug` | Remains `debugImplementation` only |
| Release signing | Host / store responsibility; demo `assembleRelease` may use default/debug signing for local validation |
| Analytics remote | In-memory + no-op remote remains acceptable for demo; host wires real adapter |

See also [release-checklist.md](release-checklist.md) and [debug-user-guide.md](debug-user-guide.md).
