package com.shaqsid.smart.domain.model

/**
 * Wi-Fi pairing strategies supported by the Tuya activator.
 *
 * - [EZ]: SmartConfig broadcast. Fastest, but fails on some routers/phones.
 * - [AP]: Access-Point (hotspot) mode. The phone first joins the device's own
 *   hotspot; more reliable and the usual fallback when EZ fails.
 * - [QR]: The device scans a QR code (or its printed QR is scanned) to bind.
 */
enum class PairingMode {
    EZ,
    AP,
    QR
}
