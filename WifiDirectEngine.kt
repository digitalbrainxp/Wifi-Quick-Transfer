package com.wifishare.app.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import com.wifishare.app.transfer.PairingInfo
import com.wifishare.app.transfer.Protocol
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real Wi-Fi Direct (WifiP2pManager) group owner + client join.
 * Bluetooth is never used. File bytes later move over a TCP socket on this link.
 */
class WifiDirectEngine(private val context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context.applicationContext, Looper.getMainLooper(), null)
    private val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isSupported: Boolean
        get() = manager != null &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    fun register(receiver: BroadcastReceiver) {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun createGroup(): PairingInfo {
        val mgr = manager ?: throw IllegalStateException("Wi-Fi Direct is not available on this device.")
        val ch = channel ?: throw IllegalStateException("Wi-Fi Direct is not available on this device.")
        runCatching { awaitAction { listener -> mgr.removeGroup(ch, listener) } }
        awaitAction { listener -> mgr.createGroup(ch, listener) }
        val group = awaitGroup(mgr, ch) ?: throw IllegalStateException("Wi-Fi Direct group was not created.")
        val info = awaitInfo(mgr, ch)
        val host = info.groupOwnerAddress?.hostAddress
            ?: throw IllegalStateException("No group-owner address yet. Try again.")
        val ssid = group.networkName
        val pwd = group.passphrase
        if (ssid.isNullOrBlank() || pwd.isNullOrBlank()) {
            throw IllegalStateException("This device created a group but did not expose the passphrase.")
        }
        return PairingInfo(
            ssid = ssid,
            password = pwd,
            host = host,
            port = Protocol.PORT,
            session = "wfs${System.currentTimeMillis().toString(36)}",
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun joinGroup(pairing: PairingInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            joinBySpecifier(pairing)
        } else {
            joinLegacy(pairing)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun joinBySpecifier(pairing: PairingInfo) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(pairing.ssid)
            .setWpa2Passphrase(pairing.password)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        suspendCancellableCoroutine { cont ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivity.bindProcessToNetwork(network)
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onUnavailable() {
                    if (cont.isActive) cont.resumeWithException(
                        IllegalStateException("Could not join the sender’s Wi-Fi Direct network."),
                    )
                }
            }
            connectivity.requestNetwork(request, callback)
            cont.invokeOnCancellation {
                connectivity.bindProcessToNetwork(null)
                connectivity.unregisterNetworkCallback(callback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun joinLegacy(pairing: PairingInfo) {
        val mgr = manager ?: throw IllegalStateException("Wi-Fi Direct is not available.")
        val ch = channel ?: throw IllegalStateException("Wi-Fi Direct is not available.")
        val config = WifiP2pConfig().apply {
            deviceAddress = pairing.session
            groupOwnerIntent = 0
        }
        awaitAction { listener -> mgr.connect(ch, config, listener) }
    }

    @SuppressLint("MissingPermission")
    suspend fun shutdown() {
        connectivity.bindProcessToNetwork(null)
        val mgr = manager ?: return
        val ch = channel ?: return
        runCatching { awaitAction { listener -> mgr.removeGroup(ch, listener) } }
        runCatching { awaitAction { listener -> mgr.cancelConnect(ch, listener) } }
    }

    private suspend fun awaitAction(block: (WifiP2pManager.ActionListener) -> Unit) {
        suspendCancellableCoroutine { cont ->
            block(object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFailure(reason: Int) {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("Wi-Fi Direct error ($reason)."))
                    }
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitGroup(mgr: WifiP2pManager, ch: WifiP2pManager.Channel): WifiP2pGroup? {
        repeat(20) {
            val group = suspendCancellableCoroutine { cont ->
                mgr.requestGroupInfo(ch) { info -> if (cont.isActive) cont.resume(info) }
            }
            if (group != null) return group
            Thread.sleep(150)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitInfo(mgr: WifiP2pManager, ch: WifiP2pManager.Channel): WifiP2pInfo {
        repeat(20) {
            val info = suspendCancellableCoroutine { cont ->
                mgr.requestConnectionInfo(ch) { value -> if (cont.isActive) cont.resume(value) }
            }
            if (info.groupFormed && info.groupOwnerAddress != null) return info
            Thread.sleep(150)
        }
        throw IllegalStateException("Wi-Fi Direct connection info was not ready.")
    }

    companion object {
        fun connectionIntent(intent: Intent): WifiP2pInfo? {
            return if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
            }
        }
    }
}
