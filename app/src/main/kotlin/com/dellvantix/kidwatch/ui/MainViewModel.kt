package com.dellvantix.kidwatch.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dellvantix.kidwatch.repository.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val screen: Screen = Screen.CONSENT,
    val permissionsGranted: Boolean = false,
    val isDeviceAdmin: Boolean = false,
    val pinSaved: Boolean = false,
    val deviceId: String = ""
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefsRepo: PrefsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val consentGiven = prefsRepo.isConsentGiven()
            val pinSaved = prefsRepo.getParentPin() != null
            val deviceId = prefsRepo.getDeviceId()
            val monitoring = prefsRepo.isMonitoringActive()

            _uiState.update {
                it.copy(
                    screen = when {
                        monitoring -> Screen.ACTIVE
                        consentGiven -> Screen.SETUP
                        else -> Screen.CONSENT
                    },
                    pinSaved = pinSaved,
                    deviceId = deviceId
                )
            }
        }
    }

    fun onConsentAccepted() {
        viewModelScope.launch {
            prefsRepo.setConsentGiven(true)
            _uiState.update { it.copy(screen = Screen.SETUP) }
        }
    }

    fun onPermissionsResult(results: Map<String, Boolean>) {
        val allGranted = results.values.all { it }
        _uiState.update { it.copy(permissionsGranted = allGranted) }
    }

    fun checkAdminStatus(dpm: DevicePolicyManager, component: ComponentName) {
        _uiState.update { it.copy(isDeviceAdmin = dpm.isAdminActive(component)) }
    }

    fun saveParentPin(pin: String) {
        viewModelScope.launch {
            prefsRepo.saveParentPin(pin)
            _uiState.update { it.copy(pinSaved = true) }
        }
    }

    fun onMonitoringStarted() {
        viewModelScope.launch {
            prefsRepo.setMonitoringActive(true)
            _uiState.update { it.copy(screen = Screen.ACTIVE) }
        }
    }
}
