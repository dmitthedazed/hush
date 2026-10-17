package com.occaecat.hush.tv

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import com.occaecat.hush.tv.ui.DevicePickerScreen
import com.occaecat.hush.tv.ui.HeadsetAlertDialog
import com.occaecat.hush.tv.ui.HushTvShell
import com.occaecat.hush.tv.ui.HushTvTheme
import com.occaecat.hush.tv.ui.PermissionScreen
import com.occaecat.hush.ui.demoState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug builds: `am start -n com.occaecat.hush/.tv.MainActivity --ez demo true` shows a connected XM4
        // without hardware, for checking the layout on the TV emulator.
        val demo = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("demo", false)
        setContent {
            HushTvTheme {
                if (demo) {
                    val context = LocalContext.current
                    HushTvShell(state = demoState, vm = remember { HeadphonesController.get(context) })
                } else {
                    HushTvApp()
                }
            }
        }
    }
}

@Composable
private fun HushTvApp() {
    val context = LocalContext.current
    val vm = remember { HeadphonesController.get(context) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        PermissionScreen(onGrant = { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) })
        return
    }

    LaunchedEffect(Unit) {
        vm.refreshPaired()
        vm.autoConnect()
    }
    val state by vm.state.collectAsStateWithLifecycle()

    state.alert?.let { (type, actionType) -> HeadsetAlertDialog(type, actionType, vm::answerAlert) }

    if (state.address == null || state.phase == Phase.IDLE) {
        DevicePickerScreen(state = state, onRefresh = vm::refreshPaired, onPick = vm::pick)
    } else {
        HushTvShell(state = state, vm = vm)
    }
}
