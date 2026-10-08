package com.fly8.clone.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class WirelessDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    companion object {
        const val ADB_CONNECT_SERVICE = "_adb-tls-connect._tcp."
        const val ADB_PAIRING_SERVICE = "_adb-tls-pairing._tcp."
    }

    fun discover(
        serviceType: String,
        timeoutMs: Long = 7_000L,
        callback: (List<NsdServiceInfo>) -> Unit
    ) {
        val manager = nsd ?: run {
            callback(emptyList())
            return
        }

        val found = LinkedHashMap<String, NsdServiceInfo>()
        var listener: NsdManager.DiscoveryListener? = null

        listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                callback(emptyList())
                runCatching { manager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                callback(found.values.toList())
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val key = serviceInfo.host.hostAddress.orEmpty() + ":" + serviceInfo.port
                        found[key] = serviceInfo
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val key = service.serviceName
                found.remove(key)
            }
        }

        manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { manager.stopServiceDiscovery(listener) }
        }, timeoutMs)
    }
}
