package com.fly8.clone

import android.content.Context
import com.fly8.clone.model.CloneEntry
import com.fly8.clone.model.SessionState
import org.json.JSONArray
import org.json.JSONObject

class CloneStore(context: Context) {
    private val prefs = context.getSharedPreferences("fly8_clones", Context.MODE_PRIVATE)

    fun load(): MutableList<CloneEntry> {
        return runCatching {
            val array = JSONArray(prefs.getString("items", "[]"))
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        CloneEntry(
                            packageName = o.getString("packageName"),
                            label = o.getString("label"),
                            userId = o.getInt("userId"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            recoveryEnabled = o.optBoolean("recoveryEnabled", false),
                            recoveryIntervalMinutes = o.optInt("recoveryIntervalMinutes", 15),
                            restartOnNetworkChange = o.optBoolean("restartOnNetworkChange", false),
                            restartOnPublicIpChange = o.optBoolean("restartOnPublicIpChange", false),
                            lastKnownState = runCatching {
                                SessionState.valueOf(o.optString("lastKnownState", SessionState.UNKNOWN.name))
                            }.getOrDefault(SessionState.UNKNOWN),
                            lastActionAt = o.optLong("lastActionAt", System.currentTimeMillis())
                        )
                    )
                }
            }.toMutableList()
        }.getOrElse { mutableListOf() }
    }

    fun save(list: List<CloneEntry>) {
        val array = JSONArray()
        list.forEach { clone ->
            array.put(JSONObject().apply {
                put("packageName", clone.packageName)
                put("label", clone.label)
                put("userId", clone.userId)
                put("createdAt", clone.createdAt)
                put("recoveryEnabled", clone.recoveryEnabled)
                put("recoveryIntervalMinutes", clone.recoveryIntervalMinutes)
                put("restartOnNetworkChange", clone.restartOnNetworkChange)
                put("restartOnPublicIpChange", clone.restartOnPublicIpChange)
                put("lastKnownState", clone.lastKnownState.name)
                put("lastActionAt", clone.lastActionAt)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
