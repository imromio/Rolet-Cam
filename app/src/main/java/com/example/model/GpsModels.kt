package com.example.model

import android.net.Uri

enum class CoordinateFormat(val shortLabel: String, val displayName: String) {
    DECIMAL_DEGREES("DD", "Decimal Degrees"),
    DEGREES_MINUTES_SECONDS("DMS", "Degrees Min Sec"),
    DEGREES_DECIMAL_MINUTES("DDM", "Degrees Dec Min"),
}

enum class UnitSystem(val shortLabel: String, val displayName: String) {
    METRIC("Metric", "Metric (m, km/h)"),
    IMPERIAL("Imperial", "Imperial (ft, mph)"),
}

enum class WatermarkStyle(val displayName: String) {
    TACTICAL_CARD("Geotag Card"),
    MINIMAL_STRIP("Minimal Strip"),
    SURVEY_BADGE("Survey Badge"),
}

enum class CameraFlashMode(val label: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off"),
}

enum class CameraAspectRatio(val label: String, val portraitRatio: Float?) {
    FOUR_THREE("4:3", 3f / 4f),
    SIXTEEN_NINE("16:9", 9f / 16f),
    FULL("Full", null),
}

enum class MiniMapSource(val title: String, val subtitle: String) {
    OFFLINE_SATELLITE(
        title = "Offline Satellite Mini-Map",
        subtitle = "Fast on-device satellite graphic (works offline)",
    ),
    GOOGLE_LIVE_SATELLITE(
        title = "Google Live Satellite Map (Internet)",
        subtitle = "Fetches live Google satellite imagery for your GPS location",
    ),
}

enum class FixQuality(val label: String) {
    NONE("No Fix"),
    COARSE("Coarse"),
    GOOD("Good Fix"),
    HIGH_PRECISION("High Precision"),
}

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val timestampMillis: Long,
    val provider: String,
    val fixQuality: FixQuality,
)

data class CompassState(
    val azimuthDegrees: Float = 0f,
    val cardinalDirection: String = "N",
    val isSensorAvailable: Boolean = true,
)

sealed interface AddressState {
    data object Idle : AddressState
    data object Resolving : AddressState
    data class Resolved(
        val shortLine: String,
        val fullAddress: String,
        val locality: String?,
        val country: String?,
    ) : AddressState
    data class OfflineFallback(val reason: String) : AddressState
}

data class PermissionStatus(
    val cameraGranted: Boolean = false,
    val fineLocationGranted: Boolean = false,
    val coarseLocationGranted: Boolean = false,
    val legacyStorageGranted: Boolean = true,
    val locationServiceEnabled: Boolean = true,
    val hasRequestedPermissions: Boolean = false,
) {
    val hasAnyLocationPermission: Boolean
        get() = fineLocationGranted || coarseLocationGranted

    val allCorePermissionsGranted: Boolean
        get() = cameraGranted && hasAnyLocationPermission && legacyStorageGranted
}

data class OverlayConfig(
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL_DEGREES,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val watermarkStyle: WatermarkStyle = WatermarkStyle.TACTICAL_CARD,
    val aspectRatio: CameraAspectRatio = CameraAspectRatio.FOUR_THREE,
    val miniMapSource: MiniMapSource = MiniMapSource.OFFLINE_SATELLITE,
    val showAddress: Boolean = true,
    val showCoordinates: Boolean = true,
    val showElevation: Boolean = false,
    val showCompass: Boolean = false,
    val showTimestamp: Boolean = true,
    val showAccuracy: Boolean = true,
    val showMiniMap: Boolean = true,
    val showGridLines: Boolean = true,
    val saveOriginalCopy: Boolean = false,
    val customNote: String = "",
)

data class CapturedPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTakenMillis: Long,
    val sizeBytes: Long,
)

data class ExifSummary(
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val dateTimeOriginal: String?,
    val width: Int,
    val height: Int,
    val description: String?,
)
