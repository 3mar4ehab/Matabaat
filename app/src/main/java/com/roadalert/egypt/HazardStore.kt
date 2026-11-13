package com.roadalert.egypt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local MVP storage. Reports stay on this phone until cloud sync is added. */
class HazardStore(context: Context) {
    private val prefs = context.getSharedPreferences("road_alert", Context.MODE_PRIVATE)

    fun all(): List<Hazard> {
        val raw = prefs.getString("hazards", "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                Hazard(o.getDouble("lat"), o.getDouble("lon"), HazardType.valueOf(o.getString("type")), o.optLong("createdAt"))
            }.getOrNull()
        }
    }

    fun add(hazard: Hazard) {
        val array = JSONArray()
        (all() + hazard).takeLast(500).forEach { h ->
            array.put(JSONObject().put("lat", h.latitude).put("lon", h.longitude).put("type", h.type.name).put("createdAt", h.createdAt))
        }
        prefs.edit().putString("hazards", array.toString()).apply()
    }
}
