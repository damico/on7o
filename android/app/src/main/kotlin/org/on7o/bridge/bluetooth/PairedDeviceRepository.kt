package org.on7o.bridge.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** A bonded (paired) Bluetooth device the user can pick as their StickS3. */
data class PairedDevice(val name: String, val address: String)

/**
 * Wraps BluetoothAdapter.bondedDevices. This app never scans for devices:
 * pairing happens out-of-band in Android's system Bluetooth settings, so all
 * it needs is BLUETOOTH_CONNECT to read the already-bonded set.
 */
class PairedDeviceRepository(private val context: Context) {

    fun hasConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun adapter() = context.getSystemService(BluetoothManager::class.java)?.adapter

    @Suppress("MissingPermission") // callers only reach here after hasConnectPermission() is true
    fun bondedDevices(): List<PairedDevice> {
        if (!hasConnectPermission()) return emptyList()
        val adapter = adapter() ?: return emptyList()
        return adapter.bondedDevices.map { PairedDevice(it.name ?: it.address, it.address) }
    }

    @Suppress("MissingPermission")
    fun findDevice(address: String): BluetoothDevice? {
        if (!hasConnectPermission()) return null
        val adapter = adapter() ?: return null
        return adapter.bondedDevices.firstOrNull { it.address == address }
    }
}
