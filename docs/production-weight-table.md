# Production weight table (signed-off)

Date: 2026-07-24  
Source: bundled Remote Config under `ads-core/src/main/assets/original-remote-config/`  
Status: **signed-off** as the canonical demo / host-template weight matrix.

Rules (unchanged):

- Weight only on `list_ads[].weight`
- Runtime: `enable_ad=true` → sort weight DESC → original index tie-break
- Turnback uses stored `sourceWeight`

## Ads configs

| Config key | Item index | enable_ad | weight | type | Format (registry) |
| --- | ---: | --- | ---: | --- | --- |
| `inter_splash_config_1` | 0 | true | 100 | inter | INTERSTITIAL |
| `inter_splash_config_1` | 1 | true | 90 | inter | INTERSTITIAL |
| `native_splash_full_config_1` | 0 | true | 100 | — | NATIVE_FULLSCREEN |
| `native_splash_full_config_1` | 1 | true | 90 | — | NATIVE_FULLSCREEN |
| `inter_onboarding_config_1` | 0 | true | 100 | inter | INTERSTITIAL |
| `inter_onboarding_config_1` | 1 | true | 90 | inter | INTERSTITIAL |
| `appopen_resume_config_1` | 0 | true | 100 | — | APP_OPEN |
| `appopen_resume_config_1` | 1 | true | 90 | — | APP_OPEN |
| `native_splash_config_1` | 0 | true | 100 | — | NATIVE |
| `native_splash_config_1` | 1 | true | 90 | — | NATIVE |
| `native_language_loading_config_1` | 0 | true | 100 | — | NATIVE |
| `native_language_loading_config_1` | 1 | true | 90 | — | NATIVE |
| `native_language_config_1` | 0 | true | 100 | — | NATIVE |
| `native_language_config_1` | 1 | true | 90 | — | NATIVE |
| `native_language_dup_config_1` | 0 | true | 100 | — | NATIVE |
| `native_language_dup_config_1` | 1 | true | 90 | — | NATIVE |
| `native_onboarding_config_1` | 0 | true | 100 | — | NATIVE |
| `native_onboarding_config_1` | 1 | true | 90 | — | NATIVE |
| `native_onb_full_config_1` | 0 | true | 100 | — | NATIVE_FULLSCREEN |
| `native_onb_full_config_1` | 1 | true | 90 | — | NATIVE_FULLSCREEN |
| `native_onb_full_2_config_1` | 0 | true | 100 | — | NATIVE_FULLSCREEN |
| `native_onb_full_2_config_1` | 1 | true | 90 | — | NATIVE_FULLSCREEN |
| `banner_ufo_config_1` | 0 | true | 100 | — | BANNER |
| `banner_ufo_config_1` | 1 | true | 90 | — | BANNER |
| `banner_home_config_1` | 0 | true | 100 | — | BANNER |
| `banner_home_config_1` | 1 | true | 90 | — | BANNER |
| `inter_all_config_1` | 0 | true | 100 | inter | INTERSTITIAL |
| `inter_all_config_1` | 1 | true | 90 | inter | INTERSTITIAL |

Changing production weights = edit Remote Config JSON and re-validate; do not invent placement-level weights.

## Ad units (signed-off demo inventory)

Bundled `adunit` values are **Google official sample / test units** (same set as `AdMobTestAdUnits`):

| Format | Ad unit |
| --- | --- |
| Interstitial | `ca-app-pub-3940256097505524/1033173712` |
| App Open | `ca-app-pub-3940256097505524/9257395921` |
| Native / Native Full | `ca-app-pub-3940256097505524/2247696110` |
| Banner | `ca-app-pub-3940256097505524/6300978111` |
| App ID | `ca-app-pub-3940256097505524~3347511713` |

Host apps replace these with real AdMob (or mediation) **publisher** units in production Remote Config.  
`DemoSdkBackend.AdMob` / `AdMobRuntimeMode.PRODUCTION` uses RC units as-is.  
`DemoSdkBackend.AdMobTest` remaps every request to the Google sample units above (emulator / QA).

Demo app ID (manifest): `ca-app-pub-3940256097505524~3347511713`.
