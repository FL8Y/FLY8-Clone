package com.fly8.clone

import android.content.Context
import org.json.JSONArray

class ActivityLogStore(context: Context) {
    private val prefs = context.getSharedPreferences("fly8_activity", Context.MODE_PRIVATE)

    fun add(message: String) {
        val current = load().toMutableList()
        current.add(0, System.currentTimeMillis().toString() + "|" + message)
        val array = JSONArray()
        current.take(100).forEach { array.put(it) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    fun load(): List<String> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        buildList {
            for (i in 0 until array.length()) add(array.getString(i))
        }
    }.getOrElse { emptyList() }

    fun clear() {
        prefs.edit().remove("items").apply()
    }
}
