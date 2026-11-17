package com.example.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.example.model.AddressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class OfflineGeocoder(private val context: Context) {

    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var cachedState: AddressState? = null

    suspend fun resolveAddress(latitude: Double, longitude: Double): AddressState {
        val prevLat = lastLat
        val prevLon = lastLon
        val prevState = cachedState
        if (prevLat != null && prevLon != null && prevState is AddressState.Resolved) {
            val results = FloatArray(1)
            Location.distanceBetween(prevLat, prevLon, latitude, longitude, results)
            if (results[0] < 25f) {
                return prevState
            }
        }

        if (!Geocoder.isPresent()) {
            return AddressState.OfflineFallback("Geocoder service unavailable on device")
        }

        val geocoder = Geocoder(context, Locale.getDefault())

        val state = try {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                fetchModern(geocoder, latitude, longitude)
            } else {
                fetchLegacy(geocoder, latitude, longitude)
            }

            val first = addresses.firstOrNull()
            if (first != null) {
                val locality = first.locality ?: first.subAdminArea ?: first.adminArea
                val thoroughfare = first.thoroughfare ?: first.featureName
                val shortParts = listOfNotNull(thoroughfare, locality).distinct()
                val shortLine = shortParts.joinToString(", ").ifBlank {
                    first.adminArea ?: first.countryName ?: "Pinned Location"
                }
                val fullLines = buildList {
                    (0..first.maxAddressLineIndex).forEach { idx ->
                        first.getAddressLine(idx)?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                val fullAddress = fullLines.joinToString(", ").ifBlank { shortLine }
                AddressState.Resolved(
                    shortLine = shortLine,
                    fullAddress = fullAddress,
                    locality = locality,
                    country = first.countryName,
                )
            } else {
                AddressState.OfflineFallback("Offline coordinates only")
            }
        } catch (_: Exception) {
            AddressState.OfflineFallback("Offline — GPS coordinates active")
        }

        if (state is AddressState.Resolved) {
            lastLat = latitude
            lastLon = longitude
            cachedState = state
        }
        return state
    }

    @Suppress("DEPRECATION")
    private suspend fun fetchLegacy(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address> = withContext(Dispatchers.IO) {
        geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
    }

    private suspend fun fetchModern(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) {
                            continuation.resume(addresses)
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                    }
                },
            )
        } else if (continuation.isActive) {
            continuation.resume(emptyList())
        }
    }
}
