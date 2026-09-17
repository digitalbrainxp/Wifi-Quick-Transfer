package com.wifishare.app.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

data class TransferProgress(
    val fileName: String,
    val fileIndex: Int,
    val fileCount: Int,
    val fileCopied: Long,
    val fileSize: Long,
    val totalCopied: Long,
    val totalSize: Long,
)

class TransferServer {
    private var server: ServerSocket? = null

    fun start(): ServerSocket {
        val socket = ServerSocket(Protocol.PORT, 1, InetAddress.getByName("0.0.0.0"))
        socket.soTimeout = 180_000
        server = socket
        return socket
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    fun send(context: Context, uris: List<Uri>, onProgress: (TransferProgress) -> Unit) {
        val socket = server ?: throw IllegalStateException("Server is not listening")
        val client = try {
            socket.accept()
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("No receiver joined in time. Ask them to scan the QR again.")
        }
        client.soTimeout = 30_000
        client.tcpNoDelay = true
        client.use { conn ->
            val files = uris.map { uri ->
                val name = queryName(context, uri)
                val size = querySize(context, uri)
                FileMeta(name = name, size = size, mime = context.contentResolver.getType(uri) ?: "application/octet-stream")
            }
            val total = files.sumOf { it.size }
            val out = DataOutputStream(conn.getOutputStream())
            Protocol.writeManifest(out, files)
            var copiedTotal = 0L
            uris.forEachIndexed { index, uri ->
                val meta = files[index]
                context.contentResolver.openInputStream(uri)?.use { input ->
                    var copied = 0L
                    val buffer = ByteArray(Protocol.CHUNK)
                    while (copied < meta.size) {
                        val want = minOf(buffer.size.toLong(), meta.size - copied).toInt()
                        val read = input.read(buffer, 0, want)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        copied += read
                        copiedTotal += read
                        onProgress(
                            TransferProgress(
                                fileName = meta.name,
                                fileIndex = index,
                                fileCount = files.size,
                                fileCopied = copied,
                                fileSize = meta.size,
                                totalCopied = copiedTotal,
                                totalSize = total,
                            ),
                        )
                    }
                } ?: throw IllegalStateException("Could not open ${meta.name}")
            }
            out.flush()
        }
    }

    private fun queryName(context: Context, uri: Uri): String {
        val fallback = uri.lastPathSegment ?: "file"
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx) ?: fallback
            }
        }
        return fallback
    }

    private fun querySize(context: Context, uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) return it.getLong(idx)
            }
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    }
}

class TransferClient {
    fun receive(
        context: Context,
        host: String,
        port: Int,
        onProgress: (TransferProgress) -> Unit,
    ): List<File> {
        Socket(host, port).use { socket ->
            socket.soTimeout = 30_000
            socket.tcpNoDelay = true
            val input = DataInputStream(socket.getInputStream())
            val files = Protocol.readManifest(input)
            val total = files.sumOf { it.size }
            var copiedTotal = 0L
            val saved = mutableListOf<File>()
            files.forEachIndexed { index, meta ->
                val dest = openDest(context, meta)
                dest.output.use { output ->
                    var copied = 0L
                    val buffer = ByteArray(Protocol.CHUNK)
                    while (copied < meta.size) {
                        val want = minOf(buffer.size.toLong(), meta.size - copied).toInt()
                        val read = input.read(buffer, 0, want)
                        if (read <= 0) throw IllegalStateException("Connection closed while receiving ${meta.name}")
                        output.write(buffer, 0, read)
                        copied += read
                        copiedTotal += read
                        onProgress(
                            TransferProgress(
                                fileName = meta.name,
                                fileIndex = index,
                                fileCount = files.size,
                                fileCopied = copied,
                                fileSize = meta.size,
                                totalCopied = copiedTotal,
                                totalSize = total,
                            ),
                        )
                    }
                    output.flush()
                }
                dest.uri?.let { context.contentResolver.notifyChange(it, null) }
                saved += dest.file
            }
            return saved
        }
    }

    private data class Dest(val file: File, val output: java.io.OutputStream, val uri: Uri?)

    private fun openDest(context: Context, meta: FileMeta): Dest {
        val name = Protocol.safeName(meta.name)
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, meta.mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WiFi Share")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create $name")
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not write $name")
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WiFi Share/$name")
            return Dest(file, PendingFinishStream(context, uri, out), uri)
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WiFi Share")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        return Dest(file, file.outputStream(), null)
    }
}

private class PendingFinishStream(
    private val context: Context,
    private val uri: Uri,
    private val inner: java.io.OutputStream,
) : java.io.OutputStream() {
    override fun write(b: Int) = inner.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = inner.write(b, off, len)
    override fun flush() = inner.flush()
    override fun close() {
        inner.close()
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }
}
