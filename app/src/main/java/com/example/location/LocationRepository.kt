package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationManagerCompat
import com.example.model.LocationFix
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun isLocationServiceEnabled(): Boolean {
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(fineGranted: Boolean): Flow<LocationFix?> = callbackFlow {
        val hasPlayServices =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                ConnectionResult.SUCCESS

        if (hasPlayServices) {
            try {
                fusedClient.lastLocation.addOnSuccessListener { last ->
                    if (last != null) {
                        trySend(last.toLocationFix())
                    }
                }
            } catch (_: SecurityException) {
                trySend(null)
            }

            val priority = if (fineGranted) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            val request = LocationRequest.Builder(priority, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        trySend(location.toLocationFix())
                    }
                }
            }

            try {
                fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (_: SecurityException) {
                trySend(null)
            }

            awaitClose {
                fusedClient.removeLocationUpdates(callback)
            }
        } else {
            val provider = when {
                fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> LocationManager.PASSIVE_PROVIDER
            }

            try {
                locationManager.getLastKnownLocation(provider)?.let { last ->
                    trySend(last.toLocationFix())
                }
            } catch (_: SecurityException) {
                trySend(null)
            }

            val listener = LocationListener { location ->
                trySend(location.toLocationFix())
            }

            try {
                locationManager.requestLocationUpdates(
                    provider,
                    2000L,
                    1f,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (_: Exception) {
                trySend(null)
            }

            awaitClose {
                locationManager.removeUpdates(listener)
            }
        }
    }

    private fun Location.toLocationFix(): LocationFix {
        val accuracyVal = if (hasAccuracy()) accuracy else null
        return LocationFix(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = if (hasAltitude()) altitude else null,
            accuracyMeters = accuracyVal,
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            bearingDegrees = if (hasBearing()) bearing else null,
            timestampMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            provider = provider ?: "gps",
            fixQuality = CoordinateFormatter.classifyFixQuality(accuracyVal),
        )
    }
}
