package com.roadalert.egypt

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Imports Egypt road hazards from OpenStreetMap via Overpass, off the UI thread. */
class OsmHazardImporter(context: Context) {
    private val appContext = context.applicationContext
    private val store = HazardStore(appContext)
    private val prefs = appContext.getSharedPreferences("road_alert_sources", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    fun importIfDue(onComplete: ((Int, Boolean) -> Unit)? = null) {
        if (System.currentTimeMillis() - prefs.getLong("osm_last_success", 0L) < REFRESH_INTERVAL_MS) {
            onComplete?.invoke(0, true)
            return
        }
        executor.execute {
            var imported = 0
            var success = false
            try {
                val query = """
                    [out:json][timeout:180];
                    area["ISO3166-1"="EG"][admin_level=2]->.egypt;
                    (
                      nwr["highway"="speed_camera"](area.egypt);
                      nwr["traffic_calming"~"^(bump|table|cushion|hump)$"](area.egypt);
                    );
                    out center tags;
                """.trimIndent()
                val connection = (URL(OVERPASS_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 210000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("User-Agent", "MatabaatAndroid/0.05 (OpenStreetMap data importer)")
                }
                try {
                    val body = "data=" + URLEncoder.encode(query, "UTF-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    if (connection.responseCode !in 200..299) error("Overpass HTTP " + connection.responseCode)
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val elements = JSONObject(response).optJSONArray("elements") ?: error("Invalid Overpass response")
                    val existingKeys = store.all().mapNotNull { h ->
                        if (h.source == HazardSource.OPENSTREETMAP && h.sourceId.isNotBlank()) "${h.type}:${h.sourceId}" else null
                    }.toHashSet()
                    val additions = mutableListOf<Hazard>()
                    for (i in 0 until elements.length()) {
                        val item = elements.optJSONObject(i) ?: continue
                        val tags = item.optJSONObject("tags") ?: continue
                        val type = when {
                            tags.optString("highway") == "speed_camera" -> HazardType.SPEED_CAMERA
                            tags.optString("traffic_calming") in setOf("bump", "table", "cushion", "hump") -> HazardType.SPEED_BUMP
                            else -> continue
                        }
                        val lat: Double
                        val lon: Double
                        if (item.has("lat") && item.has("lon")) {
                            lat = item.optDouble("lat", Double.NaN)
                            lon = item.optDouble("lon", Double.NaN)
                        } else {
                            val center = item.optJSONObject("center") ?: continue
                            lat = center.optDouble("lat", Double.NaN)
                            lon = center.optDouble("lon", Double.NaN)
                        }
                        if (!lat.isFinite() || !lon.isFinite() || lat !in 22.0..32.0 || lon !in 24.0..37.0) continue
                        val id = item.optLong("id", 0L)
                        val kind = item.optString("type", "node")
                        val hazard = Hazard(lat, lon, type, System.currentTimeMillis(),
                            HazardSource.OPENSTREETMAP, kind + "/" + id)
                        val key = "${hazard.type}:${hazard.sourceId}"
                        if (existingKeys.add(key)) additions.add(hazard)
                    }
                    additions.forEach { store.addIfMissing(it) }
                    imported = additions.size
                    prefs.edit().putLong("osm_last_success", System.currentTimeMillis()).apply()
                    success = true
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                Log.e("MatabaatOSM", "OpenStreetMap import failed", error)
            }
            onComplete?.invoke(imported, success)
        }
    }

    companion object {
        private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
        private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
