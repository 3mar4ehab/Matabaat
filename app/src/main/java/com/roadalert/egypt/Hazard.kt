package com.roadalert.egypt

enum class HazardType { SPEED_CAMERA, SPEED_BUMP, POTHOLE }

data class Hazard(
    val latitude: Double,
    val longitude: Double,
    val type: HazardType,
    val createdAt: Long = System.currentTimeMillis()
)
