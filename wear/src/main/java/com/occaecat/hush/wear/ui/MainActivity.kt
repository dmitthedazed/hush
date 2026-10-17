package com.occaecat.hush.wear.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.occaecat.hush.wear.hub.HushHub
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Home : Screen
    data object Ambient : Screen
    data object Equalizer : Screen
    data object Connection : Screen
}

class MainActivity : ComponentActivity() {

    private val hub by lazy { HushHub.get(this) }

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hub.refreshPaired()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Notifications back the standalone session's Ongoing Activity; Bluetooth is asked for where it is used.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }

        setContent {
            HushWearTheme {
                val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
                var openOnPhone by remember { mutableStateOf(false) }
                AppScaffold {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                        entryProvider = entryProvider {
                            entry<Screen.Home> {
                                HomeScreen(
                                    hub = hub,
                                    onAmbient = { backStack += Screen.Ambient },
                                    onEqualizer = { backStack += Screen.Equalizer },
                                    onConnection = { backStack += Screen.Connection },
                                    onOpenOnPhone = {
                                        openOnPhone = true
                                        openPhoneApp()
                                    },
                                )
                            }
                            entry<Screen.Ambient> { AmbientScreen(hub) }
                            entry<Screen.Equalizer> { EqualizerScreen(hub) }
                            entry<Screen.Connection> { ConnectionScreen(hub) }
                        },
                    )
                    val phoneText = OpenOnPhoneDialogDefaults.text
                    val phoneStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
                    OpenOnPhoneDialog(
                        visible = openOnPhone,
                        onDismissRequest = { openOnPhone = false },
                        curvedText = {
                            openOnPhoneDialogCurvedText(phoneText, phoneStyle)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { hub.refreshPhone() }
    }

    /** Opens Hush on the phone (its MainActivity handles hush://open) for the settings the watch doesn't carry. */
    private fun openPhoneApp() {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("hush://open"))
        runCatching { RemoteActivityHelper(this).startRemoteActivity(intent) }
            .onFailure { Log.w("Hush", "open on phone failed", it) }
    }
}
