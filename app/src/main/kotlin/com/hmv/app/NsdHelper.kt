package com.hmv.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdHelper(private val context: Context) {

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var discoveredServices = mutableListOf<NsdServiceInfo>()
    private var isDiscovering = false

    interface DeviceListener {
        fun onDeviceFound(device: DiscoveredDevice)
        fun onDeviceLost(device: DiscoveredDevice)
        fun onDiscoveryFailed(errorCode: Int)
    }

    data class DiscoveredDevice(
        val name: String,
        val host: String,
        val port: Int,
        val serviceInfo: NsdServiceInfo
    ) {
        val mediaUrl: String get() = "http://$host:$port/media"
    }

    fun registerService(port: Int, serviceName: String = "HomeMediaViewer") {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Unregister failed", e)
            }
        }
        registrationListener = null
    }

    fun startDiscovery(listener: DeviceListener) {
        if (isDiscovering) return
        isDiscovering = true
        discoveredServices.clear()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}")
                if (service.serviceType == SERVICE_TYPE) {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = serviceInfo.host?.hostAddress ?: return
                            val device = DiscoveredDevice(
                                name = serviceInfo.serviceName,
                                host = host,
                                port = serviceInfo.port,
                                serviceInfo = serviceInfo
                            )
                            synchronized(discoveredServices) {
                                discoveredServices.add(serviceInfo)
                            }
                            listener.onDeviceFound(device)
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
                synchronized(discoveredServices) {
                    discoveredServices.remove(service)
                }
                @Suppress("DEPRECATION")
                val device = DiscoveredDevice(
                    name = service.serviceName,
                    host = service.host?.hostAddress ?: "",
                    port = service.port,
                    serviceInfo = service
                )
                listener.onDeviceLost(device)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                isDiscovering = false
                listener.onDiscoveryFailed(errorCode)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Stop discovery failed", e)
            }
        }
        discoveryListener = null
        isDiscovering = false
    }

    companion object {
        private const val TAG = "NsdHelper"
        const val SERVICE_TYPE = "_hmv._tcp."
    }
}
