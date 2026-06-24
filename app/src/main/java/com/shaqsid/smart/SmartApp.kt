package com.shaqsid.smart

import android.app.Application
import com.thingclips.smart.home.sdk.ThingHomeSdk

class SmartApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()

        // Initialize Tuya / Thing Smart SDK
        // You will add your appKey and appSecret later via ThingHomeSdk.init(this, "appKey", "appSecret")
        // For now, we just initialize the basic SDK without credentials to prevent crashes or let the user handle it later.
        try {
            ThingHomeSdk.init(this, "PLACEHOLDER_APP_KEY", "PLACEHOLDER_SECRET_KEY")
            // Optionally enable debug mode:
            // ThingHomeSdk.setDebugMode(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
