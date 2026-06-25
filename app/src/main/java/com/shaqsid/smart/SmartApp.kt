package com.shaqsid.smart

import android.app.Application
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk

class SmartApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        installTuyaTrialCrashGuard()

        // Initialize the Tuya / Thing Smart SDK. The appKey/appSecret are also declared as
        // <meta-data> in AndroidManifest.xml because some SDK components read them from there.
        try {
            ThingHomeSdk.init(this, "***REMOVED***", "***REMOVED***")
            // ThingHomeSdk.setDebugMode(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * The TRIAL Tuya security image runs a background "payment prompt" check
     * (com.thingclips.security.prompt.SecurityPromptBusiness) that throws an NPE while
     * building its request on a fresh install. It is non-essential and unrelated to app
     * logic, so swallow uncaught exceptions originating from that package and let the app
     * continue; every other crash is delegated to the original handler unchanged.
     *
     * Remove this once a non-trial (official) security image is used.
     */
    private fun installTuyaTrialCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val fromTrialPrompt = generateSequence(throwable as Throwable?) { it.cause }
                .any { t -> t.stackTrace.any { it.className.startsWith("com.thingclips.security.prompt") } }
            if (fromTrialPrompt) {
                Log.w("SmartApp", "Ignored Tuya trial security-prompt crash on '${thread.name}'", throwable)
            } else {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }
}
