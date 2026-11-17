package com.example

import com.example.location.CoordinateFormatter
import com.example.location.PlusCodeEncoder
import com.example.model.CoordinateFormat
import com.example.model.FixQuality
import com.example.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateFormatterTest {

    @Test
    fun `decimal degrees formats positive and negative coordinates accurately`() {
        val lat = CoordinateFormatter.formatLatitude(37.774929, CoordinateFormat.DECIMAL_DEGREES)
        val lon = CoordinateFormatter.formatLongitude(-122.419416, CoordinateFormat.DECIMAL_DEGREES)

        assertEquals("37.774929° N", lat)
        assertEquals("122.419416° W", lon)
        assertEquals(
            "Lat 22.572600°   Long 88.363900°",
            CoordinateFormatter.formatCoordinates(22.5726, 88.3639, CoordinateFormat.DECIMAL_DEGREES),
        )
    }

    @Test
    fun `dms format produces degrees minutes seconds with hemisphere`() {
        val lat = CoordinateFormatter.formatLatitude(-33.8688, CoordinateFormat.DEGREES_MINUTES_SECONDS)
        assertTrue(lat.startsWith("33° 52'"))
        assertTrue(lat.endsWith("S"))
    }

    @Test
    fun `ddm format produces degrees decimal minutes`() {
        val lon = CoordinateFormatter.formatLongitude(139.6917, CoordinateFormat.DEGREES_DECIMAL_MINUTES)
        assertTrue(lon.startsWith("139° 41."))
        assertTrue(lon.endsWith("E"))
    }

    @Test
    fun `plus code encoder computes valid 10-char OLC code offline`() {
        assertEquals("7MJCH9F7+2H", PlusCodeEncoder.encode(22.5726, 88.3639))
    }

    @Test
    fun `cardinal direction maps azimuth angles accurately`() {
        assertEquals("N", CoordinateFormatter.toCardinalDirection(0f))
        assertEquals("N", CoordinateFormatter.toCardinalDirection(359f))
        assertEquals("NE", CoordinateFormatter.toCardinalDirection(45f))
        assertEquals("E", CoordinateFormatter.toCardinalDirection(90f))
        assertEquals("SW", CoordinateFormatter.toCardinalDirection(225f))
    }

    @Test
    fun `unit conversions format metric and imperial altitude and accuracy`() {
        assertEquals("100.0 m", CoordinateFormatter.formatAltitude(100.0, UnitSystem.METRIC))
        assertEquals("328 ft", CoordinateFormatter.formatAltitude(100.0, UnitSystem.IMPERIAL))
        assertEquals("±5 m", CoordinateFormatter.formatAccuracy(5f, UnitSystem.METRIC))
        assertEquals("±16 ft", CoordinateFormatter.formatAccuracy(5f, UnitSystem.IMPERIAL))
    }

    @Test
    fun `exif dms rational produces valid EXIF rational triplet`() {
        val rational = CoordinateFormatter.toExifDmsRational(-37.421998)
        assertEquals("37/1,25/1,19193/1000", rational)
    }

    @Test
    fun `fix quality classifies accuracy thresholds properly`() {
        assertEquals(FixQuality.HIGH_PRECISION, CoordinateFormatter.classifyFixQuality(8f))
        assertEquals(FixQuality.GOOD, CoordinateFormatter.classifyFixQuality(20f))
        assertEquals(FixQuality.COARSE, CoordinateFormatter.classifyFixQuality(65f))
    }
}
