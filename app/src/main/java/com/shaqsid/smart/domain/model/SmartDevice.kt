package com.shaqsid.smart.domain.model

data class SmartDevice(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val isOn: Boolean,
    /** Full set of controllable data points, parsed from the device schema (empty in list contexts). */
    val controls: List<DeviceControl> = emptyList(),
    /** True for IP cameras (IPC) — these open the live-video screen instead of DP control. */
    val isCamera: Boolean = false
)
