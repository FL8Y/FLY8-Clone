package com.fly8.clone.connection

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

object ConnectionUi {
    fun show(
        activity: Activity,
        manager: ConnectionManager,
        onChanged: () -> Unit
    ) {
        val root = box(activity)
        root.addView(pair(activity, "Control setup", "Choose how FLY8 gets privileged control of the device."))

        val current = TextView(activity).apply {
            text = "Current: " + manager.statusText()
            textSize = 13f
            setTextColor(0xFFFCFDFD.toInt())
            setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), dp(activity, 10))
        }
        root.addView(current)

        ConnectionMethod.values().forEach { method ->
            root.addView(methodButton(activity, manager, method) {
                manager.setMethod(method)
                current.text = "Current: " + manager.statusText()
                onChanged()
            })
        }

        root.addView(textButton(activity, "Wireless debugging setup") {
            showWireless(activity, manager, onChanged)
        })
        root.addView(textButton(activity, "Root access check") {
            checkMethod(activity, manager, ConnectionMethod.ROOT, onChanged)
        })
        root.addView(textButton(activity, "Shizuku permission") {
            checkMethod(activity, manager, ConnectionMethod.SHIZUKU, onChanged)
        })
        AlertDialog.Builder(activity)
            .setView(root)
            .setTitle("Control setup")
            .setPositiveButton("Close", null)
            .show()
    }


    private var wirelessDialog: AlertDialog? = null

    private fun showWireless(
        activity: Activity,
        manager: ConnectionManager,
        onChanged: () -> Unit
    ) {
        val state = manager.wireless()
        val host = edit(activity, state.host, "Device address")
        val pairingPort = edit(activity, if (state.pairingPort == 0) "" else state.pairingPort.toString(), "Pairing port")
        val code = edit(activity, "", "Six-digit pairing code")
        code.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        val connectionPort = edit(activity, if (state.connectionPort == 0) "" else state.connectionPort.toString(), "Connection port")

        val root = box(activity)
        root.addView(pair(activity, "Wireless debugging", "Pair with Android's Wireless debugging service. The pairing port and connection port are different."))
        root.addView(host)
        root.addView(pairingPort)
        root.addView(code)
        root.addView(connectionPort)

        root.addView(textButton(activity, "Open Developer options") {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }.onFailure {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        })

        root.addView(textButton(activity, "Discover pairing endpoint") {
            discover(activity, WirelessDiscovery.ADB_PAIRING_SERVICE) { service ->
                host.setText(service.host.hostAddress.orEmpty())
                pairingPort.setText(service.port.toString())
            }
        })

        root.addView(textButton(activity, "Discover connection endpoint") {
            discover(activity, WirelessDiscovery.ADB_CONNECT_SERVICE) { service ->
                host.setText(service.host.hostAddress.orEmpty())
                connectionPort.setText(service.port.toString())
            }
        })

        root.addView(textButton(activity, "Pair device") {
            val h = host.text.toString().trim()
            val p = pairingPort.text.toString().toIntOrNull() ?: 0
            val c = code.text.toString().trim()
            activity.toast("Pairing…")
            Thread {
                val result = WirelessPairing.pair(activity, h, p, c)
                activity.runOnUiThread {
                    result.onSuccess {
                        val saved = WirelessPairingState(
                            host = h,
                            pairingPort = p,
                            connectionPort = connectionPort.text.toString().toIntOrNull() ?: 0,
                            paired = true
                        )
                        manager.saveWireless(saved)
                        manager.setMethod(ConnectionMethod.WIRELESS_DEBUGGING)
                        onChanged()
                        activity.toast("Pairing completed. Now set or discover the connection port.")
                    }.onFailure {
                        activity.showError("Pairing failed", it.message ?: "Android rejected the pairing request.")
                    }
                }
            }.start()
        })

        root.addView(textButton(activity, "Auto-discover & connect") {
            activity.toast("Searching for Wireless debugging…")
            Thread {
                val result = WirelessPairing.autoConnect(activity)
                activity.runOnUiThread {
                    result.onSuccess {
                        manager.setMethod(ConnectionMethod.WIRELESS_DEBUGGING)
                        manager.saveWireless(
                            manager.wireless().copy(paired = true)
                        )
                        onChanged()
                        activity.toast("Wireless debugging connected.")
                        wirelessDialog?.dismiss()
                    }.onFailure {
                        activity.showError(
                            "Automatic connection failed",
                            it.message ?: "No Wireless debugging endpoint could be discovered."
                        )
                    }
                }
            }.start()
        })

        root.addView(textButton(activity, "Test & connect") {
            val h = host.text.toString().trim()
            val p = connectionPort.text.toString().toIntOrNull() ?: 0
            activity.toast("Connecting…")
            Thread {
                val result = WirelessPairing.test(activity, h, p)
                activity.runOnUiThread {
                    result.onSuccess {
                        manager.saveWireless(
                            WirelessPairingState(
                                host = h,
                                pairingPort = pairingPort.text.toString().toIntOrNull() ?: 0,
                                connectionPort = p,
                                paired = state.paired || true
                            )
                        )
                        manager.setMethod(ConnectionMethod.WIRELESS_DEBUGGING)
                        onChanged()
                        activity.toast("Controls connected through Wireless debugging.")
                        wirelessDialog?.dismiss()
                    }.onFailure {
                        activity.showError("Connection failed", it.message ?: "Could not connect to the Wireless debugging endpoint.")
                    }
                }
            }.start()
        })

        wirelessDialog = AlertDialog.Builder(activity)
            .setView(root)
            .setTitle("Wireless debugging")
            .create()
            .also { it.show() }
    }

    private fun checkMethod(
        activity: Activity,
        manager: ConnectionManager,
        method: ConnectionMethod,
        onChanged: () -> Unit
    ) {
        manager.setMethod(method)
        Thread {
            val result = manager.checkSelected()
            activity.runOnUiThread {
                if (result.ok && method == ConnectionMethod.ROOT) {
                    manager.markRootVerified()
                }
                onChanged()
                if (result.ok) activity.toast("Connected through " + method.name.lowercase(Locale.ROOT).replace('_', ' '))
                else activity.showError("Control check failed", result.message)
            }
        }.start()
    }

    private fun discover(
        activity: Activity,
        serviceType: String,
        selected: (android.net.nsd.NsdServiceInfo) -> Unit
    ) {
        activity.toast("Searching local network…")
        WirelessDiscovery(activity).discover(serviceType) { services ->
            activity.runOnUiThread {
                if (services.isEmpty()) {
                    activity.showError("Nothing found", "No compatible Wireless debugging service was discovered. Open Wireless debugging and keep it enabled, then try again.")
                    return@runOnUiThread
                }
                val labels = services.map {
                    it.serviceName + "
" + it.host.hostAddress.orEmpty() + ":" + it.port
                }.toTypedArray()
                AlertDialog.Builder(activity)
                    .setTitle("Discovered endpoints")
                    .setItems(labels) { _, which -> selected(services[which]) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun methodButton(
        activity: Activity,
        manager: ConnectionManager,
        method: ConnectionMethod,
        selected: () -> Unit
    ) = textButton(activity, method.name.replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar { it.titlecase() }) {
        selected()
        activity.toast("Selected " + method.name.lowercase(Locale.ROOT).replace('_', ' '))
    }

    private fun textButton(activity: Activity, label: String, click: () -> Unit) =
        android.widget.Button(activity).apply {
            text = label
            setAllCaps(false)
            minHeight = dp(activity, 44)
            setOnClickListener { click() }
        }

    private fun edit(activity: Activity, value: String, hint: String) =
        EditText(activity).apply {
            setText(value)
            this.hint = hint
            setSingleLine(true)
            textSize = 14f
            setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8))
        }

    private fun pair(activity: Activity, title: String, body: String) =
        TextView(activity).apply {
            text = title + " + "\n" + body
            textSize = 16f
            setTextColor(0xFFFCFDFD.toInt())
            setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8))
        }

    private fun box(activity: Activity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 18))
    }

    private fun dp(activity: Activity, value: Int) =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun Activity.toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun Activity.showError(title: String, message: String) =
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Close", null).show()
}
