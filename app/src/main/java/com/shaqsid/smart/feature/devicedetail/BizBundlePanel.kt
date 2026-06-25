package com.shaqsid.smart.feature.devicedetail

import android.app.Activity
import android.util.Log
import com.thingclips.smart.api.MicroContext
import com.thingclips.smart.commonbiz.bizbundle.family.api.AbsBizBundleFamilyService
import com.thingclips.smart.panelcaller.api.AbsPanelCallerService

/**
 * Opens Tuya's standard device control panel (the UI BizBundle panel). The panel UI is downloaded
 * from the Tuya cloud per product, so this requires network and a panel published for the device.
 */
object BizBundlePanel {

    private const val TAG = "BizBundlePanel"

    /**
     * Launches the panel for [devId]. [homeId] is set as the current family so the panel has the
     * right home context. Returns false if the BizBundle services aren't available.
     */
    fun openDevicePanel(activity: Activity, homeId: Long, devId: String): Boolean {
        val familyService = MicroContext.findServiceByInterface<AbsBizBundleFamilyService>(
            AbsBizBundleFamilyService::class.java.name
        )
        if (homeId != 0L) {
            familyService?.shiftCurrentFamily(homeId, "")
        }

        val panelService = MicroContext.findServiceByInterface<AbsPanelCallerService>(
            AbsPanelCallerService::class.java.name
        )
        if (panelService == null) {
            Log.e(TAG, "AbsPanelCallerService unavailable; BizBundle not initialized?")
            return false
        }
        // Checks device online/firmware state and shows a tip if it can't open.
        panelService.goPanelWithCheckAndTip(activity, devId)
        return true
    }
}
