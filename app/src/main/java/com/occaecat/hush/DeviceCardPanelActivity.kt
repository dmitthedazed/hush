package com.occaecat.hush

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.occaecat.hush.ui.HeadphonesPanel
import com.occaecat.hush.ui.HeadsetAlertDialog
import com.occaecat.hush.ui.theme.HushTheme

/**
 * Target of `com.oplus.mydevices.ACTION_DEVICE_DETAILED_PANEL` / `_PAGE` and of the Bluetooth settings
 * "Headphone settings" row: a translucent activity showing [HeadphonesPanel] over the system UI.
 */
class DeviceCardPanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm = HeadphonesController.get(this)
        vm.connectSaved()
        setContent {
            HushTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                state.alert?.let { (type, actionType) -> HeadsetAlertDialog(type, actionType, vm::answerAlert) }
                HeadphonesPanel(
                    state = state,
                    vm = vm,
                    onDismiss = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }
}
