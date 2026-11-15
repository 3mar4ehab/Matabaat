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
        fun rounded(color: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat() }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(243, 246, 250)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(16)) }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(navy) }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.matabaat_logo); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(64), dp(64)))
        val welcome = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        welcome.addView(TextView(this).apply { text = "أهلاً بيك في مطبات"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD) })
        welcome.addView(TextView(this).apply { text = "خليك عارف الطريق قبل ما توصله"; textSize = 12f; setTextColor(0xFFDCE5F2.toInt()) })
        header.addView(welcome); content.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        content.addView(TextView(this).apply { text = "المخاطر المسجلة"; textSize = 19f; setTextColor(navy); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, dp(8)) })
        val all = store.all()
        val cards = listOf(Triple("🚨", "رادارات", HazardType.SPEED_CAMERA), Triple("🟡", "مطبات", HazardType.SPEED_BUMP), Triple("🕳️", "حفر", HazardType.POTHOLE))
        cards.chunked(2).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { (emoji, label, type) ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(12), dp(8), dp(12))
                    background = rounded(Color.WHITE); elevation = dp(2).toFloat(); isClickable = true
                    setOnClickListener { openHazardMap(type) }
                }
                card.addView(TextView(this).apply { text = emoji; textSize = 23f })
                card.addView(TextView(this).apply { text = all.count { it.type == type }.toString(); textSize = 26f; setTextColor(navy); setTypeface(null, android.graphics.Typeface.BOLD) })
                card.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(0xFF526176.toInt()) })
                row.addView(card, LinearLayout.LayoutParams(0, dp(116), 1f).apply { marginEnd = dp(8); bottomMargin = dp(8) })
            }
            if (group.size == 1) row.addView(LinearLayout(this), LinearLayout.LayoutParams(0, 1, 1f))
            content.addView(row)
        }
        content.addView(TextView(this).apply {
            text = if (cloudReady) "● المزامنة السحابية متصلة" else "● المزامنة السحابية غير متاحة"
            textSize = 12f; setTextColor(if (cloudReady) 0xFF16834A.toInt() else 0xFF9A6700.toInt()); setPadding(0, dp(8), 0, dp(8))
        })
        content.addView(primaryButton("＋ بلاغ جديد") { showQuickReport() }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(12) })
        content.addView(sectionTitle("إلى أين تريد الذهاب؟"))
        val destination = EditText(this).apply {
            hint = "اكتب اسم المكان أو العنوان"
            setSingleLine(true)
            textSize = 16f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackground(rounded(Color.WHITE))
        }
        content.addView(destination, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(8) })
        content.addView(primaryButton("ابدأ الملاحة إلى الوجهة") {
            val query = destination.text.toString().trim()
            if (query.isBlank()) {
                destination.error = "اكتب الوجهة أولاً"
            } else {
                val uri = Uri.parse("google.navigation:q=" + Uri.encode(query) + "&mode=d")
                val navIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
                try { startActivity(navIntent) }
                catch (_: Exception) {
                    val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(query) + "&travelmode=driving")
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(12) })
        content.addView(sectionTitle("الخدمات"))
        content.addView(actionButton("فتح الخريطة الحديثة") { openHazardMap(null) })
        content.addView(actionButton("تشغيل التنبيهات أثناء القيادة") { startAlerts() })
        content.addView(actionButton("أصوات التنبيه لكل نوع خطر") { startActivity(Intent(this, AlertSettingsActivity::class.java)) })
        content.addView(actionButton("إدارة بيانات المحافظات") { startActivity(Intent(this, RegionManagerActivity::class.java)) })
        content.addView(actionButton("لوحة تحكم الأدمن") { startActivity(Intent(this, AdminLoginActivity::class.java)) })
        content.addView(actionButton("إيقاف التنبيهات") { stopService(Intent(this, AlertService::class.java)) })
        content.addView(actionButton("تحديث بيانات الطريق حولي") { refreshRoadData(true) })
        content.addView(TextView(this).apply { text = getString(R.string.osm_attribution); textSize = 10f; setTextColor(Color.GRAY); setPadding(0, dp(12), 0, 0) })

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE) }
        listOf("الرئيسية", "الخريطة", "بلاغ", "الأصوات").forEachIndexed { index, label ->
            nav.addView(Button(this).apply {
                text = label; isAllCaps = false; textSize = 12f
                setOnClickListener { when (index) { 1 -> openHazardMap(null); 2 -> showQuickReport(); 3 -> startActivity(Intent(this@MainActivity, AlertSettingsActivity::class.java)) } }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)
        setContentView(root)
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
