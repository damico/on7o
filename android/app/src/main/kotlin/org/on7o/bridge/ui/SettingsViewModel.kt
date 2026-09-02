package org.on7o.bridge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.on7o.bridge.BridgeApplication
import org.on7o.bridge.bluetooth.PairedDevice

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val bridgeApp get() = getApplication<BridgeApplication>()

    private val _serverBaseUrl = MutableStateFlow("")
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()

    private val _pairedDeviceAddress = MutableStateFlow<String?>(null)
    val pairedDeviceAddress: StateFlow<String?> = _pairedDeviceAddress.asStateFlow()

    private val _bondedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val bondedDevices: StateFlow<List<PairedDevice>> = _bondedDevices.asStateFlow()

    init {
        viewModelScope.launch {
            bridgeApp.settingsRepository.serverBaseUrl.collect { _serverBaseUrl.value = it ?: "" }
        }
        viewModelScope.launch {
            bridgeApp.settingsRepository.pairedDeviceAddress.collect { _pairedDeviceAddress.value = it }
        }
        refreshBondedDevices()
    }

    fun refreshBondedDevices() {
        _bondedDevices.value = bridgeApp.pairedDeviceRepository.bondedDevices()
    }

    fun saveServerBaseUrl(value: String) {
        viewModelScope.launch { bridgeApp.settingsRepository.setServerBaseUrl(value) }
    }

    fun selectPairedDevice(address: String) {
        viewModelScope.launch { bridgeApp.settingsRepository.setPairedDeviceAddress(address) }
    }
}
