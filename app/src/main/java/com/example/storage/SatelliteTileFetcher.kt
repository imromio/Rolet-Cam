package com.example.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

object SatelliteTileFetcher {

    private const val TILE_SIZE = 256
    private const val DEFAULT_ZOOM = 17

    private val rawTileCache = object : LruCache<String, Bitmap>(32) {}
    private val centeredCropCache = object : LruCache<String, Bitmap>(16) {}

    fun getCachedCenteredBitmap(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
    ): Bitmap? {
        val key = buildCropCacheKey(latitude, longitude, zoom)
        return centeredCropCache.get(key)?.takeIf { !it.isRecycled }
    }

    suspend fun fetchCenteredSatelliteBitmap(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = buildCropCacheKey(latitude, longitude, zoom)
        centeredCropCache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }

        runCatching {
            val n = 1 shl zoom
            val xFrac = ((longitude + 180.0) / 360.0) * n
            val latClamped = latitude.coerceIn(-85.05112878, 85.05112878)
            val latRad = Math.toRadians(latClamped)
            val yFrac = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n

            val leftTile = floor(xFrac - 0.5).toInt()
            val topTile = floor(yFrac - 0.5).toInt()
            val cropX = ((xFrac - 0.5 - leftTile) * TILE_SIZE).roundToInt().coerceIn(0, TILE_SIZE)
            val cropY = ((yFrac - 0.5 - topTile) * TILE_SIZE).roundToInt().coerceIn(0, TILE_SIZE)

            val tiles = coroutineScope {
                val jobs = buildList {
                    for (dy in 0..1) {
                        for (dx in 0..1) {
                            val wrappedX = ((leftTile + dx) % n + n) % n
                            val clampedY = (topTile + dy).coerceIn(0, n - 1)
                            add(
                                Triple(dx, dy, async { fetchSingleTile(zoom, wrappedX, clampedY) }),
                            )
                        }
                    }
                }
                jobs.map { (dx, dy, deferred) ->
                    Triple(dx, dy, deferred.await())
                }
            }

            if (tiles.any { it.third == null }) {
                return@withContext null
            }

            val stitched = Bitmap.createBitmap(TILE_SIZE * 2, TILE_SIZE * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitched)
            tiles.forEach { (dx, dy, tileBitmap) ->
                if (tileBitmap != null && !tileBitmap.isRecycled) {
                    canvas.drawBitmap(tileBitmap, (dx * TILE_SIZE).toFloat(), (dy * TILE_SIZE).toFloat(), null)
                }
            }

            val centered = Bitmap.createBitmap(stitched, cropX, cropY, TILE_SIZE, TILE_SIZE)
            if (centered !== stitched) {
                stitched.recycle()
            }
            centeredCropCache.put(key, centered)
            centered
        }.getOrNull()
    }

    private fun buildCropCacheKey(latitude: Double, longitude: Double, zoom: Int): String {
        return String.format(Locale.US, "%.5f,%.5f@%d", latitude, longitude, zoom)
    }

    private fun fetchSingleTile(zoom: Int, x: Int, y: Int): Bitmap? {
        val tileKey = "$zoom/$x/$y"
        rawTileCache.get(tileKey)?.takeIf { !it.isRecycled }?.let { return it }

        val urls = listOf(
            "https://mt1.google.com/vt/lyrs=y&x=$x&y=$y&z=$zoom",
            "https://mt0.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom",
        )

        for (urlString in urls) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 4500
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
                    )
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { stream ->
                        val decoded = BitmapFactory.decodeStream(stream)
                        if (decoded != null) {
                            rawTileCache.put(tileKey, decoded)
                            return decoded
                        }
                    }
                }
            } catch (_: Throwable) {
                // Try fallback mirror
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }
}
