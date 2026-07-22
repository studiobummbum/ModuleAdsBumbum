# Module Ads Example

Greenfield Android project for developing an SDK-neutral ads module in
incremental phases. Phase 01 contains only the build structure, module wiring,
and placeholder UI; it does not contain ads business logic.

## Modules

| Module | Namespace | Responsibility |
| --- | --- | --- |
| `:app-demo` | `com.example.adsdemo` | Demo application and entry point |
| `:ads-core` | `com.example.adsmodule.core` | SDK-neutral orchestration contracts and logic |
| `:ads-sdk-core` | `com.example.adsmodule.sdk` | Interfaces shared by SDK adapters |
| `:ads-sdk-fake` | `com.example.adsmodule.fake` | Deterministic fake adapter for later development |
| `:ads-debug` | `com.example.adsmodule.debug` | Debug-only dashboard UI |

## Dependency graph

```text
app-demo ─┬─> ads-debug ─> ads-core ─> ads-sdk-core
          ├─> ads-core
          └─> ads-sdk-fake ──────────> ads-sdk-core
```

`ads-core` must remain independent of concrete activities, Android views, and
Google Mobile Ads. SDK-specific implementations depend on `ads-sdk-core`, not
the reverse.

## UI and build

- Kotlin with Gradle Kotlin DSL
- XML layouts with ViewBinding
- Light and dark Material themes, with dynamic colors where available
- `minSdk` 23, `targetSdk` 36, and compile SDK Android 16 QPR2 (36.1)

Build on Windows:

```powershell
.\gradlew.bat :ads-core:testDebugUnitTest :app-demo:assembleDebug
```