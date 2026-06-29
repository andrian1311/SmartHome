# CLAUDE.md

Android app to add / control / edit / remove Tuya (ThingClips) smart devices.

## Stack & architecture
- Jetpack Compose UI, **Clean Architecture + MVVM**: `domain` (models, repos, use cases) ←
  `data` (repo impls wrapping the Tuya SDK) → `feature` (Compose screens + ViewModels).
- Manual DI via `AppContainer` (`app/.../AppContainer.kt`); ViewModels built with inline factories.
- Package root: `com.shaqsid.smart`.

## Tuya / Thing Smart SDK — READ FIRST
**Before any Tuya/ThingClips work, read [`docs/tuya-android-integration.md`](docs/tuya-android-integration.md).**
It has verified 7.5.7 API signatures, build/security-image setup, pairing/control patterns,
a crash symptom→cause table, and the project file map.

Critical, non-obvious facts:
- SDK = `com.thingclips.smart:thingsmart:7.5.7` (commercial maven repo). **Stay on 7.x** —
  5.x native libs are 4 KB-aligned and crash on **16 KB-page** devices (API 35 `…ps16k` emulators).
- `security-algorithm.aar` in `app/libs/` is **account-specific** (encrypted for appKey +
  appSecret + packageName + SHA-256), **gitignored**, and must be re-downloaded + **clean
  rebuilt** whenever any of those change.
- After dependency changes, re-verify **login still works** and **16 KB alignment** — the
  UI BizBundle was removed because it broke both (see doc §10/§11).
- `IThingHomeStatusListener` is device add/remove only; DP value changes need a per-device
  `IDevListener` (already wired in `DeviceRepositoryImpl`).
- IP cameras use the separate **IPC SDK** (`thingsmart-ipcsdk`), not `publishDps` — see
  [`docs/tuya-device-control.md`](docs/tuya-device-control.md) §4. It pulls Fresco 3.1.x
  (4 KB libs); we force Fresco ≥ 3.6.0 to keep 16 KB compatibility.

## Build / run
- `./gradlew :app:assembleDebug` — APK at `app/build/intermediates/apk/debug/app-debug.apk`
  (debug variant is `testOnly`; install with `adb install -r -d -t <apk>`).
- For 16 KB testing use the API 35 `…ps16k` emulator; for a 4 KB fallback use API ≤ 34.
- Verify .so alignment with `llvm-readelf -l <lib>.so` (want `0x4000` for 16 KB).

## Conventions
- Country pickers pass the **dial code** to the SDK (`util/Countries.kt`); UI shows country names.
- Wrap Tuya SDK calls that can throw synchronously (init failures) — see `AuthRepositoryImpl.guardSdkCall`.
- Tuya verbose logging is gated: `ThingHomeSdk.setDebugMode(BuildConfig.DEBUG)` in `SmartApp.kt`
  (requires `buildConfig = true` in `buildFeatures`).
