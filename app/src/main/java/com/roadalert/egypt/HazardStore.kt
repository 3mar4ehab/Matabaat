package com.roadalert.egypt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local cache for hazard reports received from this device and Firestore. */
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

    @Synchronized
    fun add(hazard: Hazard) {
        save((all() + hazard).takeLast(500))
    }

    @Synchronized
    fun addIfMissing(hazard: Hazard) {
        val current = all()
        val duplicate = current.any {
            it.type == hazard.type &&
                kotlin.math.abs(it.latitude - hazard.latitude) < 0.00001 &&
                kotlin.math.abs(it.longitude - hazard.longitude) < 0.00001
        }
        if (!duplicate) save((current + hazard).takeLast(500))
    }

    private fun save(hazards: List<Hazard>) {
        val array = JSONArray()
        hazards.forEach { h ->
            array.put(JSONObject().put("lat", h.latitude).put("lon", h.longitude).put("type", h.type.name).put("createdAt", h.createdAt))
        }
        prefs.edit().putString("hazards", array.toString()).apply()
    }
}
