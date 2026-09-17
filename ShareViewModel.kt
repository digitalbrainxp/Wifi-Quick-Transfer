package com.wifishare.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifishare.app.qr.Qr
import com.wifishare.app.transfer.PairingInfo
import com.wifishare.app.transfer.TransferClient
import com.wifishare.app.transfer.TransferProgress
import com.wifishare.app.transfer.TransferServer
import com.wifishare.app.wifi.WifiDirectEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Role { NONE, SEND, RECEIVE }

enum class Phase {
    HOME, PICK, WAITING, SCAN, CONNECTING, TRANSFER, SUCCESS, ERROR
}

data class UiState(
    val role: Role = Role.NONE,
    val phase: Phase = Phase.HOME,
    val status: String = "",
    val error: String? = null,
    val qr: Bitmap? = null,
    val pairingCode: String = "",
    val progress: TransferProgress? = null,
    val received: List<File> = emptyList(),
    val fileCount: Int = 0,
    val wifiDirect: Boolean = true,
)

class ShareViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = WifiDirectEngine(app)
    private val server = TransferServer()
    private var job: Job? = null
    private var lastUris: List<Uri> = emptyList()
    private var lastPairing: PairingInfo? = null

    private val _state = MutableStateFlow(UiState(wifiDirect = engine.isSupported))
    val state: StateFlow<UiState> = _state

    fun onSend() {
        if (!engine.isSupported) {
            _state.update {
                it.copy(
                    phase = Phase.ERROR,
                    error = "This device does not support Wi-Fi Direct.",
                    role = Role.SEND,
                )
            }
            return
        }
        _state.update { it.copy(role = Role.SEND, error = null) }
    }

    fun onReceive() {
        if (!engine.isSupported) {
            _state.update {
                it.copy(
                    phase = Phase.ERROR,
                    error = "This device does not support Wi-Fi Direct.",
                    role = Role.RECEIVE,
                )
            }
            return
        }
        _state.update { it.copy(role = Role.RECEIVE, phase = Phase.SCAN, error = null) }
    }

    fun home() {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            runCatching { server.stop() }
            runCatching { engine.shutdown() }
        }
        lastUris = emptyList()
        lastPairing = null
        _state.value = UiState(wifiDirect = engine.isSupported)
    }

    fun startSend(uris: List<Uri>) {
        if (!engine.isSupported) {
            _state.update {
                it.copy(phase = Phase.ERROR, error = "This device does not support Wi-Fi Direct.", role = Role.SEND)
            }
            return
        }
        lastUris = uris
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = Phase.WAITING,
                    status = "Starting Wi-Fi Direct…",
                    fileCount = uris.size,
                    error = null,
                )
            }
            try {
                val pairing = withContext(Dispatchers.IO) {
                    val info = engine.createGroup()
                    server.start()
                    info
                }
                lastPairing = pairing
                val bitmap = withContext(Dispatchers.Default) { Qr.bitmap(pairing.toQr()) }
                _state.update {
                    it.copy(
                        qr = bitmap,
                        pairingCode = pairing.session.uppercase(),
                        status = "Waiting for the other device to scan",
                    )
                }
                withContext(Dispatchers.IO) {
                    server.send(getApplication(), uris) { progress ->
                        _state.update {
                            it.copy(phase = Phase.TRANSFER, progress = progress, status = "Sending ${progress.fileName}")
                        }
                    }
                }
                _state.update { it.copy(phase = Phase.SUCCESS, status = "Sent") }
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = Phase.ERROR, error = e.message ?: "Send failed")
                }
            } finally {
                withContext(Dispatchers.IO) {
                    server.stop()
                }
            }
        }
    }

    fun startReceive(raw: String) {
        val pairing = PairingInfo.parse(raw)
        if (pairing == null) {
            _state.update { it.copy(phase = Phase.ERROR, error = "That QR is not a WiFi Share pairing code.") }
            return
        }
        lastPairing = pairing
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(phase = Phase.CONNECTING, status = "Joining Wi-Fi Direct…", error = null) }
            try {
                withContext(Dispatchers.IO) { engine.joinGroup(pairing) }
                _state.update { it.copy(status = "Connected — receiving files") }
                val files = withContext(Dispatchers.IO) {
                    TransferClient().receive(getApplication(), pairing.host, pairing.port) { progress ->
                        _state.update {
                            it.copy(phase = Phase.TRANSFER, progress = progress, status = "Receiving ${progress.fileName}")
                        }
                    }
                }
                _state.update { it.copy(phase = Phase.SUCCESS, received = files, status = "Saved to Downloads/WiFi Share") }
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.ERROR, error = e.message ?: "Receive failed") }
            }
        }
    }

    fun retry() {
        when (_state.value.role) {
            Role.SEND -> if (lastUris.isNotEmpty()) startSend(lastUris) else onSend()
            Role.RECEIVE -> {
                val pairing = lastPairing
                if (pairing != null) startReceive(pairing.toQr()) else onReceive()
            }
            Role.NONE -> home()
        }
    }
}
