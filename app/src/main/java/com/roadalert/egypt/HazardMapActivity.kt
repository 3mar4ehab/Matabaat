package com.roadalert.egypt

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

class HazardMapActivity : Activity(), MapEventsReceiver {
    private lateinit var map: MapView
    private var selected: GeoPoint? = null
    private var picking = false
    private var selectedMarker: Marker? = null
    private var confirmButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        picking = intent.getBooleanExtra(EXTRA_PICK_LOCATION, false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val title = TextView(this).apply {
            text = if (picking) "حدد مكان الخطر بالضغط على الخريطة" else "خريطة مواقع المخاطر"
            textSize = 17f; setTextColor(Color.rgb(12,27,54)); gravity = Gravity.CENTER
            setPadding(12, 18, 12, 18)
        }
        root.addView(title)
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
            controller.setZoom(6.0); controller.setCenter(GeoPoint(26.8, 30.8))
        }
        map.overlays.add(MapEventsOverlay(this))
        root.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        if (picking) {
            confirmButton = Button(this).apply {
                text = "تأكيد مكان البلاغ"; isAllCaps = false; isEnabled = false
                setOnClickListener {
                    val p = selected ?: return@setOnClickListener
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_LAT, p.latitude).putExtra(EXTRA_LON, p.longitude))
                    finish()
                }
            }
            root.addView(confirmButton, LinearLayout.LayoutParams(-1, -2))
            val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            val current = runCatching {
                lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            }.getOrNull()
            if (current != null) map.controller.setCenter(GeoPoint(current.latitude, current.longitude))
        } else {
            val type = runCatching { HazardType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "") }.getOrNull()
            val regionId = intent.getStringExtra(EXTRA_REGION_ID)
            val hazards = HazardStore(this).all().filter { (type == null || it.type == type) && (regionId.isNullOrBlank() || it.regionId == regionId) }
            hazards.forEach { h ->
                val marker = Marker(map).apply {
                    position = GeoPoint(h.latitude, h.longitude)
                    setTitle(when (h.type) {
                        HazardType.SPEED_CAMERA -> "رادار"
                        HazardType.SPEED_BUMP -> "مطب"
                        HazardType.POTHOLE -> "حفرة"
                    })
                    snippet = "الموقع: %.5f, %.5f".format(h.latitude, h.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(marker)
            }
            if (hazards.isNotEmpty()) {
                map.controller.setZoom(12.0)
                map.controller.setCenter(GeoPoint(hazards.first().latitude, hazards.first().longitude))
            }
            title.text = (when (type) {
                HazardType.SPEED_CAMERA -> "مواقع الرادارات"
                HazardType.SPEED_BUMP -> "مواقع المطبات"
                HazardType.POTHOLE -> "مواقع الحفر"
                else -> "كل مواقع المخاطر"
            }) + " • ${hazards.size}"
            if (hazards.isEmpty()) Toast.makeText(this, "لا توجد بلاغات محفوظة من هذا النوع حتى الآن", Toast.LENGTH_LONG).show()
        }
        setContentView(root)
    }

    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
        if (picking && p != null) {
            selected = p
            selectedMarker?.let { map.overlays.remove(it) }
            selectedMarker = Marker(map).apply {
                position = p; title = "موقع البلاغ المحدد"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(selectedMarker)
            confirmButton?.isEnabled = true
            map.invalidate()
        }
        return true
    }
    override fun longPressHelper(p: GeoPoint?): Boolean = false
    override fun onResume() { super.onResume(); if (::map.isInitialized) map.onResume() }
    override fun onPause() { if (::map.isInitialized) map.onPause(); super.onPause() }

    companion object {
        const val EXTRA_TYPE = "hazard_type"
        const val EXTRA_REGION_ID = "region_id"
        const val EXTRA_PICK_LOCATION = "pick_location"
        const val EXTRA_LAT = "picked_latitude"
        const val EXTRA_LON = "picked_longitude"
    }
}
