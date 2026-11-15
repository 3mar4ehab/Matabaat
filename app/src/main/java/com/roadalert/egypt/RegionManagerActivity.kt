package com.roadalert.egypt

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class RegionManagerActivity : Activity() {
    private lateinit var manager: RegionDataManager
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = RegionDataManager(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 18)
            setBackgroundColor(0xFFF4F7FB.toInt())
        }
        root.addView(TextView(this).apply {
            text = "إدارة بيانات المحافظات"
            textSize = 22f; setTextColor(0xFF0C1B36.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, 12, 0, 6)
        })
        root.addView(TextView(this).apply {
            text = "اختر أي محافظة لتنزيل بيانات الرادارات والمطبات من حدودها الإدارية. يمكنك تحديث البيانات أو حذفها بشكل مستقل."
            textSize = 14f; setTextColor(0xFF45556B.toInt()); setPadding(4, 4, 4, 14)
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() {
        list.removeAllViews()
        EgyptGovernorates.all.forEach { region ->
            val count = manager.hazards(region).size
            val time = manager.downloadedAt(region)
            val status = if (time > 0L) "تم التنزيل • $count نقطة • ${java.text.DateFormat.getDateInstance().format(java.util.Date(time))}" else "لم يتم تنزيل البيانات"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12)
                setBackgroundColor(android.graphics.Color.WHITE)
            }
            row.addView(TextView(this).apply {
                text = region.arabicName; textSize = 17f; setTextColor(0xFF0C1B36.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            row.addView(TextView(this).apply { text = status; textSize = 12f; setTextColor(0xFF596579.toInt()) })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(this).apply {
                text = if (time > 0L) "تحديث" else "تنزيل"; isAllCaps = false
                setOnClickListener { confirmDownload(region) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(Button(this).apply {
                text = "عرض"; isAllCaps = false; isEnabled = count > 0
                setOnClickListener { startActivity(android.content.Intent(this@RegionManagerActivity, HazardMapActivity::class.java).putExtra(HazardMapActivity.EXTRA_REGION_ID, region.id)) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(Button(this).apply {
                text = "حذف"; isAllCaps = false; isEnabled = time > 0L || count > 0
                setOnClickListener {
                    AlertDialog.Builder(this@RegionManagerActivity).setTitle("حذف بيانات ${region.arabicName}؟")
                        .setMessage("سيتم حذف بيانات OSM المحمّلة لهذه المحافظة فقط، ولن تُحذف بلاغات المستخدم.")
                        .setNegativeButton("إلغاء", null).setPositiveButton("حذف") { _, _ -> manager.delete(region); render() }.show()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actions)
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        }
    }

    private fun confirmDownload(region: Governorate) {
        AlertDialog.Builder(this).setTitle("تنزيل بيانات ${region.arabicName}")
            .setMessage("سيتم جلب الرادارات والمطبات المسجلة في OpenStreetMap داخل الحدود الإدارية للمحافظة. يتطلب ذلك اتصال إنترنت.")
            .setNegativeButton("إلغاء", null).setPositiveButton("ابدأ") { _, _ ->
                Toast.makeText(this, "جاري تنزيل بيانات ${region.arabicName}...", Toast.LENGTH_LONG).show()
                manager.download(region) { count, success, error ->
                    runOnUiThread {
                        Toast.makeText(this, if (success) "اكتمل التنزيل: $count نقطة" else "فشل التنزيل: ${error ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                        render()
                    }
                }
            }.show()
    }
}
