package com.fly8.clone.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

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
        var finished = false
        val handler = Handler(Looper.getMainLooper())

        fun finish(listener: NsdManager.DiscoveryListener?) {
            if (finished) return
            finished = true
            runCatching {
                listener?.let { manager.stopServiceDiscovery(it) }
            }
            callback(found.values.toList())
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                finish(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                finish(this)
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                finish(this)
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress.orEmpty()
                        if (host.isNotBlank()) {
                            found[serviceInfo.serviceName + ":" + serviceInfo.port] = serviceInfo
                        }
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val host = service.host?.hostAddress.orEmpty()
                val key = service.serviceName + ":" + service.port
                found.remove(key)
            }
        }

        manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

        handler.postDelayed({
            finish(listener)
        }, timeoutMs)
    }
}
