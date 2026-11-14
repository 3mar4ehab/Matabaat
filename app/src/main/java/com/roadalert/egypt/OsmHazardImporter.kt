package com.roadalert.egypt

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.floor

/** Downloads only the map tile containing the device's current location. */
class OsmHazardImporter(context: Context) {
    private val appContext = context.applicationContext
    private val store = HazardStore(appContext)
    private val prefs = appContext.getSharedPreferences("road_alert_sources", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    fun importForLocation(latitude: Double, longitude: Double, force: Boolean = false, onComplete: ((Int, Boolean) -> Unit)? = null) {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            onComplete?.invoke(0, false); return
        }
        val south = floor(latitude / TILE_DEGREES) * TILE_DEGREES
        val west = floor(longitude / TILE_DEGREES) * TILE_DEGREES
        val north = south + TILE_DEGREES
        val east = west + TILE_DEGREES
        val tileKey = "${south}_${west}"
        val prefKey = "tile_${tileKey}_success"
        if (!force && System.currentTimeMillis() - prefs.getLong(prefKey, 0L) < REFRESH_INTERVAL_MS) {
            onComplete?.invoke(0, true); return
        }
        executor.execute {
            var imported = 0
            var success = false
            val known = store.all().mapNotNull { h ->
                if (h.source == HazardSource.OPENSTREETMAP && h.sourceId.isNotBlank()) "${h.type}:${h.sourceId}" else null
            }.toHashSet()
            for (endpoint in OVERPASS_URLS) {
                try {
                    val query = """
                        [out:json][timeout:45];
                        (
                          nwr["highway"="speed_camera"](${south},${west},${north},${east});
                          nwr["traffic_calming"~"^(bump|table|cushion|hump)$"](${south},${west},${north},${east});
                        );
                        out center tags;
                    """.trimIndent()
                    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        setRequestProperty("User-Agent", "MatabaatAndroid/0.08 (OpenStreetMap data importer)")
                    }
                    try {
                        val body = "data=" + URLEncoder.encode(query, "UTF-8")
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        if (connection.responseCode !in 200..299) error("Overpass HTTP " + connection.responseCode)
                        val elements = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                            .optJSONArray("elements") ?: error("Invalid Overpass response")
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
                                lat = item.optDouble("lat", Double.NaN); lon = item.optDouble("lon", Double.NaN)
                            } else {
                                val center = item.optJSONObject("center") ?: continue
                                lat = center.optDouble("lat", Double.NaN); lon = center.optDouble("lon", Double.NaN)
                            }
                            if (!lat.isFinite() || !lon.isFinite()) continue
                            val hazard = Hazard(lat, lon, type, System.currentTimeMillis(),
                                HazardSource.OPENSTREETMAP, item.optString("type", "node") + "/" + item.optLong("id", 0L))
                            if (known.add("${hazard.type}:${hazard.sourceId}")) {
                                store.addIfMissing(hazard); imported++
                            }
                        }
                        success = true
                    } finally { connection.disconnect() }
                    if (success) break
                } catch (error: Exception) {
                    Log.w("MatabaatOSM", "Tile $tileKey failed on $endpoint", error)
                }
            }
            if (success) prefs.edit().putLong(prefKey, System.currentTimeMillis()).apply()
            onComplete?.invoke(imported, success)
        }
    }

    companion object {
        private const val TILE_DEGREES = 0.25
        private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        private val OVERPASS_URLS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
    }
}
