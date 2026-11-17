package com.example.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.exifinterface.media.ExifInterface
import com.example.location.CoordinateFormatter
import com.example.location.PlusCodeEncoder
import com.example.model.AddressState
import com.example.model.CameraAspectRatio
import com.example.model.CompassState
import com.example.model.LocationFix
import com.example.model.MiniMapSource
import com.example.model.OverlayConfig
import com.example.model.WatermarkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PhotoOverlayProcessor(
    private val context: Context,
    private val mediaStoreRepository: MediaStorePhotoRepository,
) {

    suspend fun processAndSavePhoto(
        sourceJpegFile: File,
        locationFix: LocationFix?,
        compassState: CompassState,
        addressState: AddressState,
        overlayConfig: OverlayConfig,
        captureTimestampMillis: Long,
        fullScreenAspectRatio: Float? = null,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val stampedTemp = File(context.cacheDir, "stamped_${captureTimestampMillis}.jpg")
        try {
            if (!sourceJpegFile.exists() || sourceJpegFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Captured image file is empty"))
            }

            val oriented = decodeOrientedSampledBitmap(sourceJpegFile, maxDimension = 3072)
                ?: return@withContext Result.failure(IllegalStateException("Unable to decode captured JPEG"))

            val targetAspect = when (overlayConfig.aspectRatio) {
                CameraAspectRatio.FOUR_THREE -> 3f / 4f
                CameraAspectRatio.SIXTEEN_NINE -> 9f / 16f
                CameraAspectRatio.FULL -> fullScreenAspectRatio
            }

            val workingBitmap = if (targetAspect != null) {
                cropToAspectRatio(oriented, targetAspect)
            } else {
                oriented
            }

            val showMap = overlayConfig.showMiniMap && overlayConfig.watermarkStyle != WatermarkStyle.MINIMAL_STRIP
            val liveSatelliteTile = if (
                showMap &&
                overlayConfig.miniMapSource == MiniMapSource.GOOGLE_LIVE_SATELLITE &&
                locationFix != null
            ) {
                SatelliteTileFetcher.fetchCenteredSatelliteBitmap(
                    latitude = locationFix.latitude,
                    longitude = locationFix.longitude,
                )
            } else {
                null
            }

            drawOverlayCardOnBitmap(
                bitmap = workingBitmap,
                locationFix = locationFix,
                compassState = compassState,
                addressState = addressState,
                overlayConfig = overlayConfig,
                captureTimestampMillis = captureTimestampMillis,
                liveSatelliteTile = liveSatelliteTile,
            )

            FileOutputStream(stampedTemp).use { out ->
                workingBitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            workingBitmap.recycle()

            writeExifMetadata(
                file = stampedTemp,
                locationFix = locationFix,
                addressState = addressState,
                captureTimestampMillis = captureTimestampMillis,
            )

            val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(captureTimestampMillis))
            val savedUri = mediaStoreRepository.saveJpegToMediaStore(
                jpegFile = stampedTemp,
                displayName = "IMG_GEOLENS_${fileStamp}_${captureTimestampMillis % 1000}.jpg",
                dateTakenMillis = captureTimestampMillis,
            ).getOrThrow()

            if (overlayConfig.saveOriginalCopy) {
                val originalCopy = File(context.cacheDir, "raw_${captureTimestampMillis}.jpg")
                try {
                    sourceJpegFile.copyTo(originalCopy, overwrite = true)
                    writeExifMetadata(
                        file = originalCopy,
                        locationFix = locationFix,
                        addressState = addressState,
                        captureTimestampMillis = captureTimestampMillis,
                    )
                    mediaStoreRepository.saveJpegToMediaStore(
                        jpegFile = originalCopy,
                        displayName = "IMG_GEOLENS_RAW_${fileStamp}_${captureTimestampMillis % 1000}.jpg",
                        dateTakenMillis = captureTimestampMillis,
                    )
                } finally {
                    originalCopy.delete()
                }
            }

            Result.success(savedUri)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            stampedTemp.delete()
            sourceJpegFile.delete()
        }
    }

    private fun decodeOrientedSampledBitmap(file: File, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
        while (longest / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return raw
        }

        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) {
            raw.recycle()
        }
        if (!rotated.isMutable) {
            val mutable = rotated.copy(Bitmap.Config.ARGB_8888, true)
            rotated.recycle()
            return mutable
        }
        return rotated
    }

    private fun cropToAspectRatio(source: Bitmap, portraitTargetRatio: Float): Bitmap {
        val isLandscape = source.width > source.height
        val targetRatio = if (isLandscape) 1f / portraitTargetRatio else portraitTargetRatio
        val currentRatio = source.width.toFloat() / source.height.toFloat()

        if (abs(currentRatio - targetRatio) < 0.015f) {
            return source
        }

        val cropWidth: Int
        val cropHeight: Int
        if (currentRatio > targetRatio) {
            cropHeight = source.height
            cropWidth = (cropHeight * targetRatio).roundToInt().coerceIn(1, source.width)
        } else {
            cropWidth = source.width
            cropHeight = (cropWidth / targetRatio).roundToInt().coerceIn(1, source.height)
        }

        val offsetX = ((source.width - cropWidth) / 2).coerceAtLeast(0)
        val offsetY = ((source.height - cropHeight) / 2).coerceAtLeast(0)

        val cropped = Bitmap.createBitmap(source, offsetX, offsetY, cropWidth, cropHeight)
        if (cropped !== source) {
            source.recycle()
        }
        return if (cropped.isMutable) {
            cropped
        } else {
            val mutable = cropped.copy(Bitmap.Config.ARGB_8888, true)
            cropped.recycle()
            mutable
        }
    }

    private fun drawOverlayCardOnBitmap(
        bitmap: Bitmap,
        locationFix: LocationFix?,
        compassState: CompassState,
        addressState: AddressState,
        overlayConfig: OverlayConfig,
        captureTimestampMillis: Long,
        liveSatelliteTile: Bitmap?,
    ) {
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val minDim = min(w, h)

        val outerMargin = minDim * 0.028f
        val gap = minDim * 0.016f
        val cornerRadius = minDim * 0.024f
        val showMap = overlayConfig.showMiniMap && overlayConfig.watermarkStyle != WatermarkStyle.MINIMAL_STRIP
        val cardHeight = max(minDim * 0.165f, 150f)
        val mapSize = if (showMap) cardHeight else 0f

        val cardBottom = h - outerMargin
        val cardTop = cardBottom - cardHeight
        val mapLeft = outerMargin
        val infoLeft = if (showMap) mapLeft + mapSize + gap else outerMargin
        val infoRight = w - outerMargin

        if (showMap) {
            drawSatelliteMapTile(
                canvas = canvas,
                rect = RectF(mapLeft, cardTop, mapLeft + mapSize, cardBottom),
                cornerRadius = cornerRadius,
                latitude = locationFix?.latitude,
                longitude = locationFix?.longitude,
                locality = (addressState as? AddressState.Resolved)?.locality,
                liveSatelliteTile = liveSatelliteTile,
            )
        }

        val infoRect = RectF(infoLeft, cardTop, infoRight, cardBottom)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (overlayConfig.watermarkStyle) {
                WatermarkStyle.TACTICAL_CARD -> Color.argb(204, 20, 22, 25)
                WatermarkStyle.MINIMAL_STRIP -> Color.argb(175, 0, 0, 0)
                WatermarkStyle.SURVEY_BADGE -> Color.argb(228, 17, 24, 39)
            }
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, minDim * 0.0022f)
            color = if (overlayConfig.watermarkStyle == WatermarkStyle.SURVEY_BADGE) {
                Color.argb(150, 16, 185, 129)
            } else {
                Color.argb(55, 255, 255, 255)
            }
        }
        canvas.drawRoundRect(infoRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(infoRect, cornerRadius, cornerRadius, borderPaint)

        val padX = cardHeight * 0.13f
        val textMaxWidth = (infoRect.width() - padX * 2f).coerceAtLeast(100f)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = cardHeight * 0.175f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            textSize = cardHeight * 0.118f
        }
        val coordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (overlayConfig.watermarkStyle == WatermarkStyle.SURVEY_BADGE) {
                Color.rgb(245, 158, 11)
            } else {
                Color.WHITE
            }
            textSize = cardHeight * 0.132f
            typeface = Typeface.MONOSPACE
        }
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            textSize = cardHeight * 0.12f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = cardHeight * 0.105f
            typeface = Typeface.MONOSPACE
        }

        val plusCode = locationFix?.let { PlusCodeEncoder.encode(it.latitude, it.longitude) }
        val headlineAddress = when (addressState) {
            is AddressState.Resolved -> addressState.shortLine
            is AddressState.Resolving -> "Resolving location…"
            is AddressState.OfflineFallback, AddressState.Idle ->
                if (locationFix != null) "Offline GPS Location" else "No GPS Signal"
        }
        val subAddress = when (addressState) {
            is AddressState.Resolved ->
                if (plusCode != null) "$plusCode, ${addressState.fullAddress}" else addressState.fullAddress
            else ->
                plusCode?.let { "$it • Offline GPS Coordinates" } ?: "Rolet Cam Offline Capture"
        }

        var cursorY = infoRect.top + cardHeight * 0.24f
        val x = infoRect.left + padX

        if (overlayConfig.showAddress) {
            val titleText = TextUtils.ellipsize(headlineAddress, titlePaint, textMaxWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(titleText, x, cursorY, titlePaint)
            cursorY += cardHeight * 0.155f

            val subText = TextUtils.ellipsize(subAddress, subPaint, textMaxWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(subText, x, cursorY, subPaint)
            cursorY += cardHeight * 0.17f
        }

        if (overlayConfig.showCoordinates) {
            val coordText = if (locationFix != null) {
                CoordinateFormatter.formatCoordinates(
                    locationFix.latitude,
                    locationFix.longitude,
                    overlayConfig.coordinateFormat,
                )
            } else {
                "Lat --.------°   Long --.------°"
            }
            val clippedCoord = TextUtils.ellipsize(coordText, coordPaint, textMaxWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clippedCoord, x, cursorY, coordPaint)
            cursorY += cardHeight * 0.16f
        }

        if (overlayConfig.showTimestamp) {
            val timeText = CoordinateFormatter.formatTimestamp(locationFix?.timestampMillis ?: captureTimestampMillis)
            val clippedTime = TextUtils.ellipsize(timeText, timePaint, textMaxWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clippedTime, x, cursorY, timePaint)
        }

        val footerY = infoRect.bottom - cardHeight * 0.10f
        val metaParts = buildList {
            if (overlayConfig.showAccuracy) {
                add(
                    locationFix?.accuracyMeters?.let {
                        CoordinateFormatter.formatAccuracy(it, overlayConfig.unitSystem)
                    } ?: "±-- m"
                )
            }
            if (overlayConfig.showElevation && locationFix?.altitudeMeters != null) {
                add(CoordinateFormatter.formatAltitude(locationFix.altitudeMeters, overlayConfig.unitSystem))
            }
            if (overlayConfig.showCompass) {
                add(CoordinateFormatter.formatAzimuth(compassState.azimuthDegrees))
            }
            if (overlayConfig.customNote.isNotBlank()) {
                add(overlayConfig.customNote)
            }
        }
        val badgeText = "Captured by Rolet Cam"
        val badgeWidth = footerPaint.measureText(badgeText)
        canvas.drawText(badgeText, infoRect.right - padX - badgeWidth, footerY, footerPaint)

        if (metaParts.isNotEmpty()) {
            val metaMaxWidth = (textMaxWidth - badgeWidth - padX * 0.6f).coerceAtLeast(60f)
            val metaText = TextUtils.ellipsize(metaParts.joinToString(" • "), footerPaint, metaMaxWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(metaText, x, footerY, footerPaint)
        }
    }

    private fun drawSatelliteMapTile(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        latitude: Double?,
        longitude: Double?,
        locality: String?,
        liveSatelliteTile: Bitmap?,
    ) {
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val w = rect.width()
        val h = rect.height()

        if (liveSatelliteTile != null && !liveSatelliteTile.isRecycled) {
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(liveSatelliteTile, null, rect, bitmapPaint)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    intArrayOf(
                        Color.rgb(27, 46, 36),
                        Color.rgb(20, 35, 28),
                        Color.rgb(33, 53, 43),
                    ),
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(rect, bgPaint)

            val latShift = ((abs((latitude ?: 22.57) * 1000.0) % 10.0) / 10.0).toFloat()
            val lonShift = ((abs((longitude ?: 88.36) * 1000.0) % 10.0) / 10.0).toFloat()

            val canopyDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(37, 66, 49) }
            val canopyLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(47, 83, 61) }
            canvas.drawOval(
                RectF(rect.left + w * (0.04f + lonShift * 0.06f), rect.top + h * 0.56f, rect.left + w * 0.38f, rect.top + h * 0.84f),
                canopyDark,
            )
            canvas.drawOval(
                RectF(rect.left + w * 0.58f, rect.top + h * (0.05f + latShift * 0.05f), rect.left + w * 0.94f, rect.top + h * 0.31f),
                canopyLight,
            )

            val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(19, 44, 64) }
            val riverPath = Path().apply {
                moveTo(rect.left, rect.top + h * 0.84f)
                cubicTo(
                    rect.left + w * 0.3f,
                    rect.top + h * 0.74f,
                    rect.left + w * 0.65f,
                    rect.top + h * 0.90f,
                    rect.right,
                    rect.top + h * 0.78f,
                )
                lineTo(rect.right, rect.bottom)
                lineTo(rect.left, rect.bottom)
                close()
            }
            canvas.drawPath(riverPath, riverPaint)

            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(95, 0, 0, 0) }
            val rooftops = listOf(
                RectF(rect.left + w * 0.14f, rect.top + h * 0.14f, rect.left + w * 0.30f, rect.top + h * 0.25f) to Color.rgb(71, 85, 105),
                RectF(rect.left + w * 0.38f, rect.top + h * 0.12f, rect.left + w * 0.56f, rect.top + h * 0.22f) to Color.rgb(107, 76, 62),
                RectF(rect.left + w * 0.68f, rect.top + h * 0.18f, rect.left + w * 0.86f, rect.top + h * 0.30f) to Color.rgb(82, 96, 113),
                RectF(rect.left + w * 0.12f, rect.top + h * 0.36f, rect.left + w * 0.27f, rect.top + h * 0.50f) to Color.rgb(100, 116, 139),
                RectF(rect.left + w * 0.40f, rect.top + h * 0.34f, rect.left + w * 0.59f, rect.top + h * 0.49f) to Color.rgb(91, 67, 57),
                RectF(rect.left + w * 0.70f, rect.top + h * 0.42f, rect.left + w * 0.88f, rect.top + h * 0.56f) to Color.rgb(71, 85, 105),
            )
            rooftops.forEach { (roofRect, color) ->
                val shadowRect = RectF(roofRect).apply { offset(3f, 3f) }
                canvas.drawRoundRect(shadowRect, 4f, 4f, shadowPaint)
                canvas.drawRoundRect(roofRect, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            }

            val roadShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(115, 0, 0, 0)
                strokeWidth = max(4f, w * 0.036f)
            }
            val roadOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(145, 203, 213, 225)
                strokeWidth = max(2f, w * 0.018f)
            }
            val roadLines = listOf(
                floatArrayOf(rect.left, rect.top + h * 0.28f, rect.right, rect.top + h * 0.25f),
                floatArrayOf(rect.left, rect.top + h * 0.56f, rect.right, rect.top + h * 0.60f),
                floatArrayOf(rect.left + w * 0.32f, rect.top, rect.left + w * 0.29f, rect.bottom),
                floatArrayOf(rect.left + w * 0.64f, rect.top, rect.left + w * 0.67f, rect.bottom),
            )
            roadLines.forEach { pts ->
                canvas.drawLine(pts[0], pts[1], pts[2], pts[3], roadShadow)
                canvas.drawLine(pts[0], pts[1], pts[2], pts[3], roadOverlay)
            }
        }

        val cx = rect.centerX()
        val cy = rect.centerY()
        val pinRadius = w * 0.125f
        val headY = cy - pinRadius * 0.7f
        val pinColor = Color.rgb(239, 68, 68)
        canvas.drawCircle(cx, cy, w * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 239, 68, 68) })

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pinColor }
        val conePath = Path().apply {
            moveTo(cx, cy + pinRadius * 0.55f)
            lineTo(cx - pinRadius * 0.85f, headY + pinRadius * 0.35f)
            lineTo(cx + pinRadius * 0.85f, headY + pinRadius * 0.35f)
            close()
        }
        canvas.drawPath(conePath, pinPaint)
        canvas.drawCircle(cx, headY, pinRadius, pinPaint)
        canvas.drawCircle(cx, headY, pinRadius * 0.42f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        canvas.drawRoundRect(
            rect,
            cornerRadius,
            cornerRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(80, 255, 255, 255)
            },
        )

        if (!locality.isNullOrBlank()) {
            val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = h * 0.11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val label = TextUtils.ellipsize(locality, labelPaint, w * 0.84f, TextUtils.TruncateAt.END).toString()
            val tw = labelPaint.measureText(label)
            val pillRect = RectF(cx - tw / 2f - 8f, rect.bottom - h * 0.22f, cx + tw / 2f + 8f, rect.bottom - h * 0.05f)
            canvas.drawRoundRect(pillRect, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(175, 0, 0, 0) })
            canvas.drawText(label, cx - tw / 2f, pillRect.bottom - h * 0.045f, labelPaint)
        }

        canvas.restore()
    }

    private fun writeExifMetadata(
        file: File,
        locationFix: LocationFix?,
        addressState: AddressState,
        captureTimestampMillis: Long,
    ) {
        val exif = ExifInterface(file.absolutePath)
        val exifTime = CoordinateFormatter.formatExifDateTime(captureTimestampMillis)
        exif.setAttribute(ExifInterface.TAG_DATETIME, exifTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exifTime)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())

        if (locationFix != null) {
            exif.setAttribute(
                ExifInterface.TAG_GPS_LATITUDE,
                CoordinateFormatter.toExifDmsRational(locationFix.latitude),
            )
            exif.setAttribute(
                ExifInterface.TAG_GPS_LATITUDE_REF,
                if (locationFix.latitude >= 0.0) "N" else "S",
            )
            exif.setAttribute(
                ExifInterface.TAG_GPS_LONGITUDE,
                CoordinateFormatter.toExifDmsRational(locationFix.longitude),
            )
            exif.setAttribute(
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                if (locationFix.longitude >= 0.0) "E" else "W",
            )
            exif.setAttribute(
                ExifInterface.TAG_GPS_DATESTAMP,
                CoordinateFormatter.formatExifGpsDateStamp(locationFix.timestampMillis),
            )
            exif.setAttribute(
                ExifInterface.TAG_GPS_TIMESTAMP,
                CoordinateFormatter.formatExifGpsTimeStamp(locationFix.timestampMillis),
            )
            locationFix.altitudeMeters?.let { alt ->
                val altMilli = (abs(alt) * 100.0).toInt()
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "$altMilli/100")
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (alt >= 0.0) "0" else "1")
            }
        }

        val desc = when (addressState) {
            is AddressState.Resolved -> addressState.fullAddress
            else -> locationFix?.let {
                "GPS ${it.latitude}, ${it.longitude}"
            }
        }
        if (!desc.isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, desc)
        }
        exif.saveAttributes()
    }
}
