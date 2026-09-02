package org.on7o.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val serverBaseUrl by viewModel.serverBaseUrl.collectAsState()
    val pairedDeviceAddress by viewModel.pairedDeviceAddress.collectAsState()
    val bondedDevices by viewModel.bondedDevices.collectAsState()
    var urlField by remember(serverBaseUrl) { mutableStateOf(serverBaseUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            Text(
                "LAN address of the on7o server, e.g. http://10.109.118.42:8080. From the " +
                    "emulator, a server running on this machine is reachable at http://10.0.2.2:8080.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = urlField,
                onValueChange = { urlField = it },
                label = { Text("Server base URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.saveServerBaseUrl(urlField) }) { Text("Save") }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Paired StickS3", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::refreshBondedDevices) { Text("Refresh") }
            }

            if (bondedDevices.isEmpty()) {
                Text(
                    "No bonded devices found. Pair the StickS3 in Android's system Bluetooth settings first.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                bondedDevices.forEach { device ->
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = { Text(device.address) },
                        trailingContent = {
                            RadioButton(
                                selected = device.address == pairedDeviceAddress,
                                onClick = { viewModel.selectPairedDevice(device.address) },
                            )
                        },
                    )
                }
            }
        }
    }
}
