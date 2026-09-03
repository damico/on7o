package org.on7o.bridge.bluetooth

/** State of the Bluetooth link to the StickS3, published by BluetoothCaptureService. */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
}
