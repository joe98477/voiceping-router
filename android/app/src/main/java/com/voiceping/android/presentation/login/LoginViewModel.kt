package com.voiceping.android.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceping.android.data.storage.TokenManager
import com.voiceping.android.domain.model.User
import com.voiceping.android.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            // Local validation first
            val emailError = if (email.isBlank()) "Email is required" else null
            val passwordError = if (password.isBlank()) "Password is required" else null

            if (emailError != null || passwordError != null) {
                _uiState.value = LoginUiState.Error(
                    emailError = emailError,
                    passwordError = passwordError,
                    generalError = null
                )
                return@launch
            }

            _uiState.value = LoginUiState.Loading

            val result = loginUseCase(email, password)

            _uiState.value = when {
                result.isSuccess -> LoginUiState.Success(result.getOrNull()!!)
                result.isFailure -> {
                    val exception = result.exceptionOrNull()
                    val errorMessage = exception?.message ?: "Login failed"

                    // Server errors go to generalError (e.g., "Invalid credentials")
                    LoginUiState.Error(
                        emailError = null,
                        passwordError = null,
                        generalError = errorMessage
                    )
                }
                else -> LoginUiState.Error(
                    emailError = null,
                    passwordError = null,
                    generalError = "Unknown error"
                )
            }
        }
    }

    fun checkAutoLogin(): Boolean {
        val token = tokenManager.getToken()
        return token != null && !tokenManager.isTokenExpired()
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: User) : LoginUiState()
    data class Error(
        val emailError: String?,
        val passwordError: String?,
        val generalError: String?
    ) : LoginUiState()
}
