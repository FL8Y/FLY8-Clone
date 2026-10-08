package com.fly8.clone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CloneEntry(val packageName: String, val label: String, val userId: Int)

class CloneStore(context: Context) {
    private val prefs = context.getSharedPreferences("fly8_clones", Context.MODE_PRIVATE)

    fun load(): MutableList<CloneEntry> {
        val a = JSONArray(prefs.getString("items", "[]"))
        val list = mutableListOf<CloneEntry>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            list += CloneEntry(o.getString("packageName"), o.getString("label"), o.getInt("userId"))
        }
        return list
    }

    fun save(list: List<CloneEntry>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().apply {
                put("packageName", it.packageName)
                put("label", it.label)
                put("userId", it.userId)
            })
        }
        prefs.edit().putString("items", a.toString()).apply()
    }
}
