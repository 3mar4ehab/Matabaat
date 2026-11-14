package com.roadalert.egypt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import android.location.LocationManager

class MainActivity : Activity() {
    private lateinit var store: HazardStore
    private var cloudSync: HazardCloudSync? = null
    private var cloudReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = HazardStore(this)
        buildDashboard()
        runCatching {
            HazardCloudSync(this).also { sync ->
                cloudSync = sync
                sync.start { ready -> runOnUiThread { cloudReady = ready; buildDashboard() } }
            }
        }
        requestLocationPermission()
        refreshRoadData(false)
        handleQuickReportIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleQuickReportIntent(intent)
    }

    private fun handleQuickReportIntent(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_REPORT) {
            intent.action = null
            window.decorView.post { showQuickReport() }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildDashboard() {
        val navy = Color.rgb(12, 27, 54)
        val yellow = Color.rgb(255, 193, 7)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 251))
            setPadding(dp(18), dp(14), dp(18), dp(18))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(navy)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_matabaat)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(170), dp(160))
        }
        header.addView(logo)
        val brand = TextView(this).apply {
            text = "مطبات  |  Matabaat"; textSize = 23f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(brand)
        val sub = TextView(this).apply {
            text = "تنبيهات الطريق ومشاركة المخاطر"; textSize = 14f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(4))
        }
        header.addView(sub)
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val all = store.all()
        val counts = listOf(
            Triple("🚨", "رادارات", HazardType.SPEED_CAMERA),
            Triple("🟡", "مطبات", HazardType.SPEED_BUMP),
            Triple("🕳️", "حفر", HazardType.POTHOLE)
        )
        val countRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        counts.forEach { item ->
            val count = all.count { it.type == item.third }
            val card = TextView(this).apply {
                text = "${item.first}\n$count\n${item.second}\nاضغط للخريطة"
                gravity = Gravity.CENTER; textSize = 15f; setTextColor(navy)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(4), dp(16), dp(4), dp(16))
                setBackgroundColor(Color.WHITE); isClickable = true
                setOnClickListener { openHazardMap(item.third) }
            }
            countRow.addView(card, LinearLayout.LayoutParams(0, dp(112), 1f).apply {
                marginEnd = dp(6)
            })
        }
        root.addView(countRow)
        val sync = TextView(this).apply {
            text = if (cloudReady) "● متصل بالمزامنة السحابية" else "● المزامنة السحابية معلّقة"
            setTextColor(if (cloudReady) Color.rgb(28, 130, 78) else Color.DKGRAY)
            textSize = 13f; setPadding(dp(4), dp(12), dp(4), dp(8))
        }
        root.addView(sync)
        root.addView(sectionTitle("الإبلاغ عن خطر"))
        root.addView(primaryButton("＋ بلاغ جديد") { showQuickReport() }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(8) })
        root.addView(sectionTitle("الخريطة والتنبيهات"))
        root.addView(actionButton("عرض كل المخاطر على الخريطة") { openHazardMap(null) })
        root.addView(actionButton("تشغيل التنبيهات") { startAlerts() })
        root.addView(actionButton("إيقاف التنبيهات") {
            stopService(Intent(this, AlertService::class.java))
            Toast.makeText(this, "تم إيقاف التنبيهات", Toast.LENGTH_SHORT).show()
        })
        root.addView(actionButton("تفعيل النافذة العائمة") { enableOverlay() })
        root.addView(actionButton("تحديث بيانات الطريق حولي") { refreshRoadData(true) })
        val attribution = TextView(this).apply {
            text = getString(R.string.osm_attribution); textSize = 11f
            setTextColor(Color.GRAY); setPadding(0, dp(14), 0, 0)
        }
        root.addView(attribution)
        setContentView(scroll)
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text; textSize = 17f; setTextColor(Color.rgb(12, 27, 54))
        setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, dp(14), 0, dp(8))
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 17f
        setTextColor(Color.rgb(12, 27, 54)); setBackgroundColor(Color.rgb(255, 193, 7))
        setOnClickListener { action() }
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setOnClickListener { action() }
    }

    private fun showQuickReport() {
        val labels = arrayOf("رادار", "مطب", "حفرة")
        val types = arrayOf(HazardType.SPEED_CAMERA, HazardType.SPEED_BUMP, HazardType.POTHOLE)
        AlertDialog.Builder(this).setTitle("إيه الخطر اللي قابلته؟")
            .setItems(labels) { _, which -> chooseReportLocation(types[which]) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun chooseReportLocation(type: HazardType) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
            Toast.makeText(this, "اسمح بالوصول للموقع ثم أعد البلاغ", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(Intent(this, HazardMapActivity::class.java).apply {
            putExtra(HazardMapActivity.EXTRA_PICK_LOCATION, true)
        }, REQUEST_PICK_LOCATION)
        pendingReportType = type
    }

    private var pendingReportType: HazardType? = null

    @Deprecated("Activity result API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_LOCATION && resultCode == RESULT_OK && data != null) {
            val lat = data.getDoubleExtra(HazardMapActivity.EXTRA_LAT, Double.NaN)
            val lon = data.getDoubleExtra(HazardMapActivity.EXTRA_LON, Double.NaN)
            val type = pendingReportType ?: return
            if (lat.isFinite() && lon.isFinite()) {
                val hazard = Hazard(lat, lon, type)
                store.add(hazard)
                Toast.makeText(this, "تم حفظ البلاغ عند النقطة المحددة على الخريطة", Toast.LENGTH_LONG).show()
                cloudSync?.submit(hazard) { synced ->
                    runOnUiThread {
                        Toast.makeText(this, if (synced) "تمت المزامنة" else "سيتم الاحتفاظ به محليًا حتى تتاح المزامنة", Toast.LENGTH_LONG).show()
                        buildDashboard()
                    }
                }
                buildDashboard()
            }
        }
        pendingReportType = null
    }

    private fun openHazardMap(type: HazardType?) {
        startActivity(Intent(this, HazardMapActivity::class.java).apply {
            if (type != null) putExtra(HazardMapActivity.EXTRA_TYPE, type.name)
        })
    }

    private fun requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10)
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "اسمح لمطبات بالظهور فوق التطبيقات ثم ارجع", Toast.LENGTH_LONG).show()
        } else Toast.makeText(this, "النافذة العائمة مفعّلة", Toast.LENGTH_SHORT).show()
    }

    private fun startAlerts() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { requestLocationPermission(); return }
        if (!Settings.canDrawOverlays(this)) { enableOverlay(); return }
        try {
            val i = Intent(this, AlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
            Toast.makeText(this, "بدأت خدمة التنبيهات", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "تعذر تشغيل التنبيهات: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
    }

    private fun refreshRoadData(force: Boolean) {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull() ?: return
        cloudSync?.listenExternalForLocation(loc.latitude, loc.longitude)
        OsmHazardImporter(this).importForLocation(loc.latitude, loc.longitude, force) { count, success ->
            runOnUiThread {
                if (force) Toast.makeText(this, if (success) "اكتمل تحديث بيانات المنطقة القريبة ($count نقطة جديدة)" else "تعذر تحديث بيانات الطريق", Toast.LENGTH_LONG).show()
                buildDashboard()
            }
            cloudSync?.publishOpenStreetMap()
        }
    }

    companion object {
        const val ACTION_QUICK_REPORT = "com.roadalert.egypt.ACTION_QUICK_REPORT"
        private const val REQUEST_PICK_LOCATION = 41
    }
}
