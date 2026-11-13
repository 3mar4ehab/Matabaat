package com.roadalert.egypt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var store: HazardStore
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = HazardStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(0xFFF4F7FA.toInt())
        }
        val title = TextView(this).apply { text = "Matabaat"; textSize = 28f; setTextColor(0xFF102A43.toInt()) }
        val subtitle = TextView(this).apply {
            text = "تنبيهات الرادارات والمطبات أثناء استخدام Google Maps"
            textSize = 16f; setPadding(0, 12, 0, 24); setTextColor(0xFF334E68.toInt())
        }
        status = TextView(this).apply { textSize = 14f; setPadding(0, 8, 0, 18); setTextColor(0xFF334E68.toInt()) }
        root.addView(title); root.addView(subtitle); root.addView(status)
        root.addView(actionButton("تفعيل النافذة العائمة") { enableOverlay() })
        root.addView(actionButton("تشغيل التنبيهات") { startAlerts() })
        root.addView(actionButton("إيقاف التنبيهات") { stopService(Intent(this, AlertService::class.java)); Toast.makeText(this, "تم إيقاف التنبيهات", Toast.LENGTH_SHORT).show() })
        root.addView(actionButton("إبلاغ عن رادار هنا") { report(HazardType.SPEED_CAMERA) })
        root.addView(actionButton("إبلاغ عن مطب هنا") { report(HazardType.SPEED_BUMP) })
        root.addView(actionButton("إبلاغ عن حفرة هنا") { report(HazardType.POTHOLE) })
        root.addView(actionButton("فتح Google Maps") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")))
        })
        setContentView(root)
        refreshStatus()
        requestLocationPermission()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; gravity = Gravity.CENTER
        setOnClickListener { action() }
    }

    private fun requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10)
        }
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "اسمح لـ Matabaat بالظهور فوق التطبيقات ثم ارجع للتطبيق", Toast.LENGTH_LONG).show()
        } else Toast.makeText(this, "تم السماح بالنافذة العائمة", Toast.LENGTH_SHORT).show()
    }

    private fun startAlerts() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(); return
        }
        if (!Settings.canDrawOverlays(this)) { enableOverlay(); return }
        val intent = Intent(this, AlertService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "بدأت خدمة التنبيهات", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "لم يتم تحديد موقعك بعد. شغّل GPS وانتظر قليلاً", Toast.LENGTH_LONG).show(); return
        }
        store.add(Hazard(location.latitude, location.longitude, type))
        Toast.makeText(this, "تم حفظ البلاغ على جهازك", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun refreshStatus() {
        val counts = HazardType.values().associateWith { t -> store.all().count { it.type == t } }
        status.text = "البلاغات المحفوظة على هذا الجهاز: رادارات ${counts[HazardType.SPEED_CAMERA]} | مطبات ${counts[HazardType.SPEED_BUMP]} | حفر ${counts[HazardType.POTHOLE]}\nالنسخة التجريبية لا تحتوي بعد على قاعدة بيانات مشتركة."
    }
}
