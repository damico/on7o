package org.on7o.bridge.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.on7o.bridge.BridgeApplication
import org.on7o.bridge.MainActivity
import org.on7o.bridge.R
import org.on7o.bridge.core.capture.CaptureStore
import org.on7o.bridge.core.capture.CaptureWriter
import org.on7o.bridge.core.protocol.Frame
import org.on7o.bridge.core.protocol.FrameReader
import org.on7o.bridge.core.protocol.FrameWriter
import org.on7o.bridge.settings.SettingsRepository
import org.on7o.bridge.sync.SyncScheduler
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * Foreground service that owns the RFCOMM connection to a paired StickS3 and
 * ingests the capture stream it sends, per PROTOCOL.md. Runs for as long as
 * the user has toggled "Connect" on, reconnecting with backoff whenever the
 * link drops. There is no real firmware to connect to yet, this is the piece
 * whose behavior against real hardware is necessarily unverified for now.
 */
class BluetoothCaptureService : Service() {

    companion object {
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val NOTIFICATION_CHANNEL_ID = "bluetooth_capture"
        private const val NOTIFICATION_ID = 1
        private const val RECONNECT_DELAY_MS = 3000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BluetoothCaptureService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BluetoothCaptureService::class.java))
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var socket: BluetoothSocket? = null

    private lateinit var captureStore: CaptureStore
    private lateinit var pairedDevices: PairedDeviceRepository
    private lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        val app = application as BridgeApplication
        captureStore = app.captureStore
        pairedDevices = app.pairedDeviceRepository
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
        scope.launch { connectionLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        BluetoothCaptureStatus.update(ConnectionState.Disconnected)
        closeSocket()
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun connectionLoop() {
        var attempt = 0
        while (scope.isActive) {
            val address = settings.pairedDeviceAddress.first()
            val device = address?.let { pairedDevices.findDevice(it) }
            if (device == null) {
                BluetoothCaptureStatus.update(ConnectionState.Disconnected)
                delay(RECONNECT_DELAY_MS)
                continue
            }

            BluetoothCaptureStatus.update(
                if (attempt == 0) ConnectionState.Connecting else ConnectionState.Reconnecting(attempt),
            )
            try {
                connectAndIngest(device)
                attempt = 0
            } catch (e: IOException) {
                attempt++
            }
            closeSocket()
            BluetoothCaptureStatus.update(ConnectionState.Reconnecting(attempt))
            delay(RECONNECT_DELAY_MS)
        }
    }

    @Suppress("MissingPermission") // the connect loop only reaches here once a bonded device was resolved
    private suspend fun connectAndIngest(device: BluetoothDevice) {
        val newSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
        socket = newSocket
        newSocket.connect()

        val name = device.name ?: device.address
        BluetoothCaptureStatus.update(ConnectionState.Connected(name))
        updateNotification(ConnectionState.Connected(name))

        ingestFrames(FrameReader(newSocket.inputStream), newSocket)
    }

    private fun ingestFrames(reader: FrameReader, socket: BluetoothSocket) {
        var active: Pair<String, CaptureWriter>? = null
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
                }

                is Frame.AudioChunk -> active?.second?.append(frame.payload)

                is Frame.CaptureEnd -> {
                    active?.let { (id, writer) ->
                        captureStore.complete(id, writer)
                        runCatching { FrameWriter.writeCaptureAck(socket.outputStream, true) }
                        SyncScheduler.triggerNow(applicationContext)
                    }
                    active = null
                }

                is Frame.CaptureAck -> Unit // not expected in this direction; ignored
            }
        }
        active?.let { captureStore.discard(it.first, it.second) }
    }

    private fun closeSocket() {
        socket?.let { runCatching { it.close() } }
        socket = null
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
