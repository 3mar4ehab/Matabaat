package com.roadalert.egypt

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var rows: LinearLayout
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = auth.currentUser
        if (user?.email.equals("oshehab007@gmail.com", true).not() || user?.isEmailVerified != true) {
            startActivity(android.content.Intent(this, AdminLoginActivity::class.java)); finish(); return
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12); setBackgroundColor(0xFFF4F7FB.toInt()) }
        root.addView(TextView(this).apply { text = "لوحة تحكم مطبات"; textSize = 24f; setTextColor(0xFF0C1B36.toInt()); setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, 8, 0, 12) })
        root.addView(Button(this).apply { text = "＋ إضافة بلاغ يدوي"; isAllCaps = false; setOnClickListener { editDialog(null, null) } })
        root.addView(Button(this).apply { text = "تسجيل خروج الأدمن"; isAllCaps = false; setOnClickListener { auth.signOut(); finish() } })
        val scroll = ScrollView(this)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        listener = db.collection("hazards").addSnapshotListener { snapshot, error ->
            if (error != null) { Toast.makeText(this, "تعذر تحميل البلاغات: ${error.localizedMessage}", Toast.LENGTH_LONG).show(); return@addSnapshotListener }
            rows.removeAllViews()
            snapshot?.documents?.forEach { doc ->
                val lat = doc.getDouble("latitude") ?: return@forEach
                val lon = doc.getDouble("longitude") ?: return@forEach
                val type = doc.getString("type") ?: return@forEach
                val label = when(type) { "SPEED_CAMERA" -> "رادار"; "SPEED_BUMP" -> "مطب"; "POTHOLE" -> "حفرة"; else -> type }
                val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); setBackgroundColor(android.graphics.Color.WHITE) }
                card.addView(TextView(this).apply { text = "$label • %.5f, %.5f".format(lat, lon); textSize = 15f; setTextColor(0xFF0C1B36.toInt()) })
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                actions.addView(Button(this).apply { text = "تعديل"; isAllCaps = false; setOnClickListener { editDialog(doc.id, doc.data ?: emptyMap()) } }, LinearLayout.LayoutParams(0, -2, 1f))
                actions.addView(Button(this).apply { text = "حذف"; isAllCaps = false; setOnClickListener { confirmDelete(doc.id, label) } }, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(actions); rows.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
            }
        }
    }

    private fun editDialog(id: String?, old: Map<String, Any>?) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 0) }
        val type = Spinner(this)
        val types = listOf("SPEED_CAMERA", "SPEED_BUMP", "POTHOLE")
        type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("رادار", "مطب", "حفرة"))
        type.setSelection(types.indexOf(old?.get("type") ?: "POTHOLE").coerceAtLeast(0))
        val lat = EditText(this).apply { hint = "خط العرض Latitude"; inputType = 8194; setText(old?.get("latitude")?.toString() ?: "") }
        val lon = EditText(this).apply { hint = "خط الطول Longitude"; inputType = 8194; setText(old?.get("longitude")?.toString() ?: "") }
        panel.addView(type); panel.addView(lat); panel.addView(lon)
        AlertDialog.Builder(this).setTitle(if (id == null) "إضافة بلاغ" else "تعديل البلاغ")
            .setView(panel).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(-1).setOnClickListener {
                        val la = lat.text.toString().toDoubleOrNull(); val lo = lon.text.toString().toDoubleOrNull()
                        if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                            Toast.makeText(this, "أدخل إحداثيات صحيحة", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                        }
                        val data = hashMapOf<String, Any>("latitude" to la, "longitude" to lo, "type" to types[type.selectedItemPosition], "createdAt" to (old?.get("createdAt") as? Long ?: System.currentTimeMillis()), "ownerUid" to (old?.get("ownerUid") ?: auth.currentUser!!.uid), "adminEdited" to true)
                        val ref = if (id == null) db.collection("hazards").document() else db.collection("hazards").document(id)
                        ref.set(data).addOnSuccessListener { dialog.dismiss(); Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show() }
                            .addOnFailureListener { Toast.makeText(this, "فشل الحفظ: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                    }
                }
                dialog.show()
            }
    }

    private fun confirmDelete(id: String, label: String) {
        AlertDialog.Builder(this).setTitle("حذف البلاغ؟").setMessage("سيتم حذف بلاغ $label نهائيًا من قاعدة البيانات.")
            .setNegativeButton("إلغاء", null).setPositiveButton("حذف") { _, _ ->
                db.collection("hazards").document(id).delete()
                    .addOnSuccessListener { Toast.makeText(this, "تم حذف البلاغ", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { Toast.makeText(this, "فشل الحذف: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
            }.show()
    }

    override fun onDestroy() { listener?.remove(); super.onDestroy() }
}
