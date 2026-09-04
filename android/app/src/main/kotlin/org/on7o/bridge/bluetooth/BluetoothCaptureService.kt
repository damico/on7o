package org.on7o.bridge.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.on7o.bridge.BridgeApplication
import org.on7o.bridge.MainActivity
import org.on7o.bridge.R
import org.on7o.bridge.core.capture.CaptureStore
import org.on7o.bridge.core.capture.CaptureWriter
import org.on7o.bridge.core.protocol.Frame
import org.on7o.bridge.core.protocol.FrameReader
import org.on7o.bridge.settings.SettingsRepository
import org.on7o.bridge.sync.SyncScheduler
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Foreground service that owns the BLE connection to a paired StickS3 and
 * ingests the capture stream it sends, per PROTOCOL.md. Runs for as long as
 * the user has toggled "Connect" on, reconnecting with backoff whenever the
 * link drops. There is no real firmware to connect to yet at the time this
 * was written, so this is the piece whose behavior against real hardware is
 * necessarily unverified for now.
 *
 * The StickS3 is an ESP32-S3, which has no Bluetooth Classic radio, only
 * BLE, so this app is a BLE central: it connects over GATT to a device found
 * by scanning (see [BleDeviceRepository]), rather than opening an RFCOMM
 * socket to an already-bonded device.
 */
class BluetoothCaptureService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "bluetooth_capture"
        private const val NOTIFICATION_ID = 1
        private const val RECONNECT_DELAY_MS = 3000L
        private const val READY_TIMEOUT_MS = 10000L
        private const val PREFERRED_MTU = 247

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BluetoothCaptureService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BluetoothCaptureService::class.java))
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var gatt: BluetoothGatt? = null
    private var connectionJob: Job? = null

    private lateinit var captureStore: CaptureStore
    private lateinit var bleDevices: BleDeviceRepository
    private lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        val app = application as BridgeApplication
        captureStore = app.captureStore
        bleDevices = app.bleDeviceRepository
        settings = app.settingsRepository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(ConnectionState.Connecting)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // A second start command (a double tap on Connect, or Android redelivering the
        // intent) must not spawn a second connection loop: two loops racing on the same
        // gatt field is exactly what caused one to close() the other's connection mid-setup.
        if (connectionJob?.isActive != true) {
            connectionJob = scope.launch { connectionLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        BluetoothCaptureStatus.update(ConnectionState.Disconnected)
        closeGatt()
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun connectionLoop() {
        var attempt = 0
        while (scope.isActive) {
            val address = settings.pairedDeviceAddress.first()
            val device = address?.let { bleDevices.findDevice(it) }
            if (device == null) {
                BluetoothCaptureStatus.update(ConnectionState.Disconnected)
                delay(RECONNECT_DELAY_MS)
                continue
            }

            BluetoothCaptureStatus.update(
                if (attempt == 0) ConnectionState.Connecting else ConnectionState.Reconnecting(attempt),
            )
            val ok = connectAndIngest(device)
            attempt = if (ok) 0 else attempt + 1
            closeGatt()
            BluetoothCaptureStatus.update(ConnectionState.Reconnecting(attempt))
            delay(RECONNECT_DELAY_MS)
        }
    }

    /** Connects, waits for the link to be ready, then ingests frames until the connection drops. */
    @Suppress("MissingPermission") // the connect loop only reaches here once a device was resolved
    private suspend fun connectAndIngest(device: BluetoothDevice): Boolean {
        val inputStream = GattNotificationInputStream()
        var characteristic: BluetoothGattCharacteristic? = null

        val ready = suspendCancellableCoroutine<Boolean> { cont ->
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            // AUDIO_CHUNK notifications flood out far faster than the default
                            // "balanced" connection interval can drain them, so most packets
                            // never actually leave the radio (NimBLE reports BLE_HS_ENOMEM on
                            // nearly every send). Requesting the fast interval up front raises
                            // the real throughput ceiling to match what the firmware sends.
                            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            g.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            inputStream.close()
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    val service = g.getService(UUID.fromString(BleIds.SERVICE_UUID))
                    val chr = service?.getCharacteristic(UUID.fromString(BleIds.CAPTURE_CHARACTERISTIC_UUID))
                    if (chr == null) {
                        if (cont.isActive) cont.resume(false)
                        return
                    }
                    characteristic = chr
                    g.requestMtu(PREFERRED_MTU)
                }

                override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                    val chr = characteristic
                    val cccd = chr?.getDescriptor(UUID.fromString(BleIds.CLIENT_CHARACTERISTIC_CONFIG_UUID))
                    if (chr == null || cccd == null) {
                        if (cont.isActive) cont.resume(false)
                        return
                    }
                    g.setCharacteristicNotification(chr, true)
                    // CAPTURE_HEADER/CAPTURE_END arrive as indications (reliable), AUDIO_CHUNK as
                    // notifications (fast, unconfirmed): both bits of the CCCD need to be set, not
                    // just BluetoothGattDescriptor.ENABLE_INDICATION_VALUE, or the firmware's
                    // notify() calls for the bulk payload are silently dropped.
                    val enableBoth = byteArrayOf(0x03, 0x00)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, enableBoth)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = enableBoth
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                }

                override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (cont.isActive) cont.resume(status == BluetoothGatt.GATT_SUCCESS)
                }

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun onCharacteristicChanged(g: BluetoothGatt, chr: BluetoothGattCharacteristic) {
                    chr.value?.let { inputStream.offer(it) }
                }
            }

            val g = device.connectGatt(applicationContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            gatt = g
            cont.invokeOnCancellation { g.close() }
        }

        val readyWithTimeout = withTimeoutOrNull(READY_TIMEOUT_MS) { ready } ?: false
        if (!readyWithTimeout) {
            inputStream.close()
            return false
        }

        val deviceName = runCatching { device.name }.getOrNull() ?: device.address
        BluetoothCaptureStatus.update(ConnectionState.Connected(deviceName))
        updateNotification(ConnectionState.Connected(deviceName))

        withContext(Dispatchers.IO) {
            ingestFrames(FrameReader(inputStream))
        }
        return true
    }

    /** Reads frames until the connection drops (readFrame() returns null). */
    private fun ingestFrames(reader: FrameReader) {
        var active: Pair<String, CaptureWriter>? = null
        var receivedBytes = 0L
        while (scope.isActive) {
            val frame = reader.readFrame() ?: break
            when (frame) {
                is Frame.CaptureHeader -> {
                    active?.let { captureStore.discard(it.first, it.second) }
                    val capturedAt = frame.capturedAt
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: Instant.now()
                    active = captureStore.begin(
                        frame.deviceId,
                        frame.sampleRate,
                        frame.channels,
                        frame.bitsPerSample,
                        capturedAt,
                    )
                    receivedBytes = 0
                }

                is Frame.AudioChunk -> {
                    active?.second?.append(frame.payload)
                    receivedBytes += frame.payload.size
                }

                is Frame.CaptureEnd -> {
                    active?.let { (id, writer) ->
                        // AUDIO_CHUNK travels as unconfirmed notifications for speed (see
                        // PROTOCOL.md), so a dropped packet is only ever caught here, by
                        // comparing what actually arrived against what the stick says it sent.
                        if (receivedBytes == frame.totalPcmBytes) {
                            captureStore.complete(id, writer)
                            SyncScheduler.triggerNow(applicationContext)
                        } else {
                            captureStore.discard(id, writer)
                        }
                    }
                    active = null
                }

                is Frame.CaptureAck -> Unit // not expected in this direction; ignored
            }
        }
        active?.let { captureStore.discard(it.first, it.second) }
    }

    private fun closeGatt() {
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Bluetooth capture",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText(state))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: ConnectionState) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun statusText(state: ConnectionState): String = when (state) {
        ConnectionState.Disconnected -> "No device selected"
        ConnectionState.Connecting -> "Connecting..."
        is ConnectionState.Connected -> "Connected to ${state.deviceName}"
        is ConnectionState.Reconnecting -> "Reconnecting (attempt ${state.attempt})..."
    }
}
