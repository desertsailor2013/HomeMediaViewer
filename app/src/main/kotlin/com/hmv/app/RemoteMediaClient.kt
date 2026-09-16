package com.hmv.app

import com.hmv.server.MediaItem
import org.json.JSONArray
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
            try {
                val url = URL("${device.baseUrl}/media")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val items = parseMediaList(json)
                    callback(Result.success(items))
                } else {
                    callback(Result.failure(Exception("HTTP ${conn.responseCode}")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }.start()
    }

    private fun parseMediaList(json: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            items.add(
                MediaItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    mimeType = obj.getString("mimeType"),
                    size = obj.getLong("size"),
                    relativePath = obj.getString("path")
                )
            )
        }
        return items
    }

    fun getMediaUrl(device: RemoteDevice, mediaId: String): String {
        return "${device.baseUrl}/media/$mediaId"
    }
}
