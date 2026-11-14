package com.roadalert.egypt

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.WriteBatch

/** Keeps community reports and trusted-source imports in separate cloud collections. */
class HazardCloudSync(context: Context) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val store = HazardStore(appContext)
    private val prefs = appContext.getSharedPreferences("matabaat_cloud_sync", Context.MODE_PRIVATE)
    private var reportsListener: ListenerRegistration? = null
    private var externalListener: ListenerRegistration? = null

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
        if (reportsListener == null) {
            reportsListener = db.collection("hazards").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MatabaatCloud", "Community hazard listener failed", error)
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
        if (externalListener == null) {
            externalListener = db.collection("external_hazards").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MatabaatCloud", "External hazard listener failed", error)
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    val lat = doc.getDouble("latitude") ?: return@forEach
                    val lon = doc.getDouble("longitude") ?: return@forEach
                    val type = runCatching { HazardType.valueOf(doc.getString("type") ?: "") }.getOrNull() ?: return@forEach
                    val sourceId = doc.getString("sourceId") ?: doc.id
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    if (doc.getString("source") == "OPENSTREETMAP" &&
                        lat in 22.0..32.0 && lon in 24.0..37.0) {
                        store.addIfMissing(Hazard(lat, lon, type, createdAt, HazardSource.OPENSTREETMAP, sourceId))
                    }
                }
            }
        }
    }

    /** Uploads only OSM points; user-created reports never enter this collection. */
    fun publishOpenStreetMap(onComplete: ((Boolean, Int) -> Unit)? = null) {
        start { signedIn ->
            if (!signedIn) { onComplete?.invoke(false, 0); return@start }
            val uploaded = prefs.getStringSet("osm_uploaded_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            val pending = store.all().filter { it.source == HazardSource.OPENSTREETMAP && it.sourceId.isNotBlank() }
                .distinctBy { it.sourceId }
                .filter { documentId(it.sourceId) !in uploaded }
            if (pending.isEmpty()) { onComplete?.invoke(true, 0); return@start }

            uploadChunks(pending, 0, uploaded, 0, onComplete)
        }
    }

    private fun uploadChunks(
        hazards: List<Hazard>, offset: Int, uploaded: MutableSet<String>,
        completed: Int, onComplete: ((Boolean, Int) -> Unit)?
    ) {
        if (offset >= hazards.size) {
            prefs.edit().putStringSet("osm_uploaded_ids", uploaded).apply()
            onComplete?.invoke(true, completed)
            return
        }
        val end = minOf(offset + 450, hazards.size)
        val batch: WriteBatch = db.batch()
        val chunk = hazards.subList(offset, end)
        chunk.forEach { hazard ->
            val id = documentId(hazard.sourceId)
            val data = hashMapOf(
                "latitude" to hazard.latitude,
                "longitude" to hazard.longitude,
                "type" to hazard.type.name,
                "source" to "OPENSTREETMAP",
                "sourceId" to id,
                "createdAt" to hazard.createdAt
            )
            batch.set(db.collection("external_hazards").document(id), data)
        }
        batch.commit()
            .addOnSuccessListener {
                chunk.forEach { uploaded.add(documentId(it.sourceId)) }
                uploadChunks(hazards, end, uploaded, completed + chunk.size, onComplete)
            }
            .addOnFailureListener { error ->
                Log.e("MatabaatCloud", "OSM batch upload failed", error)
                prefs.edit().putStringSet("osm_uploaded_ids", uploaded).apply()
                onComplete?.invoke(false, completed)
            }
    }

    private fun documentId(sourceId: String): String = sourceId.replace('/', '_')

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
                    Log.e("MatabaatCloud", "Could not upload community report", error)
                    onComplete?.invoke(false)
                }
        }
    }

    fun stop() {
        reportsListener?.remove(); reportsListener = null
        externalListener?.remove(); externalListener = null
    }
}
