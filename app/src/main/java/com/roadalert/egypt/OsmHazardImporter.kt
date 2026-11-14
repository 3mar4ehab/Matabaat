package com.roadalert.egypt

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Imports Egypt road hazards in small geographic tiles to avoid oversized Overpass requests. */
class OsmHazardImporter(context: Context) {
    private val appContext = context.applicationContext
    private val store = HazardStore(appContext)
    private val prefs = appContext.getSharedPreferences("road_alert_sources", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    fun importIfDue(force: Boolean = false, onComplete: ((Int, Boolean) -> Unit)? = null) {
        if (!force && System.currentTimeMillis() - prefs.getLong("osm_last_success", 0L) < REFRESH_INTERVAL_MS) {
            onComplete?.invoke(0, true)
            return
        }
        executor.execute {
            var imported = 0
            var failedTiles = 0
            val known = store.all().mapNotNull { h ->
                if (h.source == HazardSource.OPENSTREETMAP && h.sourceId.isNotBlank()) "${h.type}:${h.sourceId}" else null
            }.toHashSet()
            for (tile in TILES) {
                var loaded = false
                for (endpoint in OVERPASS_URLS) {
                    try {
                        val query = """
                            [out:json][timeout:55];
                            (
                              nwr["highway"="speed_camera"](${tile});
                              nwr["traffic_calming"~"^(bump|table|cushion|hump)$"](${tile});
                            );
                            out center tags;
                        """.trimIndent()
                        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 20000
                            readTimeout = 70000
                            doOutput = true
                            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                            setRequestProperty("User-Agent", "MatabaatAndroid/0.07 (OpenStreetMap data importer)")
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
                                    lat = item.optDouble("lat", Double.NaN)
                                    lon = item.optDouble("lon", Double.NaN)
                                } else {
                                    val center = item.optJSONObject("center") ?: continue
                                    lat = center.optDouble("lat", Double.NaN)
                                    lon = center.optDouble("lon", Double.NaN)
                                }
                                if (!lat.isFinite() || !lon.isFinite() || lat !in 22.0..32.0 || lon !in 24.0..37.0) continue
                                val hazard = Hazard(lat, lon, type, System.currentTimeMillis(),
                                    HazardSource.OPENSTREETMAP, item.optString("type", "node") + "/" + item.optLong("id", 0L))
                                if (known.add("${hazard.type}:${hazard.sourceId}")) {
                                    store.addIfMissing(hazard)
                                    imported++
                                }
                            }
                            loaded = true
                        } finally { connection.disconnect() }
                        if (loaded) break
                    } catch (error: Exception) {
                        Log.w("MatabaatOSM", "Tile $tile failed on $endpoint", error)
                    }
                }
                if (!loaded) failedTiles++
            }
            val success = failedTiles == 0
            if (success) prefs.edit().putLong("osm_last_success", System.currentTimeMillis()).apply()
            onComplete?.invoke(imported, success)
        }
    }

    companion object {
        private val TILES = listOf(
            "22.0,24.0,25.5,28.3", "22.0,28.3,25.5,32.7", "22.0,32.7,25.5,37.0",
            "25.5,24.0,28.3,28.3", "25.5,28.3,28.3,32.7", "25.5,32.7,28.3,37.0",
            "28.3,24.0,32.0,28.3", "28.3,28.3,32.0,32.7", "28.3,32.7,32.0,37.0"
        )
        private val OVERPASS_URLS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
        private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
