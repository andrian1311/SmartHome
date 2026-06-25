package com.shaqsid.smart.data.repository

import com.shaqsid.smart.domain.repository.AuthRepository
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.bizbundle.initializer.BizBundleInitializer
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IResultCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AuthRepositoryImpl : AuthRepository {

    private val userInstance get() = ThingHomeSdk.getUserInstance()

    override fun isLoggedIn(): Boolean =
        runCatching { userInstance.isLogin }.getOrDefault(false)

    override suspend fun sendRegisterCode(email: String, countryCode: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            // type 1 = registration verification code; region "" lets the SDK resolve it.
            guardSdkCall(continuation) {
                userInstance.sendVerifyCodeWithUserName(
                    email,
                    "",
                    countryCode,
                    1,
                    object : IResultCallback {
                        override fun onSuccess() {
                            if (continuation.isActive) continuation.resume(Result.success(Unit))
                        }

                        override fun onError(code: String?, error: String?) {
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(Exception(error ?: "Failed to send code")))
                            }
                        }
                    }
                )
            }
        }

    override suspend fun register(
        email: String,
        password: String,
        code: String,
        countryCode: String
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        guardSdkCall(continuation) {
            userInstance.registerAccountWithEmail(
                countryCode,
                email,
                password,
                code,
                object : IRegisterCallback {
                    override fun onSuccess(user: User?) {
                        BizBundleInitializer.onLogin()
                        if (continuation.isActive) continuation.resume(Result.success(Unit))
                    }

                    override fun onError(code: String?, error: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception(error ?: "Registration failed")))
                        }
                    }
                }
            )
        }
    }

    override suspend fun login(email: String, password: String, countryCode: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            guardSdkCall(continuation) {
                userInstance.loginWithEmail(
                    countryCode,
                    email,
                    password,
                    object : ILoginCallback {
                        override fun onSuccess(user: User?) {
                            BizBundleInitializer.onLogin()
                            if (continuation.isActive) continuation.resume(Result.success(Unit))
                        }

                        override fun onError(code: String?, error: String?) {
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(Exception(error ?: "Login failed")))
                            }
                        }
                    }
                )
            }
        }

    override suspend fun logout(): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            guardSdkCall(continuation) {
                userInstance.logout(object : ILogoutCallback {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(Result.success(Unit))
                    }

                    override fun onError(code: String?, error: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception(error ?: "Logout failed")))
                        }
                    }
                })
            }
        }

    /**
     * Runs a Tuya SDK call, converting any synchronous exception into a [Result.failure] instead
     * of letting it crash the app. The SDK throws synchronously (e.g. NPE from a null
     * ApiUrlProvider) when init failed — which happens when the appKey/appSecret/packageName/
     * SHA-256 don't match the security image on the Tuya platform.
     */
    private inline fun <T> guardSdkCall(
        continuation: kotlinx.coroutines.CancellableContinuation<Result<T>>,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (e: Throwable) {
            if (continuation.isActive) {
                continuation.resume(
                    Result.failure(
                        Exception(
                            "Tuya SDK not ready. Verify the appKey, appSecret, package name and " +
                                "signing SHA-256 match the security image on the Tuya platform. (${e.message})"
                        )
                    )
                )
            }
        }
    }
}
