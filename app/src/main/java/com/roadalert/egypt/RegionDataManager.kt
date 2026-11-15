package com.roadalert.egypt

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class Governorate(val id: String, val arabicName: String, val osmName: String)

object EgyptGovernorates {
    val all = listOf(
        Governorate("alexandria","الإسكندرية","Alexandria"),
        Governorate("assiut","أسيوط","Asyut"),
        Governorate("aswan","أسوان","Aswan"),
        Governorate("beheira","البحيرة","Beheira"),
        Governorate("beni_suef","بني سويف","Beni Suef"),
        Governorate("cairo","القاهرة","Cairo"),
        Governorate("dakahlia","الدقهلية","Dakahlia"),
        Governorate("damietta","دمياط","Damietta"),
        Governorate("faiyum","الفيوم","Faiyum"),
        Governorate("gharbia","الغربية","Gharbia"),
        Governorate("giza","الجيزة","Giza"),
        Governorate("ismailia","الإسماعيلية","Ismailia"),
        Governorate("kafr_elsheikh","كفر الشيخ","Kafr El Sheikh"),
        Governorate("qalyubia","القليوبية","Qalyubia"),
        Governorate("luxor","الأقصر","Luxor"),
        Governorate("matrouh","مطروح","Matrouh"),
        Governorate("minya","المنيا","Minya"),
        Governorate("menoufia","المنوفية","Monufia"),
        Governorate("new_valley","الوادي الجديد","New Valley"),
        Governorate("north_sinai","شمال سيناء","North Sinai"),
        Governorate("port_said","بورسعيد","Port Said"),
        Governorate("qena","قنا","Qena"),
        Governorate("red_sea","البحر الأحمر","Red Sea"),
        Governorate("sharqia","الشرقية","Sharqia"),
        Governorate("south_sinai","جنوب سيناء","South Sinai"),
        Governorate("suez","السويس","Suez"),
        Governorate("sohag","سوهاج","Sohag")
    )
}

/** Downloads OSM hazards by a selected governorate administrative area, independent of device location. */
class RegionDataManager(context: Context) {
    private val app = context.applicationContext
    private val store = HazardStore(app)
    private val prefs = app.getSharedPreferences("matabaat_regions", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    fun downloadedAt(region: Governorate): Long = prefs.getLong("downloaded_${region.id}", 0L)
    fun hazards(region: Governorate): List<Hazard> = store.forRegion(region.id)

    fun delete(region: Governorate) {
        store.removeRegion(region.id)
        prefs.edit().remove("downloaded_${region.id}").apply()
    }

    fun download(region: Governorate, onComplete: (Int, Boolean, String?) -> Unit) {
        executor.execute {
            var result: List<Hazard>? = null
            var errorMessage: String? = null
            for (endpoint in OVERPASS_URLS) {
                try {
                    val safeName = region.osmName.replace("\\", "\\\\").replace("\"", "\\\"")
                    val query = """
                        [out:json][timeout:90];
                        area["name"="$safeName"]["boundary"="administrative"]["admin_level"="4"]->.selectedRegion;
                        (
                          nwr["highway"="speed_camera"](area.selectedRegion);
                          nwr["traffic_calming"~"^(bump|table|cushion|hump)$"](area.selectedRegion);
                        );
                        out center tags;
                    """.trimIndent()
                    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 25000; readTimeout = 110000; doOutput = true
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        setRequestProperty("User-Agent", "MatabaatAndroid/0.10 (governorate data manager)")
                    }
                    try {
                        val body = "data=" + URLEncoder.encode(query, "UTF-8")
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        if (connection.responseCode !in 200..299) error("Overpass HTTP ${connection.responseCode}")
                        val elements = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                            .optJSONArray("elements") ?: error("Invalid Overpass response")
                        val downloaded = ArrayList<Hazard>()
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
                            downloaded.add(Hazard(lat, lon, type, System.currentTimeMillis(),
                                HazardSource.OPENSTREETMAP, item.optString("type", "node") + "/" + item.optLong("id", 0L), region.id))
                        }
                        result = downloaded
                    } finally { connection.disconnect() }
                    if (result != null) break
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "تعذر الاتصال بمصدر البيانات"
                    Log.w("MatabaatRegions", "Download ${region.id} failed at $endpoint", e)
                }
            }
            if (result != null) {
                store.replaceRegion(region.id, result!!)
                prefs.edit().putLong("downloaded_${region.id}", System.currentTimeMillis()).apply()
                onComplete(result!!.size, true, null)
            } else onComplete(0, false, errorMessage)
        }
    }

    companion object {
        private val OVERPASS_URLS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
    }
}
