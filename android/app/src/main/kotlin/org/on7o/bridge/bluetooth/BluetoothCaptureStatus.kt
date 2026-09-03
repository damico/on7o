package org.on7o.bridge.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide, observable connection state for the Bluetooth link. The
 * foreground service updates this; the UI observes it through [state], so
 * the UI never has to bind to the service directly.
 */
object BluetoothCaptureStatus {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    internal fun update(newState: ConnectionState) {
        _state.value = newState
    }
}
