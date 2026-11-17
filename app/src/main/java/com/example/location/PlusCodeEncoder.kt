package com.example.location

import kotlin.math.floor

object PlusCodeEncoder {

    private const val CODE_ALPHABET = "23456789CFGHJMPQRVWX"
    private const val ENCODING_BASE = 20
    private const val LATITUDE_MAX = 90.0
    private const val LONGITUDE_MAX = 180.0
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POSITION = 8
    private const val PAIR_CODE_LENGTH = 10

    private val pairResolutions = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)

    fun encode(latitude: Double, longitude: Double): String {
        val clippedLat = latitude.coerceIn(-LATITUDE_MAX, LATITUDE_MAX)
        val adjustedLat = if (clippedLat == LATITUDE_MAX) {
            clippedLat - pairResolutions.last()
        } else {
            clippedLat
        }
        var normLon = longitude
        while (normLon < -LONGITUDE_MAX) normLon += 360.0
        while (normLon >= LONGITUDE_MAX) normLon -= 360.0

        var lat = adjustedLat + LATITUDE_MAX
        var lon = normLon + LONGITUDE_MAX

        val sb = StringBuilder(PAIR_CODE_LENGTH + 1)
        var digitCount = 0

        for (resolution in pairResolutions) {
            val latDigit = floor(lat / resolution).toInt().coerceIn(0, ENCODING_BASE - 1)
            val lonDigit = floor(lon / resolution).toInt().coerceIn(0, ENCODING_BASE - 1)
            lat -= latDigit * resolution
            lon -= lonDigit * resolution
            sb.append(CODE_ALPHABET[latDigit])
            sb.append(CODE_ALPHABET[lonDigit])
            digitCount += 2
            if (digitCount == SEPARATOR_POSITION) {
                sb.append(SEPARATOR)
            }
        }
        return sb.toString()
    }
}
