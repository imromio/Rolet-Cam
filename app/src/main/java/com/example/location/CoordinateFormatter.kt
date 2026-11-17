package com.example.location

import com.example.model.CoordinateFormat
import com.example.model.FixQuality
import com.example.model.UnitSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object CoordinateFormatter {

    private val cardinals = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun formatLatitude(latitude: Double, format: CoordinateFormat): String =
        formatAxis(latitude, positiveHemisphere = "N", negativeHemisphere = "S", format = format)

    fun formatLongitude(longitude: Double, format: CoordinateFormat): String =
        formatAxis(longitude, positiveHemisphere = "E", negativeHemisphere = "W", format = format)

    fun formatCoordinates(
        latitude: Double,
        longitude: Double,
        format: CoordinateFormat,
    ): String {
        return when (format) {
            CoordinateFormat.DECIMAL_DEGREES -> String.format(
                Locale.US,
                "Lat %.6f°   Long %.6f°",
                latitude,
                longitude,
            )
            else -> "${formatLatitude(latitude, format)}  ${formatLongitude(longitude, format)}"
        }
    }

    private fun formatAxis(
        value: Double,
        positiveHemisphere: String,
        negativeHemisphere: String,
        format: CoordinateFormat,
    ): String {
        val hemisphere = if (value >= 0.0) positiveHemisphere else negativeHemisphere
        val absVal = abs(value)
        return when (format) {
            CoordinateFormat.DECIMAL_DEGREES -> {
                String.format(Locale.US, "%.6f° %s", absVal, hemisphere)
            }
            CoordinateFormat.DEGREES_MINUTES_SECONDS -> {
                val degrees = floor(absVal).toInt()
                val minutesFloat = (absVal - degrees) * 60.0
                val minutes = floor(minutesFloat).toInt()
                val seconds = (minutesFloat - minutes) * 60.0
                String.format(Locale.US, "%d° %02d' %05.2f\" %s", degrees, minutes, seconds, hemisphere)
            }
            CoordinateFormat.DEGREES_DECIMAL_MINUTES -> {
                val degrees = floor(absVal).toInt()
                val minutes = (absVal - degrees) * 60.0
                String.format(Locale.US, "%d° %07.4f' %s", degrees, minutes, hemisphere)
            }
        }
    }

    fun formatAltitude(altitudeMeters: Double?, unitSystem: UnitSystem): String {
        if (altitudeMeters == null) return "--"
        return when (unitSystem) {
            UnitSystem.METRIC -> String.format(Locale.US, "%.1f m", altitudeMeters)
            UnitSystem.IMPERIAL -> String.format(Locale.US, "%.0f ft", altitudeMeters * 3.28084)
        }
    }

    fun formatAccuracy(accuracyMeters: Float?, unitSystem: UnitSystem): String {
        if (accuracyMeters == null) return "--"
        return when (unitSystem) {
            UnitSystem.METRIC -> String.format(Locale.US, "±%.0f m", accuracyMeters)
            UnitSystem.IMPERIAL -> String.format(Locale.US, "±%.0f ft", accuracyMeters * 3.28084f)
        }
    }

    fun formatSpeed(speedMetersPerSecond: Float?, unitSystem: UnitSystem): String {
        if (speedMetersPerSecond == null) return "--"
        return when (unitSystem) {
            UnitSystem.METRIC -> String.format(Locale.US, "%.1f km/h", speedMetersPerSecond * 3.6f)
            UnitSystem.IMPERIAL -> String.format(Locale.US, "%.1f mph", speedMetersPerSecond * 2.23694f)
        }
    }

    fun toCardinalDirection(azimuthDegrees: Float): String {
        val normalized = ((azimuthDegrees % 360f) + 360f) % 360f
        val index = ((normalized + 22.5f) / 45f).toInt() % 8
        return cardinals[index]
    }

    fun formatAzimuth(azimuthDegrees: Float): String {
        val normalized = ((azimuthDegrees % 360f) + 360f) % 360f
        val rounded = normalized.roundToInt() % 360
        return String.format(Locale.US, "%03d° %s", rounded, toCardinalDirection(normalized))
    }

    fun formatTimestamp(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a z", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun formatExifDateTime(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        return formatter.format(Date(timestampMillis))
    }

    fun formatExifGpsDateStamp(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(timestampMillis))
    }

    fun formatExifGpsTimeStamp(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("HH/1,mm/1,ss/1", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(timestampMillis))
    }

    fun classifyFixQuality(accuracyMeters: Float?): FixQuality {
        if (accuracyMeters == null) return FixQuality.COARSE
        return when {
            accuracyMeters <= 12f -> FixQuality.HIGH_PRECISION
            accuracyMeters <= 35f -> FixQuality.GOOD
            else -> FixQuality.COARSE
        }
    }

    fun toExifDmsRational(coordinate: Double): String {
        val absCoord = abs(coordinate)
        val degrees = floor(absCoord).toInt()
        val minutesDouble = (absCoord - degrees) * 60.0
        val minutes = floor(minutesDouble).toInt()
        val secondsMillis = ((minutesDouble - minutes) * 60.0 * 1000.0).roundToInt()
        return "$degrees/1,$minutes/1,$secondsMillis/1000"
    }
}
