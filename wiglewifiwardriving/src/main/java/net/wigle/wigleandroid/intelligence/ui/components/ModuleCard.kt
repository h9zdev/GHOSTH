package net.wigle.wigleandroid.intelligence.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.ui.*

@Composable
fun ModuleCard(
    type: AnalyzerType,
    headlineMetric: String,
    metricSubtitle: String,
    statusText: String,
    sparklineData: List<Float>,
    accentColor: Color = CyberCyan,
    onViewDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
            .clickable { onViewDetailsClick() },
        colors = CardDefaults.cardColors(
            containerColor = CyberSurface
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header: Title and Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = type.displayName.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = accentColor
                )
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .border(1.dp, accentColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: Metric + Sparkline Chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = headlineMetric,
                        style = MaterialTheme.typography.headlineLarge,
                        color = CyberTextPrimary
                    )
                    Text(
                        text = metricSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberTextSecondary
                    )
                }

                // Mini Canvas Sparkline
                MiniSparkline(
                    data = sparklineData,
                    lineColor = accentColor,
                    modifier = Modifier
                        .width(110.dp)
                        .height(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onViewDetailsClick,
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = accentColor
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(accentColor)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VIEW DETAILS // >",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun MiniSparkline(
    data: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxVal = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val minVal = data.minOrNull() ?: 0f
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val path = Path()
        val stepX = size.width / (data.size - 1)

        data.forEachIndexed { i, value ->
            val x = i * stepX
            val normalizedY = (value - minVal) / range
            val y = size.height - (normalizedY * size.height)
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw last point dot
        val lastX = size.width
        val lastY = size.height - (((data.last() - minVal) / range) * size.height)
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}

