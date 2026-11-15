package com.roadalert.egypt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local cache for user reports and imported OpenStreetMap hazards. */
class HazardStore(context: Context) {
    private val prefs = context.getSharedPreferences("road_alert", Context.MODE_PRIVATE)

    fun all(): List<Hazard> {
        val raw = prefs.getString("hazards", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val source = runCatching { HazardSource.valueOf(o.optString("source", "USER")) }.getOrDefault(HazardSource.USER)
                Hazard(o.getDouble("lat"), o.getDouble("lon"), HazardType.valueOf(o.getString("type")),
                    o.optLong("createdAt"), source, o.optString("sourceId", ""), o.optString("regionId", ""))
            }.getOrNull()
        }
    }

    @Synchronized fun add(hazard: Hazard) { save((all() + hazard).takeLast(20000)) }

    @Synchronized
    fun addIfMissing(hazard: Hazard) {
        val current = all()
        val duplicate = current.any {
            (hazard.sourceId.isNotBlank() && it.source == hazard.source && it.sourceId == hazard.sourceId) ||
                (it.type == hazard.type && kotlin.math.abs(it.latitude - hazard.latitude) < 0.00008 &&
                    kotlin.math.abs(it.longitude - hazard.longitude) < 0.00008)
        }
        if (!duplicate) save((current + hazard).takeLast(20000))
    }

    @Synchronized
    fun replaceRegion(regionId: String, hazards: List<Hazard>) {
        val retained = all().filterNot { it.regionId == regionId }
        save((retained + hazards.map { it.copy(regionId = regionId) }).takeLast(20000))
    }

    @Synchronized
    fun removeRegion(regionId: String) {
        save(all().filterNot { it.regionId == regionId })
    }

    fun forRegion(regionId: String): List<Hazard> = all().filter { it.regionId == regionId }

    private fun save(hazards: List<Hazard>) {
        val array = JSONArray()
        hazards.forEach { h ->
            array.put(JSONObject().put("lat", h.latitude).put("lon", h.longitude)
                .put("type", h.type.name).put("createdAt", h.createdAt)
                .put("source", h.source.name).put("sourceId", h.sourceId)
                .put("regionId", h.regionId))
        }
        prefs.edit().putString("hazards", array.toString()).apply()
    }
}
