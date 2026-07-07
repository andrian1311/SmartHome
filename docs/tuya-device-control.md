# Tuya Device Control — Reference

Distilled from the official PDFs (Device Control, Device DP Parser, Scheduled Tasks,
Group Management, Multi-Control Linkage, Zigbee Sub-Device) and verified against the
**7.5.7** AARs. Companion to [`tuya-android-integration.md`](tuya-android-integration.md).

---

## 1. Device control — `IThingDevice.publishDps`

`val device = ThingHomeSdk.newDeviceInstance(devId)`

Command format is a JSON string `{ "<dpId>": <dpValue> }`; multiple DPs per request OK.
**Types must match the DP schema** or you get error `11001`:
- `bool` → `{"1": true}` · `value` → `{"104": 25}` (int, not `"25"`) · `enum` → `{"103": "2"}`
- `string` → `{"102": "ff5500"}` · `raw` → even-length hex string `{"105": "0110"}`
- Read-only (`mode = "ro"`) DPs cannot be sent.

Build values safely with `JSONObject().put(dpId, value)` (correct JSON type per value).

```kotlin
device.publishDps("{\"1\": true}", object : IResultCallback {
    override fun onSuccess() {}
    override fun onError(code: String?, error: String?) {}   // 11001 = wrong format / read-only / raw
})
```

Channels (optional overloads): `publishDps(dps, ThingDevicePublishModeEnum.{ThingDevicePublishModeLocal|Internet|Auto}, cb)`.
Default `publishDps(dps, cb)` auto-selects (LAN if available, else cloud). MQTT status:
`ThingHomeSdk.getServerInstance().isServerConnect()`. Low-power devices: cache DPs via
`ThingHomeSdk.getRequestInstance().sendCacheDps(devId, dps, validitySec, dpCacheType, cb)`.

> **Important:** control is **not** done when `onSuccess` fires — only when
> `IDevListener.onDpUpdate` reports the new value. Always drive UI from `onDpUpdate`
> (see integration doc §7). This project does this in `DeviceRepositoryImpl`.

---

## 2. DP Parser — human-readable names/values  (`IAppDpParserPlugin`)

Turns raw DPs into labelled, typed, display-ready controls. Obtained via the core
plugin manager (no BizBundle needed):

```kotlin
import com.thingclips.sdk.core.PluginManager
import com.thingclips.smart.interior.api.IAppDpParserPlugin

val plugin = PluginManager.service(IAppDpParserPlugin::class.java)
val parser = plugin.update(deviceBean)          // call when device list returns or DPs change
// plugin.getParser(devId) -> cached IDeviceDpParser ; plugin.remove(devId) to clear
```

`IDeviceDpParser`:
- `getDisplayDp(): List<IDpParser<Any>>` — read-only/display DPs
- `getOperableDp(): List<IDpParser<Any>>` — controllable DPs
- `getAllDp(): List<IDpParser<Any>>` — all (limited methods)
- `getSwitchDp(): ISwitch?` — the product's quick toggle

`IDpParser<T>` (the useful bits):
- `getDpId(): String`
- **`getDisplayTitle(): String`** — the human-readable DP name (e.g. "Switch 1", "Countdown") ← what we want for labels
- `getDisplayStatus(): String` — formatted current value (e.g. "25°C", "On")
- `getType(): String` — `bool` / `enum` / `value` / `string`
- `getValue(): T` · `getCommands(status): String` (command JSON for a target value)

Typed subinterfaces: `IBoolDp.getDisplayMessageForStatus(bool)`, `IEnumDp.getRange()/getNames()`,
`INumDp.getMin/Max/Step/Scale/Unit()`, `IStringDp`, `ILightDp` (HSV color).

> The parser reads from **product information**; if the device's product schema isn't
> cached it can be empty — fall back to `SchemaBean.name`, then `"DP <id>"`.
> Project usage: `DeviceRepositoryImpl.buildDpLabels()` builds `dpId -> displayTitle`.

### Per-DP custom names (renaming a switch/gang)
The user's own name for an individual DP (e.g. one gang of a multi-switch) is stored server-side,
**not** in the parser/schema:
- **Read:** `DeviceBean.getDpName(): Map<String, String>` (`dpId -> custom name`). Prefer it over the
  parser label so a renamed "Switch 1" shows the real name. See `DeviceRepositoryImpl.parseControls`.
- **Write:** there is **no typed SDK method** for this. `IThingDevice.saveDeviceProperty` looks right
  (it returns `onSuccess`) but does **not** touch `dpName` — verified by logcat: the name never
  changed. The device panel BizBundle renames a DP via the **raw mobile API**, which this project
  calls directly through the generic gateway:
  ```kotlin
  ThingHomeSdk.getRequestInstance().requestWithApiName(
      "s.m.dev.dp.name.update", "1.0",
      hashMapOf("gwId" to gwId, "devId" to devId, "dpId" to dpId, "name" to newName),
      object : IRequestCallback { /* onSuccess(Any?) / onFailure(code, msg) */ })
  ```
  `gwId` = the gateway/`parentId` for a sub-device, else the `devId` itself. apiName + param names
  come from Tuya's open-source `@tuya/tuya-panel-api` (`common/device.js` `updateDpName`). After
  success, re-fetch home detail so the updated `dpName` propagates. See
  `DeviceRepositoryImpl.renameControl`. (Discovery path: the base SDK ships only
  `thing.m.device.name.update` for whole-device rename; per-DP rename lives in the panel we removed.)

---

## 3. Scheduled tasks (timers) — `ThingHomeSdk.getTimerInstance()` → `IThingCommonTimer`

Use the **new** API (`getTimerInstance()`, SDK ≥ 3.18). A *task* = a named timer group;
a task holds multiple *timers*. Max 30 timers per device. Verified classes:
- builder `com.thingclips.smart.android.device.builder.ThingTimerBuilder`
- `com.thingclips.smart.android.device.enums.TimerDeviceTypeEnum` = `DEVICE` / `GROUP`
- `com.thingclips.smart.home.sdk.constant.TimerUpdateEnum` = `OPEN` / `CLOSE` / `DELETE`
- beans `com.thingclips.smart.sdk.bean.{TimerTask, Timer}`

### Add a timer
```kotlin
// actions: {"dps":{"<dpId>":<value>}, "time":"HH:mm"}  (24h)
val actions = "{\"dps\":{\"1\":true},\"time\":\"18:00\"}"
val builder = ThingTimerBuilder.Builder()
    .taskName(taskName)                 // group name; "" / a fixed name is fine
    .devId(devId)
    .deviceType(TimerDeviceTypeEnum.DEVICE)
    .actions(actions)
    .loops("0000000")                   // Sun..Sat; each digit 0/1. 0000000 = once, 1111111 = daily
    .aliasName("Evening on")
    .status(1)                          // 1 enabled, 0 disabled
    .appPush(false)
    .build()
ThingHomeSdk.getTimerInstance().addTimer(builder, object : IResultCallback { /* ... */ })
```

### Query
```kotlin
ThingHomeSdk.getTimerInstance().getAllTimerList(devId, TimerDeviceTypeEnum.DEVICE,
    object : IThingDataCallback<List<TimerTask>> {
        override fun onSuccess(tasks: List<TimerTask>) { /* task.getTimerList(): List<Timer> */ }
        override fun onError(code: String?, msg: String?) {}
    })
```
`Timer`: `getTimerId(): String`, `getTime(): String` (HH:mm), `getLoops(): String`,
`isOpen(): Boolean`, `getStatus(): Int`. (Use `getTimerList(taskName, devId, type, cb)` for one group.)

### Enable / disable / delete (bulk by timer id)
```kotlin
ThingHomeSdk.getTimerInstance().updateTimerStatus(
    devId, TimerDeviceTypeEnum.DEVICE, listOf(timerId),
    TimerUpdateEnum.DELETE /* or OPEN / CLOSE */, callback)
```
### Edit an existing timer
Reuse the **same `ThingTimerBuilder`** as add (time/loops/actions/status/…) plus the existing
timer id, then call `updateTimer`:
```kotlin
val builder = /* same builder as addTimer */ .timerId(timerId /* Long */).build()
ThingHomeSdk.getTimerInstance().updateTimer(builder, object : IResultCallback { /* ... */ })
```
- `getTimerInstance()` returns **`IThingCommonTimer`** (has `addTimer`/`updateTimer`/`getAllTimerList`/
  `updateTimerStatus`) — *not* the deprecated `IThingTimer`.
- ⚠️ `ThingTimerBuilder.Builder.timerId(long)` takes a **Long**, but `Timer.getTimerId()` returns a
  **String** — convert (`scheduleId.toLong()`) or `updateTimer` silently targets the wrong/no timer.

By-category: `updateCategoryTimerStatus(taskName, devId, type, TimerUpdateEnum, cb)`.

> Old API (`getTimerManagerInstance()`) is deprecated; new API can still read old timers.

---

## 4. IP cameras (IPC) — live preview

IP cameras are **not** controlled via `publishDps`; they use the separate **IPC SDK**
(`com.thingclips.smart:thingsmart-ipcsdk`) for live video over a P2P channel. Verified
against **7.5.4** AARs (`thingsmart-ipc-camera-sdk-api`, `thingsmart-ipc-camera-sdk`).

### ⚠️ 16 KB gotcha (already handled)
The IPC SDK pulls **Fresco 3.1.3**, whose native libs (`libimagepipeline.so`,
`libnative-filters.so`, `libnative-imagetranscoder.so`) are **4 KB-aligned** and crash
on 16 KB-page devices. Fixed by forcing Fresco ≥ 3.6.0 in `app/build.gradle.kts`:
```kotlin
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.facebook.fresco") useVersion("3.6.0")
    }
}
```
Always re-run the 16 KB alignment check after touching IPC/Fresco deps.

**Side effect — must init Fresco yourself.** Forcing Fresco ≥ 3.6.0 disables the IPC SDK's
bundled auto-init, so `ThingCameraView` crashes on first `createVideoView` with
`NullPointerException: SimpleDraweeView was not initialized!`. Fix: call `Fresco.initialize()`
once at startup (guarded) — see `SmartApp.onCreate`:
```kotlin
if (!Fresco.hasBeenInitialized()) Fresco.initialize(this)
```

### Detect a camera
```kotlin
ThingIPCSdk.getCameraInstance()?.isIPCDevice(devId) == true   // route to camera screen
ThingIPCSdk.getCameraInstance()?.isLowPowerDevice(devId)      // wake via getDoorbell().wirelessWake(devId)
```

### Live-preview lifecycle (`IThingSmartCameraP2P`)
```kotlin
val p2p = ThingIPCSdk.getCameraInstance()?.createCameraP2P(devId)
// Bind the surface created by ThingCameraView (widget):
p2p.generateCameraView(view)                       // view = ThingCameraView.createdView()
// On resume:
p2p.registerP2PCameraListener(absP2pCameraListener)
p2p.connect(devId, opCallback)                     // then on success:
p2p.startPreview(opCallback)
p2p.setMute(ICameraP2P.PLAYMODE.LIVE, ICameraP2P.MUTE /* or UNMUTE */, opCallback)
// On pause:
p2p.stopPreview(opCallback); p2p.removeOnP2PCameraListener(); p2p.disconnect(opCallback)
// On destroy:
p2p.destroyP2P()
```
- `OperationDelegateCallBack`: `onSuccess(sessionId, requestId, data)` / `onFailure(sessionId, requestId, errCode)`.
- `AbsP2pCameraListener.onSessionStatusChanged(..., status)`: **-3** = timeout, **-105** = auth
  failure → surface as error / retry.
- `ThingCameraView` (widget, `...camera.middleware.widget`): `createVideoView(devId)`,
  `setCameraViewCallback(AbsVideoViewCallback{ onCreated(view) })`, `createdView()`,
  `onResume()` / `onPause()`. `PLAYMODE` enum is `LIVE` / `PLAYBACK` (no "PREVIEW").
- In Compose, embed `ThingCameraView` with `AndroidView` and drive the above from a
  `LifecycleEventObserver` (resume→connect+preview, pause→stop, dispose→destroy).

### Pan/tilt (PTZ) — via **standard DPs**, not the P2P channel
PTZ cameras that can rotate expose two **normal data points** (control them with the same
`publishDps` used for switches — *not* a P2P/motor API):
- **`ptz_control`** — enum, the movement direction. Send the enum **string** value.
- **`ptz_stop`** — boolean, send `true` to halt an in-progress move.

Standard `ptz_control` enum → direction mapping (8-way; the odd values are diagonals):

| value | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|-------|----|----------|-----|----------|----|----------|-----|----------|
| dir   | up | up-right | right | down-right | down | down-left | left | up-left |

- **Detect support ("if the camera can move"):** look up the standard **code** (not a hardcoded
  DP id) in the schema — `ptz_control` present ⇒ PTZ, absent ⇒ fixed camera. DP ids vary by
  product (e.g. this project's test cam uses `119`=`ptz_control`, `116`=`ptz_stop`); resolve the
  code→id via the DP parser (`buildDpMeta`). See `DeviceRepositoryImpl.detectPtz`.
- **UX:** press-and-hold — publish the direction on press, `ptz_stop=true` on release
  (`feature/camera/CameraScreen.kt` `PtzControls`/`PtzButton`, driven via
  `DeviceDetailViewModel.ptzMove`/`ptzStop`).

> Not implemented yet (available in the SDK): two-way talk (needs `RECORD_AUDIO`),
> snapshot, recording, playback, cloud storage. (Basic pan/tilt PTZ **is** implemented — above.)

---

## 5. Other modules (reference, not used here)
- **Group Management** (`ThingHomeSdk.getGroupInstance()` / `newGroupInstance(groupId)`):
  create groups of same-product devices, control them together with the same `publishDps`.
- **Multi-Control Linkage**: link DPs across devices (e.g. 2 switches both toggle a light)
  via the multi-control API; device must support it.
- **Zigbee sub-devices**: pair/control sub-devices through a gateway (`getActivator().newGwSubDevActivator`),
  control identically with `publishDps`.

---

## 6. This project's mapping
| Capability | Where |
|---|---|
| Write DP / publishDps | `data/repository/DeviceRepositoryImpl.kt` (`publishDp`, `updateDeviceStatus`) |
| Real-time DP updates | `DeviceRepositoryImpl` `registerDeviceListeners` / `applyDpUpdate` |
| DP labels (parser) | `DeviceRepositoryImpl` `buildDpMeta` (DP parser plugin) |
| Per-switch countdown timers | `DeviceRepositoryImpl` `pairCountdowns` + `feature/devicedetail/SwitchControlCard.kt` |
| Schedules (timers) | `DeviceRepositoryImpl` schedule methods (add/**update**/enable/delete) + `domain/model/DeviceSchedule.kt` |
| Add/edit schedule UI | `feature/devicedetail/ScheduleSection.kt` (`ScheduleDialog`; tap a row to edit) |
| Rename device | `DeviceDetailViewModel.rename` (top-bar edit) |
| Rename a single switch/gang | `DeviceRepositoryImpl.renameControl` (`saveDeviceProperty(dpId, name)`); names read back from `DeviceBean.getDpName()` in `parseControls`; edit icon on `SwitchControlCard` |
| Control + schedule UI | `feature/devicedetail/*` |
| Camera detection | `DeviceRepositoryImpl.toSmartDevice` → `SmartDevice.isCamera` (`isIPCDevice`) |
| Camera live preview | `feature/camera/CameraController.kt` + `CameraScreen.kt` (P2P lifecycle) |
| Camera PTZ (pan/tilt) | `DeviceRepositoryImpl.detectPtz` → `SmartDevice.ptz`; `DeviceDetailViewModel.ptzMove`/`ptzStop`; `CameraScreen.PtzControls` |
| Camera live preview | `feature/camera/CameraController.kt` + `feature/camera/CameraScreen.kt` |
