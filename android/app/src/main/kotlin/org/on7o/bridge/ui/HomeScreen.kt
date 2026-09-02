package org.on7o.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.on7o.bridge.bluetooth.ConnectionState
import org.on7o.bridge.core.capture.Capture
import org.on7o.bridge.core.capture.SyncState

private const val CAPTURE_LIST_REFRESH_MS = 5000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, viewModel: HomeViewModel = viewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    val captures by viewModel.captures.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshCaptures()
            delay(CAPTURE_LIST_REFRESH_MS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("on7o Bridge") },
                actions = { TextButton(onClick = onOpenSettings) { Text("Settings") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            ConnectionCard(connectionState, onConnect = viewModel::connect, onDisconnect = viewModel::disconnect)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Captures", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::syncNow) { Text("Sync now") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (captures.isEmpty()) {
                Text("No captures yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(captures) { capture -> CaptureRow(capture) }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: ConnectionState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Bluetooth", style = MaterialTheme.typography.titleMedium)
                Text(connectionStatusText(state), style = MaterialTheme.typography.bodyMedium)
            }
            if (state == ConnectionState.Disconnected) {
                Button(onClick = onConnect) { Text("Connect") }
            } else {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        }
    }
}

private fun connectionStatusText(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Connecting -> "Connecting..."
    is ConnectionState.Connected -> "Connected to ${state.deviceName}"
    is ConnectionState.Reconnecting -> "Reconnecting (attempt ${state.attempt})..."
}

@Composable
private fun CaptureRow(capture: Capture) {
    ListItem(
        headlineContent = { Text(capture.id) },
        supportingContent = { Text("${capture.deviceId} - ${capture.pcmBytes} bytes") },
        trailingContent = { Text(syncStateLabel(capture.syncState)) },
    )
}

private fun syncStateLabel(state: SyncState): String = when (state) {
    SyncState.PENDING -> "Pending"
    SyncState.SYNCED -> "Synced"
    SyncState.FAILED -> "Failed"
}
