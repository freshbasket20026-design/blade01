package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BladeApp
import com.example.data.model.AdminAppSettings
import com.example.data.model.UserProfile
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository: BladeRepository = (application as BladeApp).repository

    val isDarkMode: StateFlow<Boolean> = repository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val languageCode: StateFlow<String> = repository.languageCode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    val appSettings: StateFlow<AdminAppSettings> = repository.appSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AdminAppSettings())

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessage = _toastMessage.asSharedFlow()

    val isSupabaseConfigured: Boolean
        get() = repository.isSupabaseConfigured()

    init {
        refreshCurrentUser()
        refreshAppSettings()
    }

    fun refreshCurrentUser() {
        viewModelScope.launch {
            _currentUser.value = repository.restoreCurrentUser()
        }
    }

    fun refreshAppSettings() {
        viewModelScope.launch {
            repository.fetchAppSettings()
        }
    }

    fun setCurrentUser(user: UserProfile?) {
        _currentUser.value = user
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(enabled) }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { repository.setLanguageCode(code) }
    }

    fun emitMessage(msg: String) {
        _toastMessage.tryEmit(msg)
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _currentUser.value = null
            emitMessage("Signed out")
        }
    }
}

class BladeViewModelFactory(
    private val repository: BladeRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(FeedViewModel::class.java) -> FeedViewModel(repository) as T
            modelClass.isAssignableFrom(DiscoverViewModel::class.java) -> DiscoverViewModel(repository) as T
            modelClass.isAssignableFrom(CreateViewModel::class.java) -> CreateViewModel(repository) as T
            modelClass.isAssignableFrom(InboxViewModel::class.java) -> InboxViewModel(repository) as T
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(repository) as T
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repository) as T
            modelClass.isAssignableFrom(AdminViewModel::class.java) -> AdminViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
