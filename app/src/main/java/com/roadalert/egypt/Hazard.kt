package com.roadalert.egypt

enum class HazardType { SPEED_CAMERA, SPEED_BUMP, POTHOLE }
enum class HazardSource { USER, FIRESTORE, OPENSTREETMAP }

data class Hazard(
    val latitude: Double,
    val longitude: Double,
    val type: HazardType,
    val createdAt: Long = System.currentTimeMillis(),
    val source: HazardSource = HazardSource.USER,
    val sourceId: String = ""
)
