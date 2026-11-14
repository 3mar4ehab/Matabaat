package com.roadalert.egypt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class AlertService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private val store by lazy { HazardStore(this) }
    private var cloudSync: HazardCloudSync? = null
    private var lastAlertKey = ""
    private var lastAlertAt = 0L
    private lateinit var osmImporter: OsmHazardImporter
    private var lastTileCheckAt = 0L
    private var lastCloudTileKey = ""

    override fun onCreate() {
        super.onCreate()
        runCatching { HazardCloudSync(this).also { cloudSync = it; it.start() } }
        createChannel()
        startForeground(44, notification(getString(R.string.service_tracking)))
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble(getString(R.string.service_locating))
        osmImporter = OsmHazardImporter(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 5f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 10f, this)
        } catch (_: SecurityException) { stopSelf() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("matabaat_alerts", getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "matabaat_alerts")
            .setContentTitle(getString(R.string.app_name)).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_map).setContentIntent(open).build()
    }

    private fun showBubble(text: String) {
        if (bubble == null) {
            bubble = TextView(this).apply {
                setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF102A43.toInt())
                textSize = 14f; setPadding(22, 16, 22, 16)
                isClickable = true
                contentDescription = getString(R.string.quick_report)
                setOnClickListener {
                    val openReport = Intent(this@AlertService, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_QUICK_REPORT
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    runCatching { startActivity(openReport) }
                }
            }
            val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 18; y = 140 }
            runCatching { windowManager.addView(bubble, params) }.onFailure { stopSelf() }
        }
        bubble?.text = "＋ " + getString(R.string.quick_report_short) + "\n" + text
    }

    override fun onLocationChanged(location: Location) {
        // Check the current map tile periodically; unseen tiles are downloaded automatically.
        if (System.currentTimeMillis() - lastTileCheckAt >= 30_000L) {
            lastTileCheckAt = System.currentTimeMillis()
            val tileKey = "${kotlin.math.floor(location.latitude / 0.25)}_${kotlin.math.floor(location.longitude / 0.25)}"
            if (tileKey != lastCloudTileKey) {
                lastCloudTileKey = tileKey
                cloudSync?.listenExternalForLocation(location.latitude, location.longitude)
            }
            osmImporter.importForLocation(location.latitude, location.longitude) { count, success ->
                if (count > 0) runCatching { cloudSync?.publishOpenStreetMap() }
            }
        }
        val nearest = Geo.nearest(location.latitude, location.longitude, store.all())
        if (nearest == null) { showBubble(getString(R.string.no_nearby)); return }
        val (hazard, distance) = nearest
        val label = when (hazard.type) {
            HazardType.SPEED_CAMERA -> getString(R.string.camera_reported)
            HazardType.SPEED_BUMP -> getString(R.string.bump_reported)
            HazardType.POTHOLE -> getString(R.string.pothole_reported)
        }
        val meters = distance.toInt()
        showBubble(getString(R.string.alert_distance, label, meters))
        val key = "${hazard.type}:${hazard.latitude}:${hazard.longitude}"
        if (distance <= 350f && (key != lastAlertKey || System.currentTimeMillis() - lastAlertAt > 45000L)) {
            lastAlertKey = key; lastAlertAt = System.currentTimeMillis()
            runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85).startTone(ToneGenerator.TONE_PROP_BEEP, 500) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(44, notification(getString(R.string.alert_distance, label, meters)))
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { showBubble(getString(R.string.enable_gps)) }
    @Deprecated("Deprecated in Android API") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

    override fun onDestroy() {
        runCatching { if (::locationManager.isInitialized) locationManager.removeUpdates(this) }
        cloudSync?.stop()
        bubble?.let { runCatching { if (::windowManager.isInitialized) windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}