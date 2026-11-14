package com.roadalert.egypt

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Anonymous-authenticated Firestore sync for user reports. */
class HazardCloudSync(context: Context) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val store = HazardStore(appContext)
    private var listener: ListenerRegistration? = null

    fun start(onReady: ((Boolean) -> Unit)? = null) {
        if (auth.currentUser != null) {
            listen()
            onReady?.invoke(true)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { listen(); onReady?.invoke(true) }
            .addOnFailureListener { error ->
                Log.e("MatabaatCloud", "Anonymous sign-in failed", error)
                onReady?.invoke(false)
            }
    }

    private fun listen() {
        if (listener != null) return
        listener = db.collection("hazards").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("MatabaatCloud", "Hazard sync listener failed", error)
                return@addSnapshotListener
            }
            snapshot?.documents?.forEach { doc ->
                val lat = doc.getDouble("latitude") ?: return@forEach
                val lon = doc.getDouble("longitude") ?: return@forEach
                val type = runCatching { HazardType.valueOf(doc.getString("type") ?: "") }.getOrNull() ?: return@forEach
                val createdAt = doc.getLong("createdAt") ?: 0L
                if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                    store.addIfMissing(Hazard(lat, lon, type, createdAt, HazardSource.FIRESTORE, doc.id))
                }
            }
        }
    }

    fun submit(hazard: Hazard, onComplete: ((Boolean) -> Unit)? = null) {
        start { signedIn ->
            if (!signedIn) { onComplete?.invoke(false); return@start }
            val data = hashMapOf(
                "latitude" to hazard.latitude, "longitude" to hazard.longitude,
                "type" to hazard.type.name, "createdAt" to hazard.createdAt
            )
            db.collection("hazards").add(data)
                .addOnSuccessListener { onComplete?.invoke(true) }
                .addOnFailureListener { error ->
                    Log.e("MatabaatCloud", "Could not upload hazard", error)
                    onComplete?.invoke(false)
                }
        }
    }

    fun stop() { listener?.remove(); listener = null }
}
