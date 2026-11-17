package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PrecisionAmber
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.TelemetryTextStyle
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CompassRose(
    azimuthDegrees: Float,
    cardinalDirection: String,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.cd_compass_rose)
    val roundedDegrees = (((azimuthDegrees % 360f) + 360f) % 360f).roundToInt() % 360

    Box(
        modifier = modifier
            .size(104.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 4f

            drawCircle(
                color = SlateContainerHigh.copy(alpha = 0.45f),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = SlateOutline,
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f),
            )

            rotate(degrees = -azimuthDegrees, pivot = center) {
                for (deg in 0 until 360 step 15) {
                    val isCardinal = deg % 90 == 0
                    val tickLength = if (isCardinal) 10f else 5f
                    val rad = Math.toRadians((deg - 90).toDouble())
                    val outer = Offset(
                        x = center.x + (radius - 3f) * cos(rad).toFloat(),
                        y = center.y + (radius - 3f) * sin(rad).toFloat(),
                    )
                    val inner = Offset(
                        x = center.x + (radius - 3f - tickLength) * cos(rad).toFloat(),
                        y = center.y + (radius - 3f - tickLength) * sin(rad).toFloat(),
                    )
                    drawLine(
                        color = if (deg == 0) PrecisionAmber else Color.White.copy(alpha = if (isCardinal) 0.85f else 0.35f),
                        start = inner,
                        end = outer,
                        strokeWidth = if (isCardinal) 2.2f else 1.2f,
                    )
                }
            }

            val pointerPath = Path().apply {
                moveTo(center.x, 3f)
                lineTo(center.x - 5f, 12f)
                lineTo(center.x + 5f, 12f)
                close()
            }
            drawPath(pointerPath, color = PrecisionAmber)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${roundedDegrees}°",
                style = TelemetryTextStyle.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = cardinalDirection,
                style = MaterialTheme.typography.labelLarge.copy(color = PrecisionAmber),
            )
        }
    }
}
