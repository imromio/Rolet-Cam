package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.FixQuality
import com.example.model.MiniMapSource
import com.example.storage.SatelliteTileFetcher
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PrecisionAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SlateOutline
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@Composable
fun VectorMiniMap(
    azimuthDegrees: Float,
    accuracyMeters: Float?,
    fixQuality: FixQuality,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    lightStreetStyle: Boolean = true,
    miniMapSource: MiniMapSource = MiniMapSource.OFFLINE_SATELLITE,
    localityLabel: String? = null,
    latitude: Double? = null,
    longitude: Double? = null,
) {
    val description = stringResource(R.string.cd_vector_minimap)

    if (lightStreetStyle) {
        SatelliteMiniMap(
            description = description,
            miniMapSource = miniMapSource,
            localityLabel = localityLabel,
            latitude = latitude,
            longitude = longitude,
            fixQuality = fixQuality,
            modifier = modifier,
        )
    } else {
        DarkRadarMiniMap(
            description = description,
            azimuthDegrees = azimuthDegrees,
            accuracyMeters = accuracyMeters,
            fixQuality = fixQuality,
            compact = compact,
            modifier = modifier,
        )
    }
}

@Composable
private fun SatelliteMiniMap(
    description: String,
    miniMapSource: MiniMapSource,
    localityLabel: String?,
    latitude: Double?,
    longitude: Double?,
    fixQuality: FixQuality,
    modifier: Modifier = Modifier,
) {
    val latShift = ((abs((latitude ?: 22.57) * 1000.0) % 10.0) / 10.0).toFloat()
    val lonShift = ((abs((longitude ?: 88.36) * 1000.0) % 10.0) / 10.0).toFloat()

    val coordCacheKey = if (
        miniMapSource == MiniMapSource.GOOGLE_LIVE_SATELLITE &&
        latitude != null &&
        longitude != null
    ) {
        String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
    } else {
        null
    }

    var liveSatelliteBitmap by remember(coordCacheKey) {
        mutableStateOf<Bitmap?>(
            if (coordCacheKey != null && latitude != null && longitude != null) {
                SatelliteTileFetcher.getCachedCenteredBitmap(latitude, longitude)
            } else {
                null
            },
        )
    }
    var isFetchingLiveTile by remember(coordCacheKey) { mutableStateOf(false) }

    LaunchedEffect(miniMapSource, coordCacheKey) {
        if (miniMapSource == MiniMapSource.GOOGLE_LIVE_SATELLITE && latitude != null && longitude != null) {
            isFetchingLiveTile = true
            val fetched = SatelliteTileFetcher.fetchCenteredSatelliteBitmap(latitude, longitude)
            if (fetched != null) {
                liveSatelliteBitmap = fetched
            }
            isFetchingLiveTile = false
        } else {
            liveSatelliteBitmap = null
            isFetchingLiveTile = false
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16241D))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .semantics { contentDescription = description },
    ) {
        val activeBitmap = liveSatelliteBitmap?.takeIf { !it.isRecycled }
        if (miniMapSource == MiniMapSource.GOOGLE_LIVE_SATELLITE && activeBitmap != null) {
            Image(
                bitmap = activeBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val pinColor = if (fixQuality == FixQuality.NONE) Color(0xFF94A3B8) else Color(0xFFEF4444)

                drawCircle(
                    color = pinColor.copy(alpha = 0.30f),
                    radius = min(w, h) * 0.22f,
                    center = Offset(cx, cy),
                )

                val pinRadius = min(w, h) * 0.13f
                val pinHeadCenter = Offset(cx, cy - pinRadius * 0.75f)
                val pinTip = Offset(cx, cy + pinRadius * 0.55f)

                val pinPath = Path().apply {
                    moveTo(pinTip.x, pinTip.y)
                    lineTo(pinHeadCenter.x - pinRadius * 0.86f, pinHeadCenter.y + pinRadius * 0.35f)
                    lineTo(pinHeadCenter.x + pinRadius * 0.86f, pinHeadCenter.y + pinRadius * 0.35f)
                    close()
                }
                drawPath(pinPath, color = pinColor)
                drawCircle(color = pinColor, radius = pinRadius, center = pinHeadCenter)
                drawCircle(color = Color.White, radius = pinRadius * 0.42f, center = pinHeadCenter)
            }
        } else {
            Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1B2E24),
                        Color(0xFF14231C),
                        Color(0xFF21352B),
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h),
                ),
            )

            val canopyDark = Color(0xFF254231)
            val canopyLight = Color(0xFF2F533D)
            drawOval(
                color = canopyDark,
                topLeft = Offset(w * (0.04f + lonShift * 0.06f), h * 0.56f),
                size = Size(w * 0.34f, h * 0.28f),
            )
            drawOval(
                color = canopyLight,
                topLeft = Offset(w * 0.58f, h * (0.05f + latShift * 0.05f)),
                size = Size(w * 0.36f, h * 0.26f),
            )
            drawOval(
                color = canopyDark,
                topLeft = Offset(w * 0.08f, h * 0.08f),
                size = Size(w * 0.24f, h * 0.22f),
            )

            val riverPath = Path().apply {
                moveTo(0f, h * 0.84f)
                cubicTo(w * 0.3f, h * 0.74f, w * 0.65f, h * 0.90f, w, h * 0.78f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(riverPath, color = Color(0xFF132C40))

            val rooftops = listOf(
                Triple(Offset(w * 0.14f, h * 0.14f), Size(w * 0.16f, h * 0.11f), Color(0xFF475569)),
                Triple(Offset(w * 0.38f, h * 0.12f), Size(w * 0.18f, h * 0.10f), Color(0xFF6B4C3E)),
                Triple(Offset(w * 0.68f, h * 0.18f), Size(w * 0.18f, h * 0.12f), Color(0xFF526071)),
                Triple(Offset(w * 0.12f, h * 0.36f), Size(w * 0.15f, h * 0.14f), Color(0xFF64748B)),
                Triple(Offset(w * 0.40f, h * 0.34f), Size(w * 0.19f, h * 0.15f), Color(0xFF5B4339)),
                Triple(Offset(w * 0.70f, h * 0.42f), Size(w * 0.18f, h * 0.14f), Color(0xFF475569)),
                Triple(Offset(w * 0.44f, h * 0.62f), Size(w * 0.17f, h * 0.14f), Color(0xFF5A6577)),
            )
            rooftops.forEach { (pos, blockSize, roofColor) ->
                val dx = (pos.x + lonShift * w * 0.03f).coerceAtMost(w - blockSize.width)
                val dy = (pos.y + latShift * h * 0.03f).coerceAtMost(h - blockSize.height)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.35f),
                    topLeft = Offset(dx + 2.5f, dy + 2.5f),
                    size = blockSize,
                    cornerRadius = CornerRadius(3f, 3f),
                )
                drawRoundRect(
                    color = roofColor,
                    topLeft = Offset(dx, dy),
                    size = blockSize,
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }

            val roads = listOf(
                Offset(0f, h * 0.28f) to Offset(w, h * 0.25f),
                Offset(0f, h * 0.56f) to Offset(w, h * 0.60f),
                Offset(w * 0.32f, 0f) to Offset(w * 0.29f, h),
                Offset(w * 0.64f, 0f) to Offset(w * 0.67f, h),
            )
            roads.forEach { (start, end) ->
                drawLine(color = Color.Black.copy(alpha = 0.45f), start = start, end = end, strokeWidth = 5.5f)
                drawLine(color = Color(0xFFCBD5E1).copy(alpha = 0.55f), start = start, end = end, strokeWidth = 2.6f)
            }

            val cx = w / 2f
            val cy = h / 2f
            val pinColor = if (fixQuality == FixQuality.NONE) Color(0xFF94A3B8) else Color(0xFFEF4444)

            drawCircle(
                color = pinColor.copy(alpha = 0.28f),
                radius = min(w, h) * 0.22f,
                center = Offset(cx, cy),
            )

            val pinRadius = min(w, h) * 0.13f
            val pinHeadCenter = Offset(cx, cy - pinRadius * 0.75f)
            val pinTip = Offset(cx, cy + pinRadius * 0.55f)

            val pinPath = Path().apply {
                moveTo(pinTip.x, pinTip.y)
                lineTo(pinHeadCenter.x - pinRadius * 0.86f, pinHeadCenter.y + pinRadius * 0.35f)
                lineTo(pinHeadCenter.x + pinRadius * 0.86f, pinHeadCenter.y + pinRadius * 0.35f)
                close()
            }
            drawPath(pinPath, color = pinColor)
            drawCircle(color = pinColor, radius = pinRadius, center = pinHeadCenter)
            drawCircle(color = Color.White, radius = pinRadius * 0.42f, center = pinHeadCenter)
            }
        }

        Text(
            text = if (miniMapSource == MiniMapSource.GOOGLE_LIVE_SATELLITE && activeBitmap != null) "G-SAT" else "SAT",
            color = if (miniMapSource == MiniMapSource.GOOGLE_LIVE_SATELLITE && activeBitmap != null) PrecisionAmber else Color(0xFFE2E8F0),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )

        if (isFetchingLiveTile && activeBitmap == null) {
            CircularProgressIndicator(
                color = PrecisionAmber,
                strokeWidth = 1.6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(12.dp),
            )
        }

        if (!localityLabel.isNullOrBlank()) {
            Text(
                text = localityLabel,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun DarkRadarMiniMap(
    description: String,
    azimuthDegrees: Float,
    accuracyMeters: Float?,
    fixQuality: FixQuality,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (fixQuality) {
        FixQuality.HIGH_PRECISION -> SignalEmerald
        FixQuality.GOOD -> SignalCyan
        FixQuality.COARSE -> PrecisionAmber
        FixQuality.NONE -> SlateOutline
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianVoid)
            .border(1.dp, SlateOutline.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val maxRadius = min(w, h) * 0.44f

            val gridSteps = 4
            val gridColor = Color(0xFF1E293B)
            for (i in 1 until gridSteps) {
                val x = w * i / gridSteps
                val y = h * i / gridSteps
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            drawCircle(
                color = SlateOutline.copy(alpha = 0.45f),
                radius = maxRadius,
                center = center,
                style = Stroke(width = 1.2f, pathEffect = dashEffect),
            )

            val normalizedAccuracy = ((accuracyMeters ?: 35f).coerceIn(5f, 80f) / 80f)
            val accuracyRadius = maxRadius * (0.28f + normalizedAccuracy * 0.62f)
            drawCircle(
                color = accentColor.copy(alpha = 0.14f),
                radius = accuracyRadius,
                center = center,
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.55f),
                radius = accuracyRadius,
                center = center,
                style = Stroke(width = 1.5f),
            )

            val fovSweep = 48f
            val startAngle = azimuthDegrees - 90f - fovSweep / 2f
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.42f), Color.Transparent),
                    center = center,
                    radius = maxRadius * 0.95f,
                ),
                startAngle = startAngle,
                sweepAngle = fovSweep,
                useCenter = true,
                topLeft = Offset(center.x - maxRadius * 0.95f, center.y - maxRadius * 0.95f),
                size = Size(maxRadius * 1.9f, maxRadius * 1.9f),
            )

            drawCircle(
                color = Color.White,
                radius = if (compact) 4.5f else 6f,
                center = center,
            )
            drawCircle(
                color = accentColor,
                radius = if (compact) 3f else 4f,
                center = center,
            )
        }

        Text(
            text = "N",
            color = PrecisionAmber,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 3.dp),
        )
    }
}
