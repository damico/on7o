package org.on7o.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.on7o.bridge.sync.SyncScheduler
import org.on7o.bridge.ui.HomeScreen
import org.on7o.bridge.ui.SettingsScreen
import org.on7o.bridge.ui.theme.OnStickBridgeTheme

private sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
}

/** Single-activity host: two screens (Home, Settings), switched in place rather than pulling in Navigation Compose for just this. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncScheduler.schedulePeriodic(applicationContext)

        setContent {
            OnStickBridgeTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                when (screen) {
                    Screen.Home -> HomeScreen(onOpenSettings = { screen = Screen.Settings })
                    Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                }
            }
        }
    }
}
