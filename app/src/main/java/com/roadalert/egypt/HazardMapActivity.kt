package com.roadalert.egypt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

class HazardMapActivity : Activity(), LocationListener {
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var store: HazardStore
    private var map: org.maplibre.android.maps.MapLibreMap? = null
    private var userSource: GeoJsonSource? = null
    private var currentLocation: Location? = null
    private lateinit var search: EditText
    private lateinit var suggestions: LinearLayout
    private lateinit var destinationPanel: LinearLayout
    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var searchToken = 0
    private var followUser = true

    data class Destination(val name: String, val lat: Double, val lon: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        store = HazardStore(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        buildScreen()
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { ready ->
            map = ready
            ready.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                HazardType.values().forEach { type ->
                    val id = "hazard-" + type.name.lowercase(Locale.ROOT)
                    val features = store.all().filter { it.type == type }.map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
                    style.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(features)))
                    val color = when (type) {
                        HazardType.SPEED_CAMERA -> "#E53935"
                        HazardType.SPEED_BUMP -> "#F9A825"
                        HazardType.POTHOLE -> "#7E57C2"
                    }
                    style.addLayer(CircleLayer(id + "-layer", id).withProperties(
                        PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(color),
                        PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f)
                    ))
                }
                userSource = GeoJsonSource("matabaat-user", FeatureCollection.fromFeatures(arrayOf()))
                style.addSource(userSource!!)
                style.addLayer(CircleLayer("matabaat-user-dot", "matabaat-user").withProperties(
                    PropertyFactory.circleRadius(9f), PropertyFactory.circleColor("#1677FF"),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f)
                ))
                ready.addOnMapClickListener { tapped -> showHazardAt(tapped); true }
                lastKnownLocation()?.let { onLocationChanged(it) } ?: requestOneLocation()
            }
        }
        ensureLocationPermission()
    }

    private fun buildScreen() {
        root = FrameLayout(this)
        mapView = MapView(this)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 18)
            elevation = dp(8).toFloat()
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        search = EditText(this).apply {
            hint = "إلى أين تريد الذهاب؟"
            textSize = 16f
            singleLine = true
            background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { requestSuggestions(s.toString()) }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        top.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        suggestions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        top.addView(suggestions)
        destinationPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        top.addView(destinationPanel)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(dp(12), dp(14), dp(12), 0) })

        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(0xF2FFFFFF.toInt(), 18)
            elevation = dp(8).toFloat()
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        rail.addView(railButton("＋", "بلاغ") { showReportChoices() })
        rail.addView(railButton("◉", "التنبيهات") { startAlerts() })
        rail.addView(railButton("♫", "الصوت") { startActivity(Intent(this, AlertSettingsActivity::class.java)) })
        rail.addView(railButton("▦", "المحافظات") { startActivity(Intent(this, RegionManagerActivity::class.java)) })
        rail.addView(railButton("⚙", "المزيد") { showServices() })
        root.addView(rail, FrameLayout.LayoutParams(dp(76), -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { setMargins(0, 0, dp(10), dp(12)) })

        val locate = TextView(this).apply {
            text = "◎"; textSize = 28f; gravity = Gravity.CENTER
            setTextColor(0xFF102A43.toInt()); background = rounded(Color.WHITE, 40); elevation = dp(8).toFloat()
            setOnClickListener { followUser = true; currentLocation?.let { moveTo(it) } ?: requestOneLocation() }
        }
        root.addView(locate, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(14), 0, 0, dp(24)) })
        setContentView(root)
    }

    private fun railButton(symbol: String, label: String, action: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(3), dp(7), dp(3), dp(7)); isClickable = true; setOnClickListener { action() }
            addView(TextView(this@HazardMapActivity).apply { text = symbol; textSize = 23f; gravity = Gravity.CENTER; setTextColor(0xFF0C1B36.toInt()); typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(this@HazardMapActivity).apply { text = label; textSize = 10f; gravity = Gravity.CENTER; setTextColor(0xFF344054.toInt()) })
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 71)
        else startLocationUpdates()
    }

    private fun lastKnownLocation(): Location? = try {
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (_: SecurityException) { null }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 10f, this)
            lastKnownLocation()?.let { onLocationChanged(it) } ?: requestOneLocation()
        } catch (_: SecurityException) {}
    }

    private fun requestOneLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try { locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, Looper.getMainLooper()) } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        userSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)))
        if (followUser && map != null) {
            val bearing = if (location.hasBearing() && location.speed > 1.5f) location.bearing.toDouble() else (map?.cameraPosition?.bearing ?: 0.0)
            map?.cameraPosition = CameraPosition.Builder().target(LatLng(location.latitude, location.longitude))
                .zoom(maxOf(map?.cameraPosition?.zoom ?: 15.5, 15.5)).bearing(bearing).tilt(35.0).build()
        }
    }

    private fun moveTo(location: Location) {
        map?.cameraPosition = CameraPosition.Builder().target(LatLng(location.latitude, location.longitude))
            .zoom(16.0).bearing(if (location.hasBearing()) location.bearing.toDouble() else 0.0).tilt(35.0).build()
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun showReportChoices() {
        val labels = arrayOf("مطب", "حفرة", "رادار")
        val types = arrayOf(HazardType.SPEED_BUMP, HazardType.POTHOLE, HazardType.SPEED_CAMERA)
        android.app.AlertDialog.Builder(this).setTitle("تسجيل خطر عند موقعي الحالي")
            .setItems(labels) { _, which -> saveReport(types[which]) }.setNegativeButton("إلغاء", null).show()
    }

    private fun saveReport(type: HazardType) {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, "جاري تحديد موقعك. انتظر ظهور موقعك ثم أعد البلاغ", Toast.LENGTH_LONG).show()
            requestOneLocation()
            return
        }
        val hazard = Hazard(location.latitude, location.longitude, type)
        store.add(hazard)
        HazardType.values().forEach { t ->
            val id = "hazard-" + t.name.lowercase(Locale.ROOT)
            val features = store.all().filter { it.type == t }.map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
            map?.style?.getSourceAs<GeoJsonSource>(id)?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
        Toast.makeText(this, "تم تسجيل البلاغ في موقعك الحالي", Toast.LENGTH_SHORT).show()
        runCatching { HazardCloudSync(this).submit(hazard) { } }
    }

    private fun requestSuggestions(query: String) {
        val q = query.trim()
        val token = ++searchToken
        if (q.length < 3) { suggestions.removeAllViews(); suggestions.visibility = View.GONE; return }
        handler.postDelayed({
            io.execute {
                val results = geocode(q)
                runOnUiThread {
                    if (token != searchToken) return@runOnUiThread
                    suggestions.removeAllViews()
                    if (results.isEmpty()) {
                        suggestions.addView(TextView(this).apply { text = "لا توجد اقتراحات متاحة حاليًا"; setPadding(8, 12, 8, 12) })
                    }
                    results.forEach { place ->
                        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { selectDestination(place) } }
                        row.addView(TextView(this).apply { text = place.name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFF102A43.toInt()) })
                        val current = currentLocation
                        val detail = if (current == null) "بانتظار تحديد موقعك" else routeSummary(current, place)
                        row.addView(TextView(this).apply { text = detail; textSize = 12f; setTextColor(0xFF667085.toInt()) })
                        suggestions.addView(row)
                        suggestions.addView(View(this).apply { setBackgroundColor(0xFFE4E7EC.toInt()) }, LinearLayout.LayoutParams(-1, dp(1)))
                    }
                    suggestions.visibility = View.VISIBLE
                }
            }
        }, 500L)
    }

    private fun routeSummary(from: Location, to: Destination): String {
        return try {
            val url = URL("https://router.project-osrm.org/route/v1/driving/" +
                from.longitude + "," + from.latitude + ";" + to.lon + "," + to.lat + "?overview=false")
            val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 7000; readTimeout = 7000 }
            val json = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            connection.disconnect()
            val route = json.optJSONArray("routes")?.optJSONObject(0) ?: return "تعذر حساب مسار القيادة"
            val km = route.optDouble("distance") / 1000.0
            val mins = (route.optDouble("duration") / 60.0).toInt().coerceAtLeast(1)
            String.format(Locale.US, "%.1f كم • %d دقيقة بالسيارة", km, mins)
        } catch (_: Exception) {
            "تعذر حساب المسافة ووقت الطريق الآن"
        }
    }

    private fun geocode(query: String): List<Destination> = try {
        val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=eg&q=" + URLEncoder.encode(query, "UTF-8"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000; readTimeout = 9000; setRequestProperty("User-Agent", "Matabaat/0.14 Android")
        }
        val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        val found = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            Destination(item.optString("display_name"), item.optDouble("lat"), item.optDouble("lon"))
        }
        connection.disconnect()
        found
    } catch (_: Exception) { emptyList() }

    private fun selectDestination(place: Destination) {
        suggestions.visibility = View.GONE
        search.setText(place.name.substringBefore(","))
        destinationPanel.removeAllViews()
        destinationPanel.addView(TextView(this).apply {
            text = "الوجهة: " + place.name + "\nالملاحة التفصيلية ستفتح في Google Maps."
            textSize = 12f; setTextColor(0xFF344054.toInt())
        })
        destinationPanel.addView(Button(this).apply {
            text = "ابدأ الطريق"; isAllCaps = false
            setOnClickListener {
                val uri = Uri.parse("google.navigation:q=" + place.lat + "," + place.lon + "&mode=d")
                try { startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")) }
                catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + place.lat + "," + place.lon + "&travelmode=driving"))) }
            }
        })
        destinationPanel.visibility = View.VISIBLE
        followUser = false
        map?.cameraPosition = CameraPosition.Builder().target(LatLng(place.lat, place.lon)).zoom(13.0).build()
    }

    private fun showHazardAt(point: LatLng) {
        val nearby = store.all().mapNotNull { h ->
            val d = FloatArray(1)
            Location.distanceBetween(point.latitude, point.longitude, h.latitude, h.longitude, d)
            if (d[0] < 100f) Pair(h, d[0]) else null
        }.minByOrNull { it.second } ?: return
        val label = when (nearby.first.type) {
            HazardType.SPEED_CAMERA -> "رادار"
            HazardType.SPEED_BUMP -> "مطب"
            HazardType.POTHOLE -> "حفرة"
        }
        android.app.AlertDialog.Builder(this).setTitle(label)
            .setMessage("يبعد " + nearby.second.toInt() + " متر • " + nearby.first.source)
            .setPositiveButton("حسنًا", null).show()
    }

    private fun startAlerts() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { ensureLocationPermission(); return }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))); return
        }
        try {
            val i = Intent(this, AlertService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            Toast.makeText(this, "تم تشغيل تنبيهات الطريق", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, "تعذر تشغيل التنبيهات", Toast.LENGTH_LONG).show() }
    }

    private fun showServices() {
        val items = arrayOf("إعدادات الصوت", "إدارة بيانات المحافظات", "لوحة المسؤول", "إيقاف التنبيهات")
        android.app.AlertDialog.Builder(this).setTitle("خدمات مطبات").setItems(items) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, AlertSettingsActivity::class.java))
                1 -> startActivity(Intent(this, RegionManagerActivity::class.java))
                2 -> startActivity(Intent(this, AdminLoginActivity::class.java))
                3 -> stopService(Intent(this, AlertService::class.java))
            }
        }.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 71 && results.any { it == PackageManager.PERMISSION_GRANTED }) startLocationUpdates()
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume(); if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startLocationUpdates() }
    override fun onPause() { mapView.onPause(); runCatching { locationManager.removeUpdates(this) }; super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { runCatching { locationManager.removeUpdates(this) }; io.shutdownNow(); mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    companion object {
        const val EXTRA_TYPE = "hazard_type"
        const val EXTRA_REGION_ID = "region_id"
        const val EXTRA_PICK_LOCATION = "pick_location"
        const val EXTRA_LAT = "picked_latitude"
        const val EXTRA_LON = "picked_longitude"
    }
}
