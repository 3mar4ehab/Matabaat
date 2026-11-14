package com.roadalert.egypt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var store: HazardStore
    private lateinit var status: TextView
    private var cloudSync: HazardCloudSync? = null
    private var cloudReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = HazardStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFFF4F7FA.toInt())
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_matabaat)
            contentDescription = getString(R.string.app_name)
            layoutParams = LinearLayout.LayoutParams(88, 88).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = 12 }
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name); textSize = 28f
            gravity = Gravity.CENTER; setTextColor(0xFF102A43.toInt())
        }
        val subtitle = TextView(this).apply {
            text = getString(R.string.subtitle)
            textSize = 16f; gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18); setTextColor(0xFF334E68.toInt())
        }
        status = TextView(this).apply { textSize = 14f; setPadding(0, 8, 0, 18); setTextColor(0xFF334E68.toInt()) }
        val attribution = TextView(this).apply { text = getString(R.string.osm_attribution); textSize = 11f; setPadding(0, 0, 0, 14); setTextColor(0xFF627D98.toInt()) }
        root.addView(logo); root.addView(title); root.addView(subtitle); root.addView(status); root.addView(attribution)
        root.addView(actionButton(R.string.overlay) { enableOverlay() })
        root.addView(actionButton(R.string.start_alerts) { startAlerts() })
        root.addView(actionButton(R.string.stop_alerts) { stopService(Intent(this, AlertService::class.java)); Toast.makeText(this, R.string.alerts_stopped, Toast.LENGTH_SHORT).show() })
        root.addView(actionButton(R.string.quick_report) { showQuickReport() })
        root.addView(actionButton(R.string.refresh_road_data) { refreshRoadData(true) })
        root.addView(actionButton(R.string.report_camera) { report(HazardType.SPEED_CAMERA) })
        root.addView(actionButton(R.string.report_bump) { report(HazardType.SPEED_BUMP) })
        root.addView(actionButton(R.string.report_pothole) { report(HazardType.POTHOLE) })
        root.addView(actionButton(R.string.open_maps) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")))
        })
        setContentView(root)
        refreshStatus()

        // Firebase is optional for opening the app; a cloud initialization issue must not crash the UI.
        runCatching {
            HazardCloudSync(this).also { sync ->
                cloudSync = sync
                sync.start { ready -> runOnUiThread { cloudReady = ready; refreshStatus() } }
            }
        }.onFailure {
            cloudReady = false
            refreshStatus()
        }

        refreshRoadData(false)

        requestLocationPermission()
        handleQuickReportIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickReportIntent(intent)
    }

    private fun handleQuickReportIntent(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_REPORT) {
            intent.action = null
            window.decorView.post { showQuickReport() }
        }
    }

    private fun actionButton(label: Int, action: () -> Unit) = Button(this).apply {
        text = getString(label); isAllCaps = false; textSize = 15f; gravity = Gravity.CENTER
        setOnClickListener { action() }
    }

    private fun showQuickReport() {
        val labels = arrayOf(getString(R.string.camera), getString(R.string.bump), getString(R.string.pothole))
        val types = arrayOf(HazardType.SPEED_CAMERA, HazardType.SPEED_BUMP, HazardType.POTHOLE)
        AlertDialog.Builder(this)
            .setTitle(R.string.quick_report_title)
            .setItems(labels) { _, which -> report(types[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10)
        }
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, R.string.overlay_instruction, Toast.LENGTH_LONG).show()
        } else Toast.makeText(this, R.string.overlay_allowed, Toast.LENGTH_SHORT).show()
    }

    private fun startAlerts() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(); return
        }
        if (!Settings.canDrawOverlays(this)) { enableOverlay(); return }
        val intent = Intent(this, AlertService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            Toast.makeText(this, R.string.alerts_started, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, getString(R.string.service_start_failed, error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
        }
    }


    private fun refreshRoadData(force: Boolean) {
        Toast.makeText(this, R.string.osm_import_started, Toast.LENGTH_SHORT).show()
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val location = runCatching {
            locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        if (location == null) {
            if (force) Toast.makeText(this, R.string.gps_wait, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.osm_import_started, Toast.LENGTH_SHORT).show()
        cloudSync?.listenExternalForLocation(location.latitude, location.longitude)
        OsmHazardImporter(this).importForLocation(location.latitude, location.longitude, force) { count, success ->
            runOnUiThread {
                if (count > 0) Toast.makeText(this, getString(R.string.osm_import_done, count), Toast.LENGTH_LONG).show()
                if (success) Toast.makeText(this, R.string.osm_import_finished, Toast.LENGTH_SHORT).show()
                else Toast.makeText(this, R.string.osm_import_failed, Toast.LENGTH_LONG).show()
                refreshStatus()
            }
            cloudSync?.publishOpenStreetMap { uploaded, total ->
                runOnUiThread {
                    if (uploaded && total > 0) Toast.makeText(this, getString(R.string.sync_complete) + " ($total OSM)", Toast.LENGTH_LONG).show()
                    if (!uploaded) Toast.makeText(this, R.string.sync_pending, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun report(type: HazardType) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(); return
        }
        val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val location = runCatching {
            lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        if (location == null) {
            Toast.makeText(this, R.string.gps_wait, Toast.LENGTH_LONG).show(); return
        }
        val hazard = Hazard(location.latitude, location.longitude, type)
        store.add(hazard)
        Toast.makeText(this, R.string.report_saved, Toast.LENGTH_SHORT).show()
        cloudSync?.submit(hazard) { synced ->
            runOnUiThread {
                Toast.makeText(this, if (synced) R.string.sync_complete else R.string.sync_pending, Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        val all = runCatching { store.all() }.getOrDefault(emptyList())
        status.text = getString(R.string.status_local,
            all.count { it.type == HazardType.SPEED_CAMERA },
            all.count { it.type == HazardType.SPEED_BUMP },
            all.count { it.type == HazardType.POTHOLE }) + "\n" + getString(if (cloudReady) R.string.sync_ready else R.string.sync_pending)
    }

    companion object {
        const val ACTION_QUICK_REPORT = "com.roadalert.egypt.ACTION_QUICK_REPORT"
    }
}