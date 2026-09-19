package com.hmv.app

import com.hmv.server.MediaItem
import org.json.JSONArray
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class RemoteMediaClient {

    data class RemoteDevice(
        val name: String,
        val host: String,
        val port: Int
    ) {
        val baseUrl: String get() = "http://$host:$port"
    }

    fun fetchMediaList(device: RemoteDevice, callback: (Result<List<MediaItem>>) -> Unit) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("${device.baseUrl}/media")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = parseMediaList(json, device)
                    callback(Result.success(items))
                } else {
                    callback(Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun parseMediaList(json: String, device: RemoteDevice): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val mediaId = obj.getString("id")
            val thumbnail = if (obj.has("thumbnail")) {
                obj.getString("thumbnail")
            } else {
                getThumbnailUrl(device, mediaId)
            }
            items.add(
                MediaItem(
                    id = mediaId,
                    title = obj.getString("title"),
                    mimeType = obj.getString("mimeType"),
                    size = obj.getLong("size"),
                    relativePath = obj.getString("path"),
                    thumbnailUri = thumbnail
                )
            )
        }
        return items
    }

    fun getMediaUrl(device: RemoteDevice, mediaId: String): String {
        return "${device.baseUrl}/media/$mediaId"
    }

    fun getThumbnailUrl(device: RemoteDevice, mediaId: String): String {
        return "${device.baseUrl}/media/$mediaId/thumbnail"
    }

    /**
     * 发送投屏指令到远程设备。
     *
     * @param device 目标设备
     * @param mediaId 媒体 ID
     * @param title 标题
     * @param position 播放位置（毫秒）
     * @param callback 结果回调
     */
    fun sendCastCommand(
        device: RemoteDevice,
        mediaId: String,
        title: String,
        position: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("${device.baseUrl}/play")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = """{"mediaId":"$mediaId","title":"$title","position":$position}"""
                conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    callback(Result.success(Unit))
                } else {
                    callback(Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
}
