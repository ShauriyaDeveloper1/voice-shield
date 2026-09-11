package com.sagar.voice_shield.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sagar.voice_shield.data.local.PreferencesManager
import com.sagar.voice_shield.data.repository.AuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val message: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = prefs.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val userName: StateFlow<String?> = prefs.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userId: StateFlow<String?> = prefs.userId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _backendOnline = MutableStateFlow<Boolean?>(null)
    val backendOnline: StateFlow<Boolean?> = _backendOnline.asStateFlow()

    init {
        checkHealth()
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.login(username, password)
            result.fold(
                onSuccess = { response ->
                    _uiState.value = AuthUiState(isSuccess = true, message = response.message)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState(error = error.message ?: "Login failed")
                }
            )
        }
    }

    fun register(name: String, email: String, phone: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.register(name, email, phone, password)
            result.fold(
                onSuccess = { response ->
                    _uiState.value = AuthUiState(isSuccess = true, message = response.message)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState(error = error.message ?: "Registration failed")
                }
            )
        }
    }

    val verificationStatus = MutableStateFlow<String?>(null)

    fun sendVerification(email: String, name: String = "", phone: String = "") {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val res = authRepository.sendVerification(email, name, phone)
            res.fold(
                onSuccess = { msg ->
                    _uiState.value = AuthUiState(isLoading = false, message = msg)
                    verificationStatus.value = msg
                },
                onFailure = { err ->
                    _uiState.value = AuthUiState(isLoading = false, error = err.message ?: "Failed to send verification email")
                }
            )
        }
    }




    fun loginWithGoogleAccount(email: String, name: String, id: String?, idToken: String?) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.loginWithGoogle(email = email, name = name, googleId = id, idToken = idToken)
            result.fold(
                onSuccess = { response ->
                    _uiState.value = AuthUiState(isSuccess = true, message = response.message)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState(error = error.message ?: "Google login failed")
                }
            )
        }
    }

    fun demoLogin(email: String = "sg0169690@gmail.com") {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.demoLogin(email)
            result.fold(
                onSuccess = { response ->
                    _uiState.value = AuthUiState(isSuccess = true, message = response.message)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState(error = error.message ?: "Demo login failed")
                }
            )
        }
    }

    val otpSent = MutableStateFlow(false)
    val otpMessage = MutableStateFlow<String?>(null)
    val currentReqId = MutableStateFlow<String>("")

    fun sendOtp(phone: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.sendOtp(phone)
            result.fold(
                onSuccess = { res ->
                    _uiState.value = AuthUiState(isLoading = false, message = res.message)
                    otpSent.value = true
                    otpMessage.value = res.message
                    currentReqId.value = res.reqId ?: ""
                },
                onFailure = { err ->
                    _uiState.value = AuthUiState(isLoading = false, error = err.message ?: "Failed to send OTP")
                }
            )
        }
    }

    var verifiedToken: String = ""
    var verifiedPhone: String = ""

    fun verifyOtp(phone: String, otp: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.verifyOtp(phone, otp, currentReqId.value)
            result.fold(
                onSuccess = { res ->
                    verifiedToken = res.token ?: res.accessToken ?: ""
                    verifiedPhone = phone
                    _uiState.value = AuthUiState(isSuccess = true, message = res.message)
                },
                onFailure = { err ->
                    _uiState.value = AuthUiState(isLoading = false, error = err.message ?: "Invalid OTP")
                }
            )
        }
    }

    val retryStatus = MutableStateFlow<String?>(null)

    fun resendSmsOtp(phone: String, onResult: ((String) -> Unit)? = null) {
        val reqId = currentReqId.value
        viewModelScope.launch {
            if (reqId.isNotBlank()) {
                val result = authRepository.retryOtp(reqId, channel = 11) // 11 = SMS
                val msg = if (result.isSuccess) "SMS resent successfully" else "SMS queued. Demo PIN is also accepted."
                retryStatus.value = msg
                onResult?.invoke(msg)
            } else {
                sendOtp(phone)
            }
        }
    }

    fun completeRegistration(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val phone = verifiedPhone.ifBlank { "+91" }
            authRepository.completeRegistration(name, phone, verifiedToken)
            onDone()
        }
    }

    fun updateProfileName(name: String, phone: String, onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.updateUserProfileName(name, phone)
            onDone()
        }
    }

    fun resetOtpState() {
        otpSent.value = false
        otpMessage.value = null
        retryStatus.value = null
        currentReqId.value = ""
        _uiState.value = AuthUiState()
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setAuthError(error: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = error)
    }

    private fun checkHealth() {
        viewModelScope.launch {
            _backendOnline.value = authRepository.checkHealth()
        }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val prefs: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository, prefs) as T
        }
    }
}
