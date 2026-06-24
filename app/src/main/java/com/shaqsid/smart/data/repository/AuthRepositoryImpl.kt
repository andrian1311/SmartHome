package com.shaqsid.smart.data.repository

import com.shaqsid.smart.domain.repository.AuthRepository
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IResultCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AuthRepositoryImpl : AuthRepository {

    private val userInstance get() = ThingHomeSdk.getUserInstance()

    override fun isLoggedIn(): Boolean = userInstance.isLogin

    override suspend fun sendRegisterCode(email: String, countryCode: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            // type 1 = registration verification code; region "" lets the SDK resolve it.
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

    override suspend fun register(
        email: String,
        password: String,
        code: String,
        countryCode: String
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        userInstance.registerAccountWithEmail(
            countryCode,
            email,
            password,
            code,
            object : IRegisterCallback {
                override fun onSuccess(user: User?) {
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

    override suspend fun login(email: String, password: String, countryCode: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            userInstance.loginWithEmail(
                countryCode,
                email,
                password,
                object : ILoginCallback {
                    override fun onSuccess(user: User?) {
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

    override suspend fun logout(): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
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
