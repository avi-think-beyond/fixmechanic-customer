package com.example.data.location

import com.example.data.model.Mechanic
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure utility for Haversine distance calculation and strict eligibility filtering
 * of registered, verified, online, available mechanics near a customer's requested location.
 */
object NearbyMechanicMatcher {

    private const val EARTH_RADIUS_KM = 6371.0
    private const val AVERAGE_CITY_SPEED_KMH = 25.0

    fun haversineDistanceKm(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (latitude.isNaN() || longitude.isNaN()) return false
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return true
    }

    fun isEligibleNearbyMechanic(
        mechanic: Mechanic,
        userLat: Double,
        userLng: Double,
        searchRadiusKm: Double
    ): Boolean {
        if (!mechanic.isVerified) return false
        if (mechanic.isBlocked) return false
        if (!mechanic.isOnline || !mechanic.isAvailable) return false

        val mLat = mechanic.latitude
        val mLng = mechanic.longitude
        if (!isValidCoordinate(mLat, mLng) || !isValidCoordinate(userLat, userLng)) return false

        val distanceKm = haversineDistanceKm(userLat, userLng, mLat!!, mLng!!)
        if (distanceKm > searchRadiusKm) return false

        val allowedServiceRadius = mechanic.serviceRadiusKm
        if (allowedServiceRadius != null && allowedServiceRadius > 0.0 && distanceKm > allowedServiceRadius) {
            return false
        }
        return true
    }

    fun filterAndSortNearby(
        mechanics: List<Mechanic>,
        userLat: Double,
        userLng: Double,
        searchRadiusKm: Double
    ): List<Mechanic> {
        if (!isValidCoordinate(userLat, userLng) || searchRadiusKm <= 0.0) return emptyList()

        return mechanics
            .asSequence()
            .filter { isEligibleNearbyMechanic(it, userLat, userLng, searchRadiusKm) }
            .map { mechanic ->
                val dist = haversineDistanceKm(userLat, userLng, mechanic.latitude!!, mechanic.longitude!!)
                val etaMins = ((dist / AVERAGE_CITY_SPEED_KMH) * 60.0).roundToInt().coerceAtLeast(3)
                mechanic.copy(
                    currentDistanceKm = ((dist * 10.0).roundToInt() / 10.0f),
                    estimatedArrivalMinutes = etaMins
                )
            }
            .sortedBy { it.currentDistanceKm ?: Float.MAX_VALUE }
            .toList()
    }
}
