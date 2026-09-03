package org.on7o.bridge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.on7o.bridge.BridgeApplication
import org.on7o.bridge.bluetooth.BluetoothCaptureService
import org.on7o.bridge.bluetooth.BluetoothCaptureStatus
import org.on7o.bridge.bluetooth.ConnectionState
import org.on7o.bridge.core.capture.Capture
import org.on7o.bridge.sync.SyncScheduler

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val bridgeApp get() = getApplication<BridgeApplication>()

    val connectionState: StateFlow<ConnectionState> = BluetoothCaptureStatus.state

    private val _captures = MutableStateFlow<List<Capture>>(emptyList())
    val captures: StateFlow<List<Capture>> = _captures.asStateFlow()

    init {
        refreshCaptures()
    }

    fun refreshCaptures() {
        _captures.value = bridgeApp.captureStore.list()
    }

    fun connect() {
        BluetoothCaptureService.start(bridgeApp)
    }

    fun disconnect() {
        BluetoothCaptureService.stop(bridgeApp)
    }

    fun syncNow() {
        SyncScheduler.triggerNow(bridgeApp)
    }
}
