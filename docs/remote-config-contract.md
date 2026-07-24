# Remote Config contract

Date: 2026-07-24 (Phase 18)  
Source of truth: original Remote Config field names; bundled assets under `ads-core/src/main/assets/original-remote-config/`; registry `ConfigKeyRegistry`.

## Hard rules

- Each placement has its **own** Remote Config key.
- Each ads config has **exactly one** `list_ads`.
- Do **not** rename `list_ads` → `candidates`.
- Do **not** invent `candidate_sets` or alternate schemas.
- Only fix invalid JSON (curly quotes, missing commas, empty weight → Number).
- `weight` lives only on `list_ads[].weight` (no placement/list-level weight).
- Runtime: filter `enable_ad=true`, sort weight DESC, tie-break by original index; one in-flight SDK load per config key; success stores that item’s weight.

## Original field names (ads configs)

| Field | Location | Notes |
| --- | --- | --- |
| `enable` | config root | Master switch |
| `isOrganic` | config root | Audience gate (see open decisions) |
| `timeout_total` | config root | Optional total timeout |
| `type_layout` | config root | Native layout hint |
| `collapsible` | config root | Banner |
| `refresh_time` | config root | Banner |
| `interval` | config root | e.g. inter interval |
| `list_ads` | config root | Ordered array |
| `enable_ad` | item | Per-item enable |
| `weight` | item | Integer ≥ 0 |
| `timeout` | item | Per-item timeout |
| `type` | item | `inter` / `appopen` / `native` when mixed |
| `adunit` | item | Ad unit string |

Forbidden root keys (validator): `schema_version`, `candidates`, `candidate_sets`, `ad_unit_id`, root-level `weight`.

## Mixed type

`inter_splash_config_1` (and similar mixed fullscreen keys) may mix `inter`, `appopen`, and `native` in the same `list_ads`. Runtime resolves format via `ConfigKeyRegistry.resolveAdFormat`; do not split into separate configs.

## Config key map

### Ads (`list_ads`)

| Key | Default format | Native item format |
| --- | --- | --- |
| `inter_splash_config_1` | INTERSTITIAL | NATIVE_FULLSCREEN |
| `native_splash_full_config_1` | NATIVE_FULLSCREEN | NATIVE_FULLSCREEN |
| `inter_onboarding_config_1` | INTERSTITIAL | NATIVE_FULLSCREEN |
| `appopen_resume_config_1` | APP_OPEN | NATIVE |
| `native_splash_config_1` | NATIVE | NATIVE |
| `native_language_loading_config_1` | NATIVE | NATIVE |
| `native_language_config_1` | NATIVE | NATIVE |
| `native_language_dup_config_1` | NATIVE | NATIVE |
| `native_onboarding_config_1` | NATIVE | NATIVE |
| `native_onb_full_config_1` | NATIVE_FULLSCREEN | NATIVE_FULLSCREEN |
| `native_onb_full_2_config_1` | NATIVE_FULLSCREEN | NATIVE_FULLSCREEN |
| `banner_ufo_config_1` | BANNER | NATIVE |
| `banner_home_config_1` | BANNER | NATIVE |
| `inter_all_config_1` | INTERSTITIAL | NATIVE_FULLSCREEN |

### Auxiliary (non-`list_ads` ads payloads)

| Key | Kind |
| --- | --- |
| `native_splash_full_config_2` | Full-screen timing (X delay / auto_skip) |
| `native_onb_full_1_config_2` | Full-screen timing |
| `native_onb_full_2_config_2` | Full-screen timing |
| `splash_screen_config` | Splash screen |
| `onboard_ads_config` | Onboarding ads policy |
| `onboard_screen_config` | Onboarding screen / pager enablement |
| `splash_skip_ads` | Splash skip (`enable`, `isOrganic`, `time_skip`) |
| `turnback_ads` | Turnback feature flags |
| `reopen_language` | Boolean |
| `enable_ads_app` | Boolean master |

## StoredAd metadata (required after successful load)

`objectId`, `sourceConfigKey`, `sourceListIndex`, `sourceType`, `sourceAdunit`, `sourceWeight`, `screenInstanceId`, `loadedAt`, `state`, `sdkHandle`.

## Validation

```bash
python .cursor/skills/android-ads-module-engineering/scripts/validate_ads_config.py path/to/json
```

Unit coverage: `OriginalAdsConfigValidatorTest`, `OriginalAdsConfigParserTest`, `ConfigKeyRegistryTest`, `CoreContractsTest`.
