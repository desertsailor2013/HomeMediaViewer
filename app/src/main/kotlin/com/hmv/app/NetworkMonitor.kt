package com.hmv.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * 网络状态监听器。
 *
 * 监听网络连接/断开事件，提供回调通知。
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var listener: NetworkListener? = null
    private var isRegistered = false

    interface NetworkListener {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }

    fun startListening(listener: NetworkListener) {
        this.listener = listener

        // 检查当前网络状态
        val currentNetwork = connectivityManager.activeNetwork
        val currentCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
        val isCurrentlyConnected = currentCapabilities?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) == true

        if (!isCurrentlyConnected) {
            listener.onNetworkLost()
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                listener.onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                listener.onNetworkLost()
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            isRegistered = true
        } catch (e: Exception) {
            // 忽略注册失败
        }
    }

    fun stopListening() {
        networkCallback?.let {
            if (isRegistered) {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                } catch (e: Exception) {
                    // 忽略注销失败
                }
            }
        }
        networkCallback = null
        listener = null
        isRegistered = false
    }

    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
