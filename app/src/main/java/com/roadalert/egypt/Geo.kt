package com.roadalert.egypt

import android.location.Location

object Geo {
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0]
    }

    fun nearest(lat: Double, lon: Double, hazards: List<Hazard>): Pair<Hazard, Float>? =
        hazards.map { it to distanceMeters(lat, lon, it.latitude, it.longitude) }
            .filter { it.second <= 1200f }
            .minByOrNull { it.second }
}
