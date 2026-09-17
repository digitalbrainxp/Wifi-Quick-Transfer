package com.wifishare.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.wifishare.app.ui.HomeScreen
import com.wifishare.app.ui.OutcomeScreen
import com.wifishare.app.ui.ScanScreen
import com.wifishare.app.ui.TransferScreen
import com.wifishare.app.ui.WaitingScreen
import com.wifishare.app.ui.WifiShareTheme
import com.wifishare.app.wifi.WifiDirectEngine

class MainActivity : ComponentActivity() {
    private val vm: ShareViewModel by viewModels()
    private val engine by lazy { WifiDirectEngine(this) }

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsState()
            val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
            val pick = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments(),
            ) { uris ->
                if (uris.isNotEmpty()) vm.startSend(uris)
            }
            val permission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                pending.value?.invoke()
                pending.value = null
            }

            fun withPermissions(includeCamera: Boolean, action: () -> Unit) {
                val needed = missingPermissions(includeCamera)
                if (needed.isEmpty()) action()
                else {
                    pending.value = action
                    permission.launch(needed)
                }
            }

            WifiShareTheme {
                when (state.phase) {
                    Phase.HOME, Phase.PICK -> HomeScreen(
                        onSend = {
                            vm.onSend()
                            withPermissions(false) { pick.launch(arrayOf("*/*")) }
                        },
                        onReceive = {
                            withPermissions(true) { vm.onReceive() }
                        },
                    )
                    Phase.WAITING -> WaitingScreen(state, onCancel = vm::home)
                    Phase.SCAN -> ScanScreen(onCode = vm::startReceive, onCancel = vm::home)
                    Phase.CONNECTING, Phase.TRANSFER -> TransferScreen(state, onCancel = vm::home)
                    Phase.SUCCESS -> OutcomeScreen(
                        success = true,
                        title = if (state.role == Role.SEND) "Sent" else "Received",
                        body = state.status,
                        onHome = vm::home,
                        onRetry = vm::retry,
                    )
                    Phase.ERROR -> OutcomeScreen(
                        success = false,
                        title = "Couldn’t finish",
                        body = state.error ?: "The Wi-Fi Direct link dropped.",
                        onHome = vm::home,
                        onRetry = vm::retry,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        engine.register(p2pReceiver)
    }

    override fun onStop() {
        engine.unregister(p2pReceiver)
        super.onStop()
    }

    private fun missingPermissions(includeCamera: Boolean): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.READ_MEDIA_VIDEO
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (includeCamera) list += Manifest.permission.CAMERA
        return list.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
}
