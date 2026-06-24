package com.shaqsid.smart.domain.usecase

import com.shaqsid.smart.domain.repository.AuthRepository

class IsLoggedInUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Boolean = repository.isLoggedIn()
}

class SendRegisterCodeUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, countryCode: String): Result<Unit> =
        repository.sendRegisterCode(email, countryCode)
}

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
        code: String,
        countryCode: String
    ): Result<Unit> = repository.register(email, password, code, countryCode)
}

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, countryCode: String): Result<Unit> =
        repository.login(email, password, countryCode)
}

class LogoutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.logout()
}

data class AuthUseCases(
    val isLoggedIn: IsLoggedInUseCase,
    val sendRegisterCode: SendRegisterCodeUseCase,
    val register: RegisterUseCase,
    val login: LoginUseCase,
    val logout: LogoutUseCase
)
