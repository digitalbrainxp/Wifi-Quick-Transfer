package com.wifishare.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifishare.app.Phase
import com.wifishare.app.UiState
import com.wifishare.app.transfer.TransferProgress

@Composable
fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("WiFi Share", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Direct device transfer", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(36.dp))
            Text("NEARBY", letterSpacing = 2.sp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            Spacer(Modifier.height(8.dp))
            Text("Send a file\nwithout the cloud.", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Pair with a QR code, then transfer over Wi-Fi Direct. No Bluetooth. No internet.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                fontSize = 14.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BigAction(
                title = "Send",
                subtitle = "Choose files and show a QR code",
                icon = Icons.Outlined.ArrowUpward,
                container = MaterialTheme.colorScheme.onBackground,
                content = MaterialTheme.colorScheme.background,
                onClick = onSend,
            )
            BigAction(
                title = "Receive",
                subtitle = "Scan the sender’s QR code",
                icon = Icons.Outlined.ArrowDownward,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                onClick = onReceive,
            )
            Text(
                "Files travel directly between devices. Nothing is uploaded.",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun BigAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(148.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(28.dp))
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 13.sp, color = content.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun WaitingScreen(state: UiState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(22.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle("Show this code", "Open Receive on the other device and scan.")
        Spacer(Modifier.height(24.dp))
        QrFrame(state.qr)
        Spacer(Modifier.height(16.dp))
        Text("PAIRING SESSION", fontSize = 11.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        Text(state.pairingCode, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(state.status, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
fun TransferScreen(state: UiState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(22.dp),
    ) {
        ScreenTitle(if (state.phase == Phase.CONNECTING) "Connecting" else "Transferring", state.status)
        Spacer(Modifier.height(20.dp))
        Icon(Icons.Outlined.Wifi, contentDescription = null)
        Spacer(Modifier.height(8.dp))
        Text("Wi-Fi Direct link", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        val progress = state.progress
        val fraction = if (progress != null && progress.totalSize > 0) {
            progress.totalCopied.toFloat() / progress.totalSize.toFloat()
        } else 0f
        Text("${(fraction * 100).toInt()}%", fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)))
        Spacer(Modifier.height(10.dp))
        if (progress != null) {
            Text(
                "${formatBytes(progress.totalCopied)} / ${formatBytes(progress.totalSize)}  ·  ${progress.fileName}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
    }
}

@Composable
fun OutcomeScreen(success: Boolean, title: String, body: String, onHome: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
        Spacer(Modifier.height(28.dp))
        if (success) {
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Done")
            }
        } else {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Try again")
            }
            TextButton(onClick = onHome) { Text("Home") }
        }
    }
}

@Composable
fun ScanScreen(onCode: (String) -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(22.dp),
    ) {
        ScreenTitle("Receive", "Point the camera at the sender’s QR code.")
        Spacer(Modifier.height(16.dp))
        QrScanner(
            onCode = onCode,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun QrFrame(bitmap: Bitmap?) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Pairing QR code",
                modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Spacer(Modifier.size(240.dp))
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

fun describeProgress(progress: TransferProgress?): String {
    if (progress == null) return ""
    return "${progress.fileIndex + 1}/${progress.fileCount}  ${formatBytes(progress.totalCopied)} / ${formatBytes(progress.totalSize)}"
}
