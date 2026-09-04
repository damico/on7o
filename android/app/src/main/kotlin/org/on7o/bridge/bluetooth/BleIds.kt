package org.on7o.bridge.bluetooth

/**
 * UUIDs for the on7o BLE GATT service, matching the constants in
 * m5sitcks3/on7o-capture/ble_transport.cpp. A private, project-specific
 * service, not one adopted by the Bluetooth SIG.
 */
object BleIds {
    const val SERVICE_UUID = "8b6c9f10-4b3e-4d2a-9f0a-1f6c7a2e0a01"
    const val CAPTURE_CHARACTERISTIC_UUID = "8b6c9f11-4b3e-4d2a-9f0a-1f6c7a2e0a01"
    const val CLIENT_CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
}
