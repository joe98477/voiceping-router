package com.voiceping.android.domain.usecase

import com.voiceping.android.data.repository.AuthRepository
import com.voiceping.android.domain.model.User
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        return authRepository.login(email, password)
    }
}
