package com.shaqsid.smart.feature.camera

import android.util.Log
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.AbsP2pCameraListener
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationDelegateCallBack
import com.thingclips.smart.camera.ipccamerasdk.p2p.ICameraP2P
import com.thingclips.smart.camera.middleware.p2p.IThingSmartCameraP2P

/** Live-preview connection state for the camera screen. */
enum class CameraState { Idle, Connecting, Playing, Error }

/**
 * Wraps the Tuya IPC P2P lifecycle for a single camera: create → connect → preview, and the
 * reverse on teardown. Mirrors the official sample's CameraPanelActivity but headless, so a Compose
 * screen can drive it. [onState] is invoked on the main thread.
 */
class CameraController(
    private val devId: String,
    private val onState: (CameraState) -> Unit
) {
    private val tag = "CameraController"
    private var p2p: IThingSmartCameraP2P<Any>? = null
    private var muted = true

    /** True if the device is actually an IPC camera the SDK can drive. */
    val isSupported: Boolean
        get() = ThingIPCSdk.getCameraInstance()?.isIPCDevice(devId) == true

    @Suppress("UNCHECKED_CAST")
    fun create() {
        if (p2p != null) return
        p2p = ThingIPCSdk.getCameraInstance()?.createCameraP2P(devId) as? IThingSmartCameraP2P<Any>
    }

    /** Binds the rendering surface created by ThingCameraView. */
    fun attachView(view: Any) {
        p2p?.generateCameraView(view)
    }

    private val listener = object : AbsP2pCameraListener() {
        override fun onSessionStatusChanged(camera: Any?, sessionId: Int, sessionStatus: Int) {
            // -3 timeout / -105 auth failure: surface as error (UI offers retry).
            if (sessionStatus == -3 || sessionStatus == -105) {
                Log.w(tag, "session error status=$sessionStatus")
                onState(CameraState.Error)
            }
        }
    }

    /** Connects the P2P channel and starts live preview. Safe to call when resumed. */
    fun start() {
        val camera = p2p ?: run { onState(CameraState.Error); return }
        camera.registerP2PCameraListener(listener)
        if (camera.isConnecting) {
            startPreview()
            return
        }
        onState(CameraState.Connecting)
        if (ThingIPCSdk.getCameraInstance()?.isLowPowerDevice(devId) == true) {
            ThingIPCSdk.getDoorbell()?.wirelessWake(devId)
        }
        camera.connect(devId, object : OperationDelegateCallBack {
            override fun onSuccess(sessionId: Int, requestId: Int, data: String?) = startPreview()
            override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                Log.w(tag, "connect failed err=$errCode")
                onState(CameraState.Error)
            }
        })
    }

    private fun startPreview() {
        val camera = p2p ?: return
        camera.startPreview(object : OperationDelegateCallBack {
            override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                applyMute()
                onState(CameraState.Playing)
            }

            override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                Log.w(tag, "startPreview failed err=$errCode")
                onState(CameraState.Error)
            }
        })
    }

    fun toggleMute(): Boolean {
        muted = !muted
        applyMute()
        return muted
    }

    private fun applyMute() {
        val camera = p2p ?: return
        camera.setMute(
            ICameraP2P.PLAYMODE.LIVE,
            if (muted) ICameraP2P.MUTE else ICameraP2P.UNMUTE,
            object : OperationDelegateCallBack {
                override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {}
                override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {}
            }
        )
    }

    /** Stops preview and releases the P2P channel; call when the screen is paused/backgrounded. */
    fun stop() {
        val camera = p2p ?: return
        camera.stopPreview(object : OperationDelegateCallBack {
            override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {}
            override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {}
        })
        camera.removeOnP2PCameraListener()
        camera.disconnect(object : OperationDelegateCallBack {
            override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {}
            override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {}
        })
        onState(CameraState.Idle)
    }

    /** Final cleanup; call when the screen is destroyed. */
    fun destroy() {
        p2p?.destroyP2P()
        p2p = null
    }
}
