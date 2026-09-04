package org.on7o.bridge.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.UUID

/** A BLE device seen while scanning for a StickS3. */
data class DiscoveredDevice(val name: String, val address: String)

/**
 * Scans for the stick's advertised service. Unlike Bluetooth Classic, BLE
 * peripherals here are never bonded ahead of time through system settings:
 * every device is found with a short scan, the user picks one once in
 * Settings, and its address is remembered from then on exactly like the
 * bonded-device address used to be.
 */
class BleDeviceRepository(private val context: Context) {

    fun hasScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED

    fun hasConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun adapter(): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /**
     * Scans for advertising on7o devices for [durationMs], deduplicated by
     * address. Returns an empty list immediately if scanning is not
     * possible right now (no permission, adapter off, no scanner).
     */
    @Suppress("MissingPermission") // caller checks hasScanPermission() first
    suspend fun scan(durationMs: Long = 6000): List<DiscoveredDevice> {
        val adapter = adapter() ?: return emptyList()
        if (!hasScanPermission() || !adapter.isEnabled) return emptyList()
        val scanner = adapter.bluetoothLeScanner ?: return emptyList()

        val found = LinkedHashMap<String, DiscoveredDevice>()
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(BleIds.SERVICE_UUID)))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: result.scanRecord?.deviceName ?: device.address
                found[device.address] = DiscoveredDevice(name, device.address)
            }
        }

        scanner.startScan(listOf(filter), settings, callback)
        delay(durationMs)
        scanner.stopScan(callback)

        return found.values.toList()
    }

    @Suppress("MissingPermission") // caller checks hasConnectPermission() first
    fun findDevice(address: String): BluetoothDevice? {
        if (!hasConnectPermission()) return null
        return runCatching { adapter()?.getRemoteDevice(address) }.getOrNull()
    }
}
