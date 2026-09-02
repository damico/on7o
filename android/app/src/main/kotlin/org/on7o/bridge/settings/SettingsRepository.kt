package org.on7o.bridge.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

private val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
private val PAIRED_DEVICE_ADDRESS = stringPreferencesKey("paired_device_address")

/**
 * User-configured settings for this bridge instance: the server's base URL
 * and which bonded device to treat as the StickS3. Unlike the firmware's
 * hardcoded config.h, both are set at runtime because the phone roams
 * between networks and devices.
 */
class SettingsRepository(private val context: Context) {

    val serverBaseUrl: Flow<String?> = context.dataStore.data.map { it[SERVER_BASE_URL] }
    val pairedDeviceAddress: Flow<String?> = context.dataStore.data.map { it[PAIRED_DEVICE_ADDRESS] }

    suspend fun setServerBaseUrl(value: String) {
        context.dataStore.edit { it[SERVER_BASE_URL] = value }
    }

    suspend fun setPairedDeviceAddress(value: String) {
        context.dataStore.edit { it[PAIRED_DEVICE_ADDRESS] = value }
    }
}
