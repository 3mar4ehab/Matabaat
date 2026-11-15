package com.roadalert.egypt

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.google.firebase.auth.FirebaseAuth

class AdminLoginActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var email: EditText
    private lateinit var password: EditText
    private val adminEmail = "oshehab007@gmail.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28); setBackgroundColor(0xFFF4F7FB.toInt())
        }
        root.addView(TextView(this).apply { text = "دخول إدارة مطبات"; textSize = 25f; setTextColor(0xFF0C1B36.toInt()); setTypeface(null, android.graphics.Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "الدخول متاح للبريد الإداري المعتمد فقط"; textSize = 14f; setTextColor(0xFF596579.toInt()); setPadding(0, 8, 0, 20) })
        email = EditText(this).apply { hint = "البريد الإلكتروني"; setSingleLine(); setText(adminEmail); inputType = 33 }
        password = EditText(this).apply { hint = "كلمة المرور"; inputType = 129 }
        root.addView(email, LinearLayout.LayoutParams(-1, -2))
        root.addView(password, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })
        root.addView(Button(this).apply { text = "تسجيل الدخول"; isAllCaps = false; setOnClickListener { signIn() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        root.addView(Button(this).apply { text = "إنشاء حساب الأدمن لأول مرة"; isAllCaps = false; setOnClickListener { register() } })
        root.addView(TextView(this).apply { text = "سيتم إرسال رسالة تحقق للبريد. لا تُمنح صلاحيات الإدارة قبل توثيق البريد من Firebase."; textSize = 12f; setTextColor(0xFF667085.toInt()); setPadding(0, 18, 0, 0) })
        setContentView(root)
    }

    private fun validInput(): Boolean {
        if (email.text.toString().trim().equals(adminEmail, true).not()) {
            Toast.makeText(this, "هذا البريد غير مصرح له بالإدارة", Toast.LENGTH_LONG).show(); return false
        }
        if (password.text.isNullOrBlank() || password.text.length < 6) {
            password.error = "أدخل كلمة مرور لا تقل عن 6 أحرف"; return false
        }
        return true
    }

    private fun signIn() {
        if (!validInput()) return
        auth.signInWithEmailAndPassword(adminEmail, password.text.toString())
            .addOnSuccessListener {
                val user = auth.currentUser
                if (user?.isEmailVerified == true) {
                    startActivity(android.content.Intent(this, AdminActivity::class.java)); finish()
                } else {
                    user?.sendEmailVerification()
                    AlertDialog.Builder(this).setTitle("البريد غير موثّق")
                        .setMessage("افتح رسالة التحقق التي أرسلناها إلى $adminEmail، ثم ارجع وسجّل الدخول مرة أخرى.")
                        .setPositiveButton("حسنًا", null).show()
                }
            }.addOnFailureListener { Toast.makeText(this, "تعذر تسجيل الدخول: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }

    private fun register() {
        if (!validInput()) return
        auth.createUserWithEmailAndPassword(adminEmail, password.text.toString())
            .addOnSuccessListener { result ->
                result.user?.sendEmailVerification()?.addOnCompleteListener {
                    AlertDialog.Builder(this).setTitle("تم إنشاء الحساب")
                        .setMessage("أرسلنا رسالة تحقق إلى $adminEmail. افتحها أولًا، ثم ارجع وسجّل الدخول.")
                        .setPositiveButton("حسنًا", null).show()
                }
            }.addOnFailureListener { Toast.makeText(this, "تعذر إنشاء الحساب: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }
}
