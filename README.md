# Module Ads Example

Greenfield Android project for an SDK-neutral ads module with an AdMob demo app.

## Modules

| Module | Namespace | Responsibility |
| --- | --- | --- |
| `:app-demo` | `com.example.adsdemo` | Demo application (AdMob only) |
| `:ads-core` | `com.example.adsmodule.core` | SDK-neutral orchestration |
| `:ads-sdk-core` | `com.example.adsmodule.sdk` | Interfaces shared by SDK adapters |
| `:ads-sdk-admob` | `com.example.adsmodule.admob` | Google Mobile Ads adapters |
| `:ads-debug` | `com.example.adsmodule.debug` | Debug-only dashboard UI |

## Dependency graph

```text
app-demo (debug) ─┬─> ads-debug ─> ads-core ─> ads-sdk-core
app-demo (all)   ─┼─> ads-core
                 └─> ads-sdk-admob ─────────> ads-sdk-core (+ GMA)
```

`ads-core` stays independent of concrete activities, Android views, and Google
Mobile Ads. `:ads-debug` is `debugImplementation` only.

## Testing vs production ad units

- **Debug / emulator:** SDK backend **AdMob Test** remaps loads to official
  Google sample ad units (`AdMobRuntimeMode.TEST`).
- **Production:** switch backend to **AdMob** and replace `list_ads[].adunit`
  (and App ID) in Remote Config with **publisher** units. Same adapters and core.

## UI and build

- Kotlin, XML layouts, ViewBinding
- `minSdk` 23, `targetSdk` 36

```powershell
.\gradlew.bat :ads-core:testDebugUnitTest :ads-sdk-admob:testDebugUnitTest :app-demo:testDebugUnitTest :app-demo:assembleDebug
```
