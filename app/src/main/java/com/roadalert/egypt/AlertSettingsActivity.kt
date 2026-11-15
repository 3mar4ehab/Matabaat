package com.roadalert.egypt

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import java.util.Locale

object AlertSounds {
    const val PREFS = "matabaat_alert_sounds"
    private val names = listOf("نبضة واحدة", "نبضتان", "ثلاث نبضات")
    fun label(index: Int) = names[index.coerceIn(0, 2)]
    fun play(context: Context, type: HazardType, previewIndex: Int? = null) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val index = previewIndex ?: prefs.getInt(type.name, defaultFor(type))
        if (prefs.getBoolean("spoken_alerts", true)) {
            val spoken = when (type) {
                HazardType.SPEED_CAMERA -> "احذر، رادار أمامك"
                HazardType.SPEED_BUMP -> "احذر، مطب أمامك"
                HazardType.POTHOLE -> "احذر، حفرة أمامك"
            }
            lateinit var speaker: TextToSpeech
            speaker = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val languageResult = speaker.setLanguage(Locale("ar"))
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        speaker.setLanguage(Locale.getDefault())
                    }
                    speaker.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "matabaat-${type.name}")
                    Handler(Looper.getMainLooper()).postDelayed({ runCatching { speaker.stop(); speaker.shutdown() } }, 5000L)
                }
            }
            return
        }
        val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        val handler = Handler(Looper.getMainLooper())
        val tone = when (type) {
            HazardType.SPEED_CAMERA -> ToneGenerator.TONE_PROP_BEEP
            HazardType.SPEED_BUMP -> ToneGenerator.TONE_PROP_ACK
            HazardType.POTHOLE -> ToneGenerator.TONE_PROP_NACK
        }
        val count = index + 1
        repeat(count) { n ->
            handler.postDelayed({
                runCatching { generator.startTone(tone, if (index == 0) 500 else 170) }
                if (n == count - 1) handler.postDelayed({ generator.release() }, 600)
            }, n * if (index == 2) 260L else 420L)
        }
    }
    fun defaultFor(type: HazardType) = when(type) {
        HazardType.SPEED_CAMERA -> 0
        HazardType.SPEED_BUMP -> 1
        HazardType.POTHOLE -> 2
    }
}

object AlertDistances {
    const val PREFS = "matabaat_alert_distances"
    fun defaultFor(type: HazardType) = when (type) {
        HazardType.SPEED_CAMERA -> 500
        HazardType.SPEED_BUMP -> 250
        HazardType.POTHOLE -> 150
    }
    fun get(context: Context, type: HazardType): Int =
        context.getSharedPreferences(PREFS, 0).getInt(type.name, defaultFor(type)).coerceIn(50, 1000)
}

class AlertSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(AlertSounds.PREFS, 0)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 20); setBackgroundColor(0xFFF4F7FB.toInt()) }
        root.addView(TextView(this).apply { text = "إعدادات أصوات التنبيه"; textSize = 23f; setTextColor(0xFF0C1B36.toInt()); setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = "اختار نمط صوت مستقل لكل نوع خطر، واضغط تجربة قبل الحفظ."; textSize = 14f; setTextColor(0xFF596579.toInt()); setPadding(0, 8, 0, 18) })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@AlertSettingsActivity).apply { text = "التنبيه الصوتي المنطوق بالعربية"; textSize = 16f; setTextColor(0xFF0C1B36.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Switch(this@AlertSettingsActivity).apply {
                isChecked = prefs.getBoolean("spoken_alerts", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("spoken_alerts", checked).apply() }
            })
        })
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
        root.addView(TextView(this).apply { text = "مسافة بدء التنبيه"; textSize = 20f; setTextColor(0xFF0C1B36.toInt()); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 24, 0, 8) })
        root.addView(TextView(this).apply { text = "حدد من كام متر يبدأ الصوت لكل نوع خطر. القيم المقترحة: الرادار 500م، المطب 250م، الحفرة 150م. المسافة تتكيف تلقائيًا مع سرعة السيارة."; textSize = 14f; setTextColor(0xFF596579.toInt()) })
        listOf(HazardType.SPEED_CAMERA to "الرادارات", HazardType.SPEED_BUMP to "المطبات", HazardType.POTHOLE to "الحفر").forEach { (type, label) ->
            root.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(0xFF0C1B36.toInt()); setPadding(0, 12, 0, 4) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val values = (50..1000 step 50).toList()
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values.map { "$it متر" })
            spinner.setSelection(values.indexOf(AlertDistances.get(this@AlertSettingsActivity, type)).coerceAtLeast(0))
            row.addView(spinner, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply { text = "حفظ"; isAllCaps = false; setOnClickListener {
                getSharedPreferences(AlertDistances.PREFS, 0).edit().putInt(type.name, values[spinner.selectedItemPosition]).apply()
                Toast.makeText(this@AlertSettingsActivity, "تم حفظ مسافة $label", Toast.LENGTH_SHORT).show()
            } })
            root.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
