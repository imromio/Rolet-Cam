package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.location.CoordinateFormatter
import com.example.location.PlusCodeEncoder
import com.example.model.AddressState
import com.example.model.CompassState
import com.example.model.FixQuality
import com.example.model.LocationFix
import com.example.model.OverlayConfig
import com.example.model.WatermarkStyle
import com.example.ui.theme.PrecisionAmber
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryTextStyle

@Composable
fun WatermarkPreviewCard(
    locationFix: LocationFix?,
    compassState: CompassState,
    addressState: AddressState,
    overlayConfig: OverlayConfig,
    currentTimeMillis: Long,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (overlayConfig.watermarkStyle) {
        WatermarkStyle.TACTICAL_CARD -> Color(0xC8141619)
        WatermarkStyle.MINIMAL_STRIP -> Color(0xA6000000)
        WatermarkStyle.SURVEY_BADGE -> Color(0xE0111827)
    }

    val borderColor = when (overlayConfig.watermarkStyle) {
        WatermarkStyle.TACTICAL_CARD -> Color.White.copy(alpha = 0.18f)
        WatermarkStyle.MINIMAL_STRIP -> Color.White.copy(alpha = 0.14f)
        WatermarkStyle.SURVEY_BADGE -> SignalEmerald.copy(alpha = 0.55f)
    }

    val plusCode = locationFix?.let { PlusCodeEncoder.encode(it.latitude, it.longitude) }

    val headlineAddress = when (addressState) {
        is AddressState.Resolved -> addressState.shortLine
        is AddressState.Resolving -> "Resolving location…"
        is AddressState.OfflineFallback ->
            if (locationFix != null) "Offline GPS Location" else "Acquiring GPS Signal…"
        AddressState.Idle ->
            if (locationFix != null) "Pinned GPS Location" else "Waiting for GPS Fix"
    }

    val subAddress = when (addressState) {
        is AddressState.Resolved ->
            if (plusCode != null) "$plusCode, ${addressState.fullAddress}" else addressState.fullAddress
        is AddressState.Resolving ->
            plusCode?.let { "$it, Resolving address…" } ?: "Resolving street address…"
        is AddressState.OfflineFallback ->
            plusCode?.let { "$it • Offline GPS coordinates active" } ?: "Grant location permission for GPS"
        AddressState.Idle ->
            plusCode?.let { "$it • GPS coordinates locked" } ?: "Enable GPS to stamp coordinates"
    }

    val coordinatesLine = if (locationFix != null) {
        CoordinateFormatter.formatCoordinates(
            latitude = locationFix.latitude,
            longitude = locationFix.longitude,
            format = overlayConfig.coordinateFormat,
        )
    } else {
        "Lat --.------°   Long --.------°"
    }

    val localityChip = (addressState as? AddressState.Resolved)?.locality

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (overlayConfig.showMiniMap && overlayConfig.watermarkStyle != WatermarkStyle.MINIMAL_STRIP) {
            VectorMiniMap(
                azimuthDegrees = compassState.azimuthDegrees,
                accuracyMeters = locationFix?.accuracyMeters,
                fixQuality = locationFix?.fixQuality ?: FixQuality.NONE,
                compact = true,
                lightStreetStyle = overlayConfig.watermarkStyle == WatermarkStyle.TACTICAL_CARD,
                miniMapSource = overlayConfig.miniMapSource,
                localityLabel = localityChip,
                latitude = locationFix?.latitude,
                longitude = locationFix?.longitude,
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight()
                    .size(92.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (overlayConfig.showAddress) {
                Text(
                    text = headlineAddress,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subAddress,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
            }

            if (overlayConfig.showCoordinates) {
                Text(
                    text = coordinatesLine,
                    style = TelemetryTextStyle.copy(
                        color = if (overlayConfig.watermarkStyle == WatermarkStyle.SURVEY_BADGE) {
                            PrecisionAmber
                        } else {
                            Color.White
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (overlayConfig.showTimestamp) {
                Text(
                    text = CoordinateFormatter.formatTimestamp(
                        locationFix?.timestampMillis ?: currentTimeMillis,
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.5.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
            }

            if (overlayConfig.customNote.isNotBlank()) {
                Text(
                    text = overlayConfig.customNote,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = PrecisionAmber,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metaParts.joinToString("  •  "),
                    style = TelemetryTextStyle.copy(
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Captured by Rolet Cam",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFCBD5E1),
                        fontSize = 9.5.sp,
                        letterSpacing = 0.3.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
