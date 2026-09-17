package com.wifishare.app.transfer

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class FileMeta(
    val name: String,
    val size: Long,
    val mime: String,
)

data class PairingInfo(
    val ssid: String,
    val password: String,
    val host: String,
    val port: Int,
    val session: String,
) {
    fun toQr(): String = JSONObject()
        .put("v", 1)
        .put("app", "wifishare")
        .put("ssid", ssid)
        .put("pwd", password)
        .put("host", host)
        .put("port", port)
        .put("room", session)
        .toString()

    companion object {
        fun parse(raw: String): PairingInfo? {
            return try {
                val json = JSONObject(raw.trim())
                if (json.optInt("v") != 1) return null
                PairingInfo(
                    ssid = json.getString("ssid"),
                    password = json.getString("pwd"),
                    host = json.getString("host"),
                    port = json.getInt("port"),
                    session = json.optString("room"),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

object Protocol {
    const val MAGIC = 0x57465331 // "WFS1"
    const val PORT = 18988
    const val CHUNK = 64 * 1024

    fun writeManifest(out: DataOutputStream, files: List<FileMeta>) {
        val array = JSONArray()
        files.forEach { file ->
            array.put(
                JSONObject()
                    .put("name", file.name)
                    .put("size", file.size)
                    .put("mime", file.mime),
            )
        }
        val bytes = JSONObject().put("files", array).toString().toByteArray(Charsets.UTF_8)
        out.writeInt(MAGIC)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun readManifest(input: DataInputStream): List<FileMeta> {
        val magic = input.readInt()
        require(magic == MAGIC) { "Unexpected protocol header" }
        val size = input.readInt()
        require(size in 1..1_000_000) { "Invalid header" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        val array = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("files")
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    FileMeta(
                        name = obj.getString("name"),
                        size = obj.getLong("size"),
                        mime = obj.optString("mime", "application/octet-stream"),
                    ),
                )
            }
        }
    }

    fun copy(
        input: InputStream,
        output: OutputStream,
        total: Long,
        onProgress: (Long) -> Unit,
    ) {
        val buffer = ByteArray(CHUNK)
        var copied = 0L
        while (copied < total) {
            val want = minOf(buffer.size.toLong(), total - copied).toInt()
            val read = input.read(buffer, 0, want)
            if (read <= 0) throw IllegalStateException("Connection closed during transfer")
            output.write(buffer, 0, read)
            copied += read
            onProgress(copied)
        }
        output.flush()
    }

    fun safeName(name: String): String {
        val base = File(name).name.replace(Regex("[\\\\/]+"), "_")
        return base.ifBlank { "file.bin" }
    }
}
