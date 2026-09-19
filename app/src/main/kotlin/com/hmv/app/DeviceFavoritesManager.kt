package com.hmv.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 管理收藏设备列表。
 *
 * 支持：
 * - 收藏/取消收藏设备
 * - 为设备设置自定义别名
 * - 启动时优先加载收藏设备
 */
class DeviceFavoritesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class FavoriteDevice(
        val name: String,
        val host: String,
        val port: Int,
        val alias: String = ""
    ) {
        val displayName: String get() = alias.ifEmpty { name }
    }

    fun getFavorites(): List<FavoriteDevice> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<FavoriteDevice>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    FavoriteDevice(
                        name = obj.getString("name"),
                        host = obj.getString("host"),
                        port = obj.getInt("port"),
                        alias = obj.optString("alias", "")
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isFavorite(deviceName: String): Boolean {
        return getFavorites().any { it.name == deviceName }
    }

    fun addFavorite(device: NsdHelper.DiscoveredDevice, alias: String = "") {
        val favorites = getFavorites().toMutableList()
        if (favorites.none { it.name == device.name }) {
            favorites.add(FavoriteDevice(device.name, device.host, device.port, alias))
            saveFavorites(favorites)
        } else {
            // 更新已收藏设备的 IP:Port
            updateDeviceAddress(device.name, device.host, device.port)
        }
    }

    fun updateDeviceAddress(deviceName: String, host: String, port: Int) {
        val favorites = getFavorites().toMutableList()
        val index = favorites.indexOfFirst { it.name == deviceName }
        if (index >= 0) {
            favorites[index] = favorites[index].copy(host = host, port = port)
            saveFavorites(favorites)
        }
    }

    fun removeFavorite(deviceName: String) {
        val favorites = getFavorites().toMutableList()
        favorites.removeAll { it.name == deviceName }
        saveFavorites(favorites)
    }

    fun toggleFavorite(device: NsdHelper.DiscoveredDevice): Boolean {
        return if (isFavorite(device.name)) {
            removeFavorite(device.name)
            false
        } else {
            addFavorite(device)
            true
        }
    }

    fun setAlias(deviceName: String, alias: String) {
        val favorites = getFavorites().toMutableList()
        val index = favorites.indexOfFirst { it.name == deviceName }
        if (index >= 0) {
            favorites[index] = favorites[index].copy(alias = alias)
            saveFavorites(favorites)
        }
    }

    fun getAlias(deviceName: String): String {
        return getFavorites().firstOrNull { it.name == deviceName }?.alias ?: ""
    }

    private fun saveFavorites(favorites: List<FavoriteDevice>) {
        val array = JSONArray()
        for (device in favorites) {
            val obj = JSONObject()
            obj.put("name", device.name)
            obj.put("host", device.host)
            obj.put("port", device.port)
            obj.put("alias", device.alias)
            array.put(obj)
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "hmv_device_favorites"
        private const val KEY_FAVORITES = "favorites"
    }
}
