package com.example.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.LocationData
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

sealed class LocationFetchResult {
    data class Success(val locationData: LocationData) : LocationFetchResult()
    object PermissionRequired : LocationFetchResult()
    object GpsDisabled : LocationFetchResult()
    data class Unavailable(val reason: String) : LocationFetchResult()
}

class LocationHelper(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isGpsEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getCurrentLocation(): LocationFetchResult = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext LocationFetchResult.PermissionRequired
        }

        if (!isGpsEnabled()) {
            return@withContext LocationFetchResult.GpsDisabled
        }

        val rawLocation: Location? = fetchLocationFromFusedClient()
        if (rawLocation == null) {
            return@withContext LocationFetchResult.Unavailable("Unable to obtain GPS fix. Please verify device location settings or enter address manually.")
        }

        val geocoded = reverseGeocode(rawLocation.latitude, rawLocation.longitude)

        LocationFetchResult.Success(
            LocationData(
                latitude = rawLocation.latitude,
                longitude = rawLocation.longitude,
                address = geocoded.first,
                landmark = geocoded.second,
                city = geocoded.third,
                isGpsAccurate = true,
                isObtained = true
            )
        )
    }

    @Suppress("MissingPermission")
    private suspend fun fetchLocationFromFusedClient(): Location? {
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        continuation.resume(loc)
                    } else {
                        // Fallback to last known location
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { lastLoc -> continuation.resume(lastLoc) }
                            .addOnFailureListener { continuation.resume(null) }
                    }
                }
                .addOnFailureListener {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLoc -> continuation.resume(lastLoc) }
                        .addOnFailureListener { continuation.resume(null) }
                }

            continuation.invokeOnCancellation {
                cts.cancel()
            }
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): Triple<String, String, String> {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val street = addr.thoroughfare ?: addr.subLocality ?: addr.featureName ?: "Latitude: ${"%.4f".format(lat)}, Longitude: ${"%.4f".format(lng)}"
                val locality = addr.locality ?: addr.subAdminArea ?: "India"
                val landmark = addr.featureName?.takeIf { it != street } ?: "Near ${addr.subLocality ?: locality}"
                val fullAddress = listOfNotNull(addr.subThoroughfare, addr.thoroughfare, addr.subLocality, addr.locality).joinToString(", ")
                    .ifBlank { street }
                Triple(fullAddress, landmark, locality)
            } else {
                Triple("Lat: ${"%.4f".format(lat)}, Lng: ${"%.4f".format(lng)}", "GPS Location", "India")
            }
        } catch (e: Exception) {
            Triple("GPS Location (Lat: ${"%.4f".format(lat)}, Lng: ${"%.4f".format(lng)})", "Coordinates obtained", "India")
        }
    }
}
