package net.wigle.wigleandroid.intelligence.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Severity
import net.wigle.wigleandroid.intelligence.ui.*

@Composable
fun SharedMapLayer(
    findings: List<Finding>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(CyberSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Cyber Grid lines
            val gridStep = 30.dp.toPx()
            var x = 0f
            while (x < w) {
                drawLine(
                    color = CyberBorder.copy(alpha = 0.4f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                x += gridStep
            }
            var y = 0f
            while (y < h) {
                drawLine(
                    color = CyberBorder.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }

            // 2. Reticle / Crosshair
            drawLine(
                color = CyberCyan.copy(alpha = 0.25f),
                start = Offset(w / 2f, 0f),
                end = Offset(w / 2f, h),
                strokeWidth = 1.5f
            )
            drawLine(
                color = CyberCyan.copy(alpha = 0.25f),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1.5f
            )

            if (findings.isEmpty()) return@Canvas

            // 3. Normalize findings onto HUD canvas
            val validFindings = findings.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            if (validFindings.isEmpty()) return@Canvas

            val minLat = validFindings.minOf { it.latitude }
            val maxLat = validFindings.maxOf { it.latitude }
            val minLon = validFindings.minOf { it.longitude }
            val maxLon = validFindings.maxOf { it.longitude }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.001)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.001)

            val padding = 20.dp.toPx()
            val drawW = w - (padding * 2)
            val drawH = h - (padding * 2)

            for (f in validFindings) {
                val normX = ((f.longitude - minLon) / lonSpan).toFloat()
                val normY = 1.0f - ((f.latitude - minLat) / latSpan).toFloat()

                val px = padding + (normX * drawW)
                val py = padding + (normY * drawH)

                val markerColor = when (f.severity) {
                    Severity.CRITICAL -> CyberPink
                    Severity.HIGH -> CyberOrange
                    Severity.MEDIUM -> CyberYellow
                    else -> CyberCyan
                }

                if (f.analyzerType == AnalyzerType.DENSITY_PREDICTION) {
                    // Density radar halo
                    val radius = (12f * f.score.toFloat()).coerceIn(6f, 22f)
                    drawCircle(
                        color = CyberCyan.copy(alpha = 0.2f),
                        radius = radius,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = CyberCyan,
                        radius = 4f,
                        center = Offset(px, py)
                    )
                } else {
                    // Threat / Anomaly marker
                    drawCircle(
                        color = markerColor.copy(alpha = 0.3f),
                        radius = 14f,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = markerColor,
                        radius = 5f,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = markerColor,
                        radius = 10f,
                        center = Offset(px, py),
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }

        // HUD Overlay text tags
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SHARED HUD MAP // LIVE FEED",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan
            )
            Text(
                text = "POINTS: ${findings.size}",
                style = MaterialTheme.typography.labelSmall,
                color = CyberTextSecondary
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "GRID: 50m | PASSIVE ONLY",
                style = MaterialTheme.typography.labelSmall,
                color = CyberBorder
            )
        }
    }
}

