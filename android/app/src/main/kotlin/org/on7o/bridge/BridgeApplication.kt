package org.on7o.bridge

import android.app.Application
import okhttp3.OkHttpClient
import org.on7o.bridge.bluetooth.PairedDeviceRepository
import org.on7o.bridge.core.capture.CaptureStore
import org.on7o.bridge.core.sync.UploadClient
import org.on7o.bridge.settings.SettingsRepository
import java.io.File

/**
 * Hand-wires this app's few dependencies. Deliberately no DI framework: at
 * this size, manual constructor calls are simpler and cheaper to build than
 * Hilt's annotation processing, in the same spirit as the repo's aversion to
 * heavyweight frameworks where a plain approach works.
 */
class BridgeApplication : Application() {

    lateinit var captureStore: CaptureStore
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var pairedDeviceRepository: PairedDeviceRepository
        private set
    lateinit var uploadClient: UploadClient
        private set

    override fun onCreate() {
        super.onCreate()
        captureStore = CaptureStore(File(filesDir, "captures"))
        settingsRepository = SettingsRepository(this)
        pairedDeviceRepository = PairedDeviceRepository(this)
        uploadClient = UploadClient(OkHttpClient())
    }
}
