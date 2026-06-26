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
Update one timer: `updateTimer(builder, cb)` (set `.timerId(...)`). By-category:
`updateCategoryTimerStatus(taskName, devId, type, TimerUpdateEnum, cb)`.

> Old API (`getTimerManagerInstance()`) is deprecated; new API can still read old timers.

---

## 4. Other modules (reference, not used here)
- **Group Management** (`ThingHomeSdk.getGroupInstance()` / `newGroupInstance(groupId)`):
  create groups of same-product devices, control them together with the same `publishDps`.
- **Multi-Control Linkage**: link DPs across devices (e.g. 2 switches both toggle a light)
  via the multi-control API; device must support it.
- **Zigbee sub-devices**: pair/control sub-devices through a gateway (`getActivator().newGwSubDevActivator`),
  control identically with `publishDps`.

---

## 5. This project's mapping
| Capability | Where |
|---|---|
| Write DP / publishDps | `data/repository/DeviceRepositoryImpl.kt` (`publishDp`, `updateDeviceStatus`) |
| Real-time DP updates | `DeviceRepositoryImpl` `registerDeviceListeners` / `applyDpUpdate` |
| DP labels (parser) | `DeviceRepositoryImpl` `buildDpLabels` (DP parser plugin) |
| Schedules (timers) | `DeviceRepositoryImpl` schedule methods + `domain/model/DeviceSchedule.kt` |
| Rename device | `DeviceDetailViewModel.rename` (top-bar edit) |
| Control + schedule UI | `feature/devicedetail/*` |
