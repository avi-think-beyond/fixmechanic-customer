package com.example

import com.example.data.location.NearbyMechanicMatcher
import com.example.data.model.Mechanic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyMechanicMatcherTest {

    // Customer location in New Delhi (Connaught Place: 28.6315, 77.2167)
    private val userLat = 28.6315
    private val userLng = 77.2167

    private fun sampleMechanic(
        id: String,
        lat: Double? = 28.6380,
        lng: Double? = 77.2220,
        isVerified: Boolean = true,
        isOnline: Boolean = true,
        isAvailable: Boolean = true,
        isBlocked: Boolean = false,
        serviceRadiusKm: Double? = null
    ) = Mechanic(
        id = id,
        name = "Mechanic $id",
        rating = 4.8f,
        rescuesCompleted = 50,
        vehicleInfo = "Mobile Van",
        isVerified = isVerified,
        latitude = lat,
        longitude = lng,
        isOnline = isOnline,
        isAvailable = isAvailable,
        isBlocked = isBlocked,
        serviceRadiusKm = serviceRadiusKm
    )

    @Test
    fun filtersOutOfflineUnavailableBlockedUnverifiedAndOutOfRangeMechanics() {
        val nearEligible1 = sampleMechanic(id = "near_1", lat = 28.6400, lng = 77.2240) // ~1.19 km
        val nearEligible2 = sampleMechanic(id = "near_2", lat = 28.6550, lng = 77.2300) // ~2.91 km
        val offlineMech = sampleMechanic(id = "offline", isOnline = false)
        val busyMech = sampleMechanic(id = "busy", isAvailable = false)
        val blockedMech = sampleMechanic(id = "blocked", isBlocked = true)
        val unverifiedMech = sampleMechanic(id = "unverified", isVerified = false)
        val noCoordsMech = sampleMechanic(id = "no_coords", lat = null, lng = null)
        val zeroCoordsMech = sampleMechanic(id = "zero_coords", lat = 0.0, lng = 0.0)
        // ~11.1 km north
        val farMech = sampleMechanic(id = "far_11km", lat = 28.7315, lng = 77.2167)
        // Within 5 km of user (~2.9 km), but mechanic's own serviceRadiusKm is 2.0 km
        val limitedServiceAreaMech = sampleMechanic(
            id = "outside_mech_service_area",
            lat = 28.6550,
            lng = 77.2300,
            serviceRadiusKm = 2.0
        )

        val candidates = listOf(
            nearEligible2,
            offlineMech,
            busyMech,
            blockedMech,
            unverifiedMech,
            noCoordsMech,
            zeroCoordsMech,
            farMech,
            limitedServiceAreaMech,
            nearEligible1
        )

        val within5Km = NearbyMechanicMatcher.filterAndSortNearby(
            mechanics = candidates,
            userLat = userLat,
            userLng = userLng,
            searchRadiusKm = 5.0
        )

        assertEquals(listOf("near_1", "near_2"), within5Km.map { it.id })
        assertTrue((within5Km[0].currentDistanceKm ?: 99f) < (within5Km[1].currentDistanceKm ?: 0f))
        assertTrue((within5Km[0].estimatedArrivalMinutes ?: 0) >= 3)
    }

    @Test
    fun expandingRadiusIncludesFurtherEligibleMechanic() {
        val farEligible = sampleMechanic(id = "far_8km", lat = 28.7035, lng = 77.2167) // ~8.0 km

        val within5Km = NearbyMechanicMatcher.filterAndSortNearby(
            mechanics = listOf(farEligible),
            userLat = userLat,
            userLng = userLng,
            searchRadiusKm = 5.0
        )
        assertTrue(within5Km.isEmpty())

        val within10Km = NearbyMechanicMatcher.filterAndSortNearby(
            mechanics = listOf(farEligible),
            userLat = userLat,
            userLng = userLng,
            searchRadiusKm = 10.0
        )
        assertEquals(1, within10Km.size)
        assertEquals("far_8km", within10Km.first().id)
    }
}
