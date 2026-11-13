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
    private var lastAlertKey = ""
    private var lastAlertAt = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(44, notification("جاري متابعة الطريق"))
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble("RoadAlert\nجاري تحديد الموقع...")
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 5f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 10f, this)
        } catch (_: SecurityException) { stopSelf() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("road_alert", "RoadAlert navigation alerts", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "road_alert")
            .setContentTitle("RoadAlert Egypt").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_map).setContentIntent(open).build()
    }

    private fun showBubble(text: String) {
        if (bubble == null) {
            bubble = TextView(this).apply {
                setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xEE102A43.toInt())
                textSize = 14f; setPadding(22, 14, 22, 14)
            }
            val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 18; y = 140 }
            runCatching { windowManager.addView(bubble, params) }.onFailure { stopSelf() }
        }
        bubble?.text = text
    }

    override fun onLocationChanged(location: Location) {
        val nearest = Geo.nearest(location.latitude, location.longitude, store.all())
        if (nearest == null) { showBubble("RoadAlert\nلا توجد بلاغات قريبة"); return }
        val (hazard, distance) = nearest
        val label = when (hazard.type) {
            HazardType.SPEED_CAMERA -> "رادار مُبلّغ عنه"
            HazardType.SPEED_BUMP -> "مطب مُبلّغ عنه"
            HazardType.POTHOLE -> "حفرة مُبلّغ عنها"
        }
        val meters = distance.toInt()
        showBubble("⚠ $label\nبعد ${meters} متر")
        val key = "${hazard.type}:${hazard.latitude}:${hazard.longitude}"
        if (distance <= 350f && (key != lastAlertKey || System.currentTimeMillis() - lastAlertAt > 45000L)) {
            lastAlertKey = key; lastAlertAt = System.currentTimeMillis()
            runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85).startTone(ToneGenerator.TONE_PROP_BEEP, 500) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(44, notification("$label على بعد ${meters} متر"))
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { showBubble("RoadAlert\nفعّل GPS للحصول على التنبيهات") }
    @Deprecated("Deprecated in Android API") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
