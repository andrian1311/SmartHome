package com.shaqsid.smart.domain.model

/**
 * A scheduled on/off action for a device's switch DP, backed by a Tuya timer.
 *
 * @param time 24-hour "HH:mm".
 * @param loops 7 chars Sun..Sat, each '0'/'1'. "0000000" = run once, "1111111" = daily.
 * @param dpId the switch DP this schedule controls.
 * @param turnOn target value when the schedule fires.
 */
data class DeviceSchedule(
    val id: String,
    val time: String,
    val loops: String,
    val enabled: Boolean,
    val dpId: String,
    val turnOn: Boolean
) {
    companion object {
        const val LOOPS_ONCE = "0000000"
        const val LOOPS_DAILY = "1111111"
    }
}
