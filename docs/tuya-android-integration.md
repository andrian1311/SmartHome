# Tuya / Thing Smart — Android Integration Reference

Practical reference for this project (`com.shaqsid.smart`). Focuses on Android only.
APIs below were verified against the **ThingClips SDK 7.5.7** AARs (decompiled) and/or
are used in this project's working code.

- Official docs (JS-rendered, hard to scrape): https://developer.tuya.com/en/docs/app-development/featureoverview?id=Ka69nt97vtsfu
- Official Kotlin sample: https://github.com/tuya/tuya-home-android-sdk-sample-kotlin

> Package names are `com.thingclips.smart.*` (formerly `com.tuya.smart.*`). The SDK
> classes are prefixed `Thing*`. Minimum Android 6.0 (we use minSdk 24).

---

## 1. Build setup (the parts that bite)

### Versions (this project)
- `thingsmart` aggregate SDK: **7.5.7** (commercial maven repo). 5.x ships **4 KB-aligned**
  native libs that **cannot load on 16 KB-page devices** (Android 15+/API35 emulators).
  7.5.x libs are 16 KB-aligned. Stay on 7.x for 16 KB support.

### `gradle/libs.versions.toml` / `app/build.gradle.kts`
```kotlin
implementation(libs.thing.home.sdk)            // com.thingclips.smart:thingsmart:7.5.7
implementation(libs.fastjson)                  // com.alibaba:fastjson:1.1.67.android   (REQUIRED, not bundled)
implementation(libs.okhttp.urlconnection)      // com.squareup.okhttp3:okhttp-urlconnection:3.14.9 (REQUIRED)
implementation(fileTree("libs") { include("*.jar", "*.aar") })  // security-algorithm.aar
```

`android { }` essentials:
```kotlin
defaultConfig {
    ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }  // Tuya ships ARM-only; never package x86 (no security lib there)
}
packaging {
    resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    jniLibs {
        pickFirsts += "lib/*/libc++_shared.so"
        useLegacyPackaging = true   // extractNativeLibs=true: security loader needs .so on disk
    }
}
```

### Repositories (`settings.gradle.kts`)
```kotlin
maven { url = uri("https://maven-other.tuya.com/repository/maven-releases/") }
maven { url = uri("https://maven-other.tuya.com/repository/maven-commercial-releases/") } // 7.x lives here
maven { url = uri("https://maven-other.tuya.com/repository/maven-snapshots/") }
```

### Credentials in `AndroidManifest.xml` (some SDK components read these from meta-data)
```xml
<meta-data android:name="THING_SMART_APPKEY" android:value="<appKey>" />
<meta-data android:name="THING_SMART_SECRET" android:value="<appSecret>" />
```

### ProGuard (release)
```
-keep class com.thingclips.**{*;}
-keep class com.alibaba.fastjson.**{*;}
-keep class okhttp3.** { *; }
-dontwarn com.thingclips.**
```

---

## 2. The security image (`security-algorithm.aar`) — required, account-specific

`libthing_security.so` has a `DT_NEEDED` dependency on `libthing_security_algorithm.so`,
which is **not on Maven**. It ships inside `security-algorithm.aar`, downloaded from the
Tuya IoT Platform and **encrypted for one exact appKey + appSecret + packageName + SHA-256**.

- Place it in `app/libs/` (wired via `fileTree`). It is **gitignored** (account-specific).
- Re-download it whenever the appKey/package/SHA-256 changes, then **clean rebuild**.
- The AGP native-lib merge caches — after adding/replacing the AAR, do a **clean** build
  or the `.so` won't repackage.

### Symptom → cause
| Crash / error | Cause |
|---|---|
| `NoClassDefFoundError: com.alibaba.fastjson.JSON` | fastjson/okhttp deps missing |
| `UnsatisfiedLinkError: SecureNativeApi.doCommandNative` | `extractNativeLibs` false, or `security-algorithm.aar` missing/not packaged (clean build) |
| `dlopen ... empty/missing DT_HASH ... new hash type from the future` | 4 KB-aligned lib on a **16 KB** device → use 7.x + run on 4 KB emu or upgrade |
| `AEADBadTagException: mac check in GCM failed` → `app key/secret/packageName does not match` | security image doesn't match appKey/package/SHA-256 |
| `invalid client, no access` | client validation failed — wrong creds/security image, or polluted deps (see §11) |
| Trial dialog "This application is for testing only…" | normal **trial** security image behavior (free dev edition, ≤100 users) |

Enable `ThingHomeSdk.setDebugMode(true)` to surface the exact mismatch message.

---

## 3. SDK init (Application)
```kotlin
ThingHomeSdk.setDebugMode(BuildConfig.DEBUG)
ThingHomeSdk.init(this, appKey, appSecret)   // main thread, all processes
```
Project file: `app/src/main/java/com/shaqsid/smart/SmartApp.kt`
(also installs a guard that swallows the trial `com.thingclips.security.prompt` NPE).

---

## 4. User / account  (`ThingHomeSdk.getUserInstance()`)

Callbacks: `ILoginCallback`/`IRegisterCallback` → `onSuccess(User)` + `onError(code, error)`;
`IResultCallback` → `onSuccess()` + `onError`; `ILogoutCallback` → `onSuccess()` + `onError`.

```kotlin
val user = ThingHomeSdk.getUserInstance()
user.isLogin                                                         // Boolean
user.loginWithEmail(countryCode, email, password, ILoginCallback)
user.sendVerifyCodeWithUserName(email, "", countryCode, /*type*/1, IResultCallback) // 1 = register code
user.registerAccountWithEmail(countryCode, email, password, code, IRegisterCallback)
user.logout(ILogoutCallback)
```
- `countryCode` is the **dial code** ("1", "62", …), not an ISO code. This project shows a
  country-**name** picker (`util/Countries.kt`) and passes `country.dialCode`.
- SDK calls can throw **synchronously** if init failed → wrap in try/catch (see
  `data/repository/AuthRepositoryImpl.kt`, `guardSdkCall`).

Project files: `data/repository/AuthRepositoryImpl.kt`, `feature/auth/*`.

---

## 5. Home / family  (`ThingHomeSdk.getHomeManagerInstance()`, `newHomeInstance(homeId)`)
```kotlin
ThingHomeSdk.getHomeManagerInstance().queryHomeList(IThingGetHomeListCallback)   // List<HomeBean>
ThingHomeSdk.getHomeManagerInstance().createHome(name, lon, lat, geoName, rooms, IThingHomeResultCallback)

val home = ThingHomeSdk.newHomeInstance(homeId)
home.getHomeDetail(IThingHomeResultCallback)          // HomeBean.deviceList: List<DeviceBean>
home.registerHomeStatusListener(IThingHomeStatusListener)   // onDeviceAdded/Removed/onGroupAdded...
```
- `IThingHomeStatusListener` covers **device add/remove only** — NOT DP value changes (see §7).
- Project caches one home id and a `MutableStateFlow<List<SmartDevice>>` in
  `data/repository/DeviceRepositoryImpl.kt`.

---

## 6. Device data: schema + DPs

A `DeviceBean` exposes:
```kotlin
deviceBean.devId; deviceBean.name; deviceBean.isOnline
deviceBean.dps: Map<String, Any?>                    // current values, keyed by DP id ("1","9",...)
deviceBean.schemaMap: Map<String, SchemaBean>        // DP definitions (may be EMPTY for some devices)
```
`SchemaBean`: `id, code, name, mode ("rw"/"ro"), type, property (JSON)`.
DP types (from `property.type` / schema subtype): `bool`, `value`
(`{min,max,step,scale,unit}`), `enum` (`{range:[...]}`), `string`. Skip `raw/bitmap/struct`.

- **Scale:** a `value` DP's real number = `raw / 10^scale` (e.g. raw 250, scale 1 → 25.0°C).
  Publish the **raw int**, display the scaled value.
- **Empty schema fallback:** some devices (often freshly paired) report no `schemaMap`.
  Infer controls from raw `dps` runtime types (bool→toggle, else→read-only).
  See `parseControls` / `parseControlsFromDps` in `DeviceRepositoryImpl.kt`.

---

## 7. Device control + real-time state  (`ThingHomeSdk.newDeviceInstance(devId)` → `IThingDevice`)

### Write a DP
```kotlin
val device = ThingHomeSdk.newDeviceInstance(devId)
device.publishDps("{\"1\":true}", IResultCallback)   // JSON; use JSONObject().put(dpId, value) for correct types
device.renameDevice(newName, IResultCallback)
device.removeDevice(IResultCallback)
```

### Listen for changes (CRITICAL — without this the UI goes stale)
`publishDps` only sends the command. The UI must update from the **device listener**, which
also catches physical/external changes:
```kotlin
device.registerDevListener(object : IDevListener {
    override fun onDpUpdate(devId: String?, dpStr: String?) { /* dpStr = {"1":true,...} → merge into state */ }
    override fun onStatusChanged(devId: String?, online: Boolean) {}
    override fun onNetworkStatusChanged(devId: String?, status: Boolean) {}
    override fun onRemoved(devId: String?) {}
    override fun onDevInfoUpdate(devId: String?) {}
})
device.unRegisterDevListener(); device.onDestroy()    // on cleanup/logout
```
- **Merge `onDpUpdate` locally** (parse the JSON, update values) — do NOT call `getHomeDetail`
  per update: sockets/meters stream power DPs and would cause a refresh storm.
- Project impl: `registerDeviceListeners` / `applyDpUpdate` / `DeviceControl.withValue` in
  `DeviceRepositoryImpl.kt`.

---

## 8. Device pairing / activation  (`ThingHomeSdk.getActivatorInstance()`)

Get a pairing token first, then build an activator. `IThingSmartActivatorListener`:
`onError(code,msg)`, `onActiveSuccess(DeviceBean)`, `onStep(step,data)`.

### EZ (SmartConfig) and AP (hotspot) — share `ActivatorBuilder`
```kotlin
ThingHomeSdk.getActivatorInstance().getActivatorToken(homeId, object : IThingActivatorGetToken {
  override fun onSuccess(token: String?) {
    val builder = ActivatorBuilder()
        .setContext(context).setSsid(ssid).setPassword(password)
        .setActivatorModel(ActivatorModelEnum.THING_EZ /* or THING_AP */)
        .setTimeOut(100).setToken(token).setListener(listener)
    val act = ThingHomeSdk.getActivatorInstance().newActivator(builder)
    act.start()   // act.stop() on cancel
  }
  override fun onFailure(code: String?, msg: String?) {}
})
```
`ActivatorModelEnum`: `THING_EZ`, `THING_AP`, `THING_QR`, `THING_4G_GATEWAY`.

### QR — separate `ThingQRCodeActivatorBuilder`
```kotlin
val builder = ThingQRCodeActivatorBuilder()
    .setContext(context).setHomeId(homeId).setUuid(qrContent).setTimeOut(100).setListener(listener)
val act = ThingHomeSdk.getActivatorInstance().newQRCodeDevActivator(builder)
act.start()
```

### Required for pairing (manifest + runtime)
- Permissions: `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_FINE/COARSE_LOCATION`.
- EZ/AP need the phone on a **2.4 GHz** network; reading the SSID needs **location permission
  granted + location services on** (Android 8.1+). See `util/WifiUtils.kt`, `feature/adddevice/*`.

> The official sample uses the newer `ThingActivatorCoreKit` / `ThingActivatorDeviceCoreKit`
> (bizbundle activator). The `getActivatorInstance()` home-SDK path above is what this project
> uses and is confirmed working.

---

## 9. Other modules (available, not used here)
Groups, scheduled tasks/timers, OTA firmware update, scenes/automation, mesh & Zigbee
sub-devices, vertical SDKs (Camera/Lock/Sweeper). See sample modules `devicebiz`,
`familybiz`, `scenebiz`, `activator`.

---

## 10. UI BizBundle (the standard cloud control panel) — NOT used; incompatible here
The "standard Tuya device panel" is the **UI BizBundle** (`thingsmart-bizbundle-panel`, BOM
`thingsmart-BizBundlesBom`). Opening a panel:
`MicroContext.findServiceByInterface<AbsPanelCallerService>(...).goPanelWithCheckAndTip(activity, devId)`
after `BizBundleInitializer.init(app, RouteEventListener, ServiceEventListener)`.

**We removed it.** Its transitive deps (ex-jcenter flexbox/exoplayer/etc., pulled via an
aliyun mirror) **broke fresh login with "invalid client, no access"**, and the panels are
heavy + cloud-downloaded. Not worth it while *working login + 16 KB* are hard requirements.
If ever revisited, isolate it and re-test login after every dependency change.

---

## 11. Hard-won gotchas (chronological, all hit in this project)
1. **fastjson/okhttp not bundled** → add them explicitly (§1).
2. **Security native lib won't link** → `useLegacyPackaging=true` + ARM-only ABIs (§1).
3. **`security-algorithm.aar` missing** → download per-account, drop in `app/libs/`, **clean** build (§2).
4. **16 KB page emulators** (API35 `...ps16k`) reject 4 KB libs → use SDK 7.x; for dev you can
   also run a 4 KB emulator (API ≤ 34, or non-`ps16k` images).
5. **Trial security prompt NPE/dialog** → free dev edition behavior; guard the
   `com.thingclips.security.prompt` uncaught exception (see `SmartApp.kt`).
6. **Stale switch UI** → register `IDevListener`; `IThingHomeStatusListener` is add/remove only (§7).
7. **BizBundle broke login + bloated deps** → reverted (§10).

---

## 12. Project file map
| Concern | File |
|---|---|
| SDK init + crash guard | `app/.../SmartApp.kt` |
| DI container | `app/.../AppContainer.kt` |
| Auth (login/register/logout) | `data/repository/AuthRepositoryImpl.kt`, `feature/auth/*` |
| Country picker + detection | `util/Countries.kt`, `feature/auth/CountryDropdown.kt` |
| Home + devices + control + listeners | `data/repository/DeviceRepositoryImpl.kt` |
| Domain models | `domain/model/{SmartDevice,DeviceControl,PairingMode}.kt` |
| Use cases | `domain/usecase/{DeviceUseCases,AuthUseCases}.kt` |
| Device list / detail UI | `feature/devicelist/*`, `feature/devicedetail/*` |
| Add device (EZ/AP/QR) | `feature/adddevice/*`, `util/WifiUtils.kt` |
| Security image (gitignored) | `app/libs/security-algorithm-*.aar` |

Architecture: Clean Architecture + MVVM (domain ← data → feature), Jetpack Compose,
manual DI via `AppContainer`.
