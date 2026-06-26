package com.shaqsid.smart.domain.model

/**
 * A single controllable data point (DP) of a device, modelled by its Tuya schema type.
 * The UI renders a different control per variant, and writes back via the DP id.
 */
sealed interface DeviceControl {
    val dpId: String
    val name: String
    /** True when the DP is writable (schema mode "rw"/"wr"); read-only DPs are shown but disabled. */
    val editable: Boolean

    data class Switch(
        override val dpId: String,
        override val name: String,
        override val editable: Boolean,
        val on: Boolean,
        /** Paired countdown DP id (turns this switch off after a delay), or null if unsupported. */
        val countdownDpId: String? = null,
        /** Remaining countdown seconds for this switch; 0 = no countdown running. */
        val countdownSeconds: Int = 0
    ) : DeviceControl

    data class Numeric(
        override val dpId: String,
        override val name: String,
        override val editable: Boolean,
        val current: Int,
        val min: Int,
        val max: Int,
        val step: Int,
        val scale: Int,
        val unit: String
    ) : DeviceControl {
        /** Raw value divided by 10^scale, for human-readable display. */
        val displayValue: Double get() = current / Math.pow(10.0, scale.toDouble())
    }

    data class Enumeration(
        override val dpId: String,
        override val name: String,
        override val editable: Boolean,
        val current: String,
        val options: List<String>
    ) : DeviceControl

    data class Text(
        override val dpId: String,
        override val name: String,
        override val editable: Boolean,
        val current: String
    ) : DeviceControl
}
