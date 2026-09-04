package org.on7o.bridge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.on7o.bridge.BridgeApplication
import org.on7o.bridge.bluetooth.DiscoveredDevice

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val bridgeApp get() = getApplication<BridgeApplication>()

    private val _serverBaseUrl = MutableStateFlow("")
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()

    private val _pairedDeviceAddress = MutableStateFlow<String?>(null)
    val pairedDeviceAddress: StateFlow<String?> = _pairedDeviceAddress.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    init {
        viewModelScope.launch {
            bridgeApp.settingsRepository.serverBaseUrl.collect { _serverBaseUrl.value = it ?: "" }
        }
        viewModelScope.launch {
            bridgeApp.settingsRepository.pairedDeviceAddress.collect { _pairedDeviceAddress.value = it }
        }
    }

    fun scanForDevices() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            _discoveredDevices.value = bridgeApp.bleDeviceRepository.scan()
            _scanning.value = false
        }
    }

    fun saveServerBaseUrl(value: String) {
        viewModelScope.launch { bridgeApp.settingsRepository.setServerBaseUrl(value) }
    }

    fun selectPairedDevice(address: String) {
        viewModelScope.launch { bridgeApp.settingsRepository.setPairedDeviceAddress(address) }
    }
}
