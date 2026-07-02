package com.shaqsid.smart.domain.model

/**
 * Pan/tilt capability of a camera. Present only when the device exposes the standard
 * `ptz_control` data point; fixed cameras have no PTZ and this is null.
 */
data class PtzControl(
    /** DP id of the enum `ptz_control` data point (accepts a [PtzDirection.dpValue]). */
    val controlDpId: String,
    /** DP id of the boolean `ptz_stop` data point, if the device exposes one. */
    val stopDpId: String?
)

/**
 * The four cardinal pan/tilt directions and their `ptz_control` enum values, per Tuya's
 * standard schema (0=up, 2=right, 4=down, 6=left; the odd values are the diagonals).
 */
enum class PtzDirection(val dpValue: String) {
    UP("0"),
    RIGHT("2"),
    DOWN("4"),
    LEFT("6")
}
