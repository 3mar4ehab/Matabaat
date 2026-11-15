package com.roadalert.egypt

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*

object AlertSounds {
    const val PREFS = "matabaat_alert_sounds"
    private val names = listOf("نبضة واحدة", "نبضتان", "ثلاث نبضات")
    fun label(index: Int) = names[index.coerceIn(0, 2)]
    fun play(context: Context, type: HazardType, previewIndex: Int? = null) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val index = previewIndex ?: prefs.getInt(type.name, defaultFor(type))
        val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        val handler = Handler(Looper.getMainLooper())
        val tone = when (type) { HazardType.SPEED_CAMERA -> ToneGenerator.TONE_PROP_BEEP; HazardType.SPEED_BUMP -> ToneGenerator.TONE_PROP_ACK; HazardType.POTHOLE -> ToneGenerator.TONE_PROP_NACK }
        val count = index + 1
        repeat(count) { n -> handler.postDelayed({ runCatching { generator.startTone(tone, if (index == 0) 500 else 170) }; if (n == count - 1) handler.postDelayed({ generator.release() }, 600) }, n * if (index == 2) 260L else 420L) }
    }
    fun defaultFor(type: HazardType) = when(type) { HazardType.SPEED_CAMERA -> 0; HazardType.SPEED_BUMP -> 1; HazardType.POTHOLE -> 2 }
}

class AlertSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(AlertSounds.PREFS, 0)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 20); setBackgroundColor(0xFFF4F7FB.toInt()) }
        root.addView(TextView(this).apply { text = "إعدادات أصوات التنبيه"; textSize = 23f; setTextColor(0xFF0C1B36.toInt()); setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = "اختار نمط صوت مستقل لكل نوع خطر، واضغط تجربة قبل الحفظ."; textSize = 14f; setTextColor(0xFF596579.toInt()); setPadding(0, 8, 0, 18) })
        listOf(HazardType.SPEED_BUMP to "المطبات", HazardType.POTHOLE to "الحفر", HazardType.SPEED_CAMERA to "الرادارات").forEach { (type, label) ->
            root.addView(TextView(this).apply { text = label; textSize = 18f; setTextColor(0xFF0C1B36.toInt()); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 12, 0, 4) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("نبضة واحدة", "نبضتان", "ثلاث نبضات"))
            spinner.setSelection(prefs.getInt(type.name, AlertSounds.defaultFor(type)))
            row.addView(spinner, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply { text = "تجربة"; isAllCaps = false; setOnClickListener { AlertSounds.play(this@AlertSettingsActivity, type, spinner.selectedItemPosition) } })
            row.addView(Button(this).apply { text = "حفظ"; isAllCaps = false; setOnClickListener { prefs.edit().putInt(type.name, spinner.selectedItemPosition).apply(); Toast.makeText(this@AlertSettingsActivity, "تم حفظ صوت $label", Toast.LENGTH_SHORT).show() } })
            root.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
