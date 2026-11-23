package com.example.ui.viewmodel

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val offerBrowserGoogleSignIn: Boolean = false,
    val suggestSignUp: Boolean = false,
    val emailAlreadyRegistered: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class AuthViewModel(private val repository: BladeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun setMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                errorMessage = null,
                infoMessage = null,
                offerBrowserGoogleSignIn = false,
                suggestSignUp = false,
                emailAlreadyRegistered = false
            )
        }
    }

    fun signInWithEmail(email: String, password: String, onAuthenticated: (UserProfile) -> Unit) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please enter email address and password.",
                    suggestSignUp = false,
                    emailAlreadyRegistered = false
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    suggestSignUp = false,
                    emailAlreadyRegistered = false
                )
            }
            repository.signInWithEmail(cleanEmail, password).fold(
                onSuccess = { profile ->
                    _uiState.update { it.copy(isLoading = false, suggestSignUp = false) }
                    onAuthenticated(profile)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Sign in failed"
                    val isInvalidCreds = msg.contains("invalid", ignoreCase = true) ||
                        msg.contains("create account", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = msg,
                            suggestSignUp = isInvalidCreds
                        )
                    }
                }
            )
        }
    }

    fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
        onAuthenticated: (UserProfile) -> Unit
    ) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        if (cleanName.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please enter your name.",
                    suggestSignUp = false,
                    emailAlreadyRegistered = false
                )
            }
            return
        }
        if (cleanEmail.isBlank() || !emailRegex.matches(cleanEmail)) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please enter a valid email address.",
                    suggestSignUp = false,
                    emailAlreadyRegistered = false
                )
            }
            return
        }
        if (password.length < 6) {
            _uiState.update {
                it.copy(
                    errorMessage = "Password must be at least 6 characters.",
                    suggestSignUp = false,
                    emailAlreadyRegistered = false
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    suggestSignUp = false,
                    emailAlreadyRegistered = false
                )
            }
            repository.signUpWithEmail(cleanName, cleanEmail, password).fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            suggestSignUp = false,
                            emailAlreadyRegistered = false
                        )
                    }
                    onAuthenticated(profile)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Sign up failed"
                    val isDuplicate = msg.contains("already registered", ignoreCase = true) ||
                        msg.contains("already exists", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = msg,
                            emailAlreadyRegistered = isDuplicate
                        )
                    }
                }
            )
        }
    }

    fun signInWithGoogleToken(idToken: String, onAuthenticated: (UserProfile) -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isGoogleLoading = true,
                    errorMessage = null,
                    infoMessage = null,
                    offerBrowserGoogleSignIn = false
                )
            }
            repository.signInWithGoogleIdToken(idToken).fold(
                onSuccess = { profile ->
                    _uiState.update { it.copy(isLoading = false, isGoogleLoading = false) }
                    onAuthenticated(profile)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isGoogleLoading = false,
                            errorMessage = err.message ?: "Google sign-in failed"
                        )
                    }
                }
            )
        }
    }

    fun setGoogleLoading(loading: Boolean) {
        _uiState.update {
            it.copy(
                isGoogleLoading = loading,
                errorMessage = if (loading) null else it.errorMessage,
                infoMessage = if (loading) null else it.infoMessage,
                offerBrowserGoogleSignIn = if (loading) false else it.offerBrowserGoogleSignIn
            )
        }
    }

    fun onGoogleCredentialCancelled(fastSystemCancel: Boolean, canUseBrowserFallback: Boolean) {
        _uiState.update {
            if (fastSystemCancel) {
                it.copy(
                    isGoogleLoading = false,
                    errorMessage = null,
                    infoMessage = if (canUseBrowserFallback) {
                        "Google one-tap prompt wasn't available on this device. Continue with Google in Browser or use Email."
                    } else {
                        "Google prompt could not open on this device. Please sign in with Email or check your Google account."
                    },
                    offerBrowserGoogleSignIn = canUseBrowserFallback
                )
            } else {
                it.copy(
                    isGoogleLoading = false,
                    errorMessage = null,
                    infoMessage = "Google Sign-In was closed. Tap Continue with Google to try again or sign in with Email.",
                    offerBrowserGoogleSignIn = canUseBrowserFallback
                )
            }
        }
    }

    fun onGoogleCredentialError(rawMessage: String?, canUseBrowserFallback: Boolean) {
        val clean = sanitizeCredentialError(rawMessage)
        _uiState.update {
            it.copy(
                isGoogleLoading = false,
                errorMessage = clean,
                infoMessage = null,
                offerBrowserGoogleSignIn = canUseBrowserFallback
            )
        }
    }

    fun launchBrowserGoogleOAuth(context: Context) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGoogleLoading = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            repository.prepareGoogleBrowserOAuthUrl().fold(
                onSuccess = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        if (context !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    try {
                        context.startActivity(intent)
                        _uiState.update {
                            it.copy(
                                isGoogleLoading = false,
                                infoMessage = "Complete Google Sign-In in your browser to return to Blade."
                            )
                        }
                    } catch (_: ActivityNotFoundException) {
                        _uiState.update {
                            it.copy(
                                isGoogleLoading = false,
                                errorMessage = "No web browser app found to open Google Sign-In."
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = err.message ?: "Unable to start Google Sign-In."
                        )
                    }
                }
            )
        }
    }

    fun completeOAuthCallback(uri: Uri, onAuthenticated: (UserProfile) -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isGoogleLoading = true,
                    errorMessage = null,
                    infoMessage = null,
                    offerBrowserGoogleSignIn = false
                )
            }
            repository.completeOAuthCallbackUri(uri).fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(isLoading = false, isGoogleLoading = false)
                    }
                    onAuthenticated(profile)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isGoogleLoading = false,
                            errorMessage = err.message ?: "Google Sign-In did not complete."
                        )
                    }
                }
            )
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your account email.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.sendPasswordReset(email).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isLoading = false, infoMessage = "Password reset link sent to $email.")
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Reset failed") }
                }
            )
        }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = sanitizeCredentialError(message)) }
    }

    private fun sanitizeCredentialError(raw: String?): String {
        val msg = raw?.trim().orEmpty()
        return when {
            msg.isBlank() -> "Google Sign-In couldn't complete. Please try again."
            msg.contains("cancelled by the user", ignoreCase = true) ||
                msg.contains("canceled by the user", ignoreCase = true) ->
                "Google Sign-In was closed before finishing. Please try again or continue in browser."
            msg.contains("no credentials available", ignoreCase = true) ||
                msg.contains("no matching credentials", ignoreCase = true) ->
                "No Google account is signed in on this device for one-tap sign-in."
            else -> msg
        }
    }
}
