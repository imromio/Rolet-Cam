package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PrecisionAmber

@Composable
fun RoletCamSplashOverlay(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "roletSplash")

    val logoScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoScale",
    )

    val sweepRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepRotation",
    )

    val haloAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF081224),
                        Color(0xFF10274C),
                        Color(0xFF0A162B),
                    ),
                ),
            )
            .testTag("startup_splash_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(164.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 4.dp.toPx()
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = haloAlpha * 0.45f),
                        style = Stroke(width = strokeWidth * 0.6f),
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF38BDF8),
                                PrecisionAmber,
                                Color.Transparent,
                            ),
                        ),
                        startAngle = sweepRotation,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.ic_rolet_cam_foreground),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .size(116.dp)
                        .scale(logoScale)
                        .clip(RoundedCornerShape(26.dp)),
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "GPS Geotag Camera",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color(0xFF7DD3FC),
                    letterSpacing = 1.2.sp,
                ),
            )

            Spacer(modifier = Modifier.height(28.dp))

            LinearProgressIndicator(
                color = Color(0xFF38BDF8),
                trackColor = Color.White.copy(alpha = 0.14f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .size(width = 160.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Loading camera & GPS…",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
            )
        }
    }
}
