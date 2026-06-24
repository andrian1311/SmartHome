package com.shaqsid.smart.util

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Helpers for the Tuya EZ (SmartConfig) pairing flow, which pairs a device onto the
 * phone's currently connected 2.4 GHz Wi-Fi network.
 */
object WifiUtils {

    /**
     * Returns the SSID of the Wi-Fi the phone is currently connected to, or an empty string
     * if it can't be determined. On Android 8.1+ this requires location permission to be
     * granted and location services enabled, otherwise the system returns "<unknown ssid>".
     */
    fun getCurrentSsid(context: Context): String {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return ""

        @Suppress("DEPRECATION")
        val rawSsid = wifiManager.connectionInfo?.ssid ?: return ""

        val ssid = rawSsid.removePrefix("\"").removeSuffix("\"")
        return if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID || ssid == "<unknown ssid>") {
            ""
        } else {
            ssid
        }
    }
}
