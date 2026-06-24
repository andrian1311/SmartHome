package com.shaqsid.smart.domain.repository

interface AuthRepository {
    /** Whether there is a valid, logged-in Tuya user session. */
    fun isLoggedIn(): Boolean

    /** Sends an email verification code used to complete registration. */
    suspend fun sendRegisterCode(email: String, countryCode: String): Result<Unit>

    /** Registers a new account with an email, password and the verification code. */
    suspend fun register(
        email: String,
        password: String,
        code: String,
        countryCode: String
    ): Result<Unit>

    /** Logs in with an existing email/password account. */
    suspend fun login(email: String, password: String, countryCode: String): Result<Unit>

    /** Ends the current session. */
    suspend fun logout(): Result<Unit>
}
