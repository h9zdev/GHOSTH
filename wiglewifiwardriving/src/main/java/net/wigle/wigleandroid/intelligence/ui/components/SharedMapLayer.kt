package net.wigle.wigleandroid.intelligence.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import net.wigle.wigleandroid.intelligence.service.GridCell
import net.wigle.wigleandroid.intelligence.ui.*

enum class MapVisualizationMode(val label: String) {
    DENSITY_HEATMAP("Density Heatmap"),
    OBSERVATION_CLUSTERS("Clusters"),
    NETWORK_DISTRIBUTION("Distribution"),
    SIGNAL_STRENGTH_HEATMAP("Signal Heatmap"),
    COVERAGE_LAYER("Coverage Layer")
}

@Composable
fun SharedMapLayer(
    findings: List<Finding> = emptyList(),
    gridCells: List<GridCell> = emptyList(),
    observations: List<Observation> = emptyList(),
    selectedMode: MapVisualizationMode = MapVisualizationMode.DENSITY_HEATMAP,
    onModeChanged: (MapVisualizationMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedCell by remember { mutableStateOf<GridCell?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberSurface, RoundedCornerShape(8.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        // Mode Selector Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapVisualizationMode.values().forEach { mode ->
                val isSelected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) CyberCyan.copy(alpha = 0.2f) else CyberBackground,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) CyberCyan else CyberBorder.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onModeChanged(mode) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isSelected) CyberCyan else CyberTextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // HUD Canvas Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(CyberBackground, RoundedCornerShape(6.dp))
                .border(1.dp, CyberBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gridCells, findings) {
                        detectTapGestures { tapOffset ->
                            // Hit test on nearest cell
                            val w = size.width
                            val h = size.height
                            val allPoints = if (gridCells.isNotEmpty()) {
                                gridCells.map { Pair(it.centerLat, it.centerLon) }
                            } else {
                                findings.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                                    .map { Pair(it.latitude, it.longitude) }
                            }
                            if (allPoints.isNotEmpty()) {
                                val minLat = allPoints.minOf { it.first }
                                val maxLat = allPoints.maxOf { it.first }
                                val minLon = allPoints.minOf { it.second }
                                val maxLon = allPoints.maxOf { it.second }
                                val latSpan = (maxLat - minLat).coerceAtLeast(0.001)
                                val lonSpan = (maxLon - minLon).coerceAtLeast(0.001)
                                val padding = 20.dp.toPx()
                                val drawW = w - (padding * 2)
                                val drawH = h - (padding * 2)

                                val clicked = gridCells.minByOrNull { cell ->
                                    val nx = padding + (((cell.centerLon - minLon) / lonSpan).toFloat() * drawW)
                                    val ny = padding + ((1.0f - ((cell.centerLat - minLat) / latSpan).toFloat()) * drawH)
                                    val dx = nx - tapOffset.x
                                    val dy = ny - tapOffset.y
                                    dx * dx + dy * dy
                                }
                                selectedCell = clicked
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // 1. Cyber Grid lines
                val gridStep = 28.dp.toPx()
                var x = 0f
                while (x < w) {
                    drawLine(
                        color = CyberBorder.copy(alpha = 0.35f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                    x += gridStep
                }
                var y = 0f
                while (y < h) {
                    drawLine(
                        color = CyberBorder.copy(alpha = 0.35f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                    y += gridStep
                }

                // Center Reticle
                drawLine(
                    color = CyberCyan.copy(alpha = 0.2f),
                    start = Offset(w / 2f, 0f),
                    end = Offset(w / 2f, h),
                    strokeWidth = 1f
                )
                drawLine(
                    color = CyberCyan.copy(alpha = 0.2f),
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 1f
                )

                // 2. Determine Map Bounding Box
                val validCoords = mutableListOf<Pair<Double, Double>>()
                for (cell in gridCells) {
                    if (cell.centerLat != 0.0 && cell.centerLon != 0.0) {
                        validCoords.add(Pair(cell.centerLat, cell.centerLon))
                    }
                }
                if (validCoords.isEmpty()) {
                    for (f in findings) {
                        if (f.latitude != 0.0 && f.longitude != 0.0) {
                            validCoords.add(Pair(f.latitude, f.longitude))
                        }
                    }
                }

                if (validCoords.isEmpty()) return@Canvas

                val minLat = validCoords.minOf { it.first }
                val maxLat = validCoords.maxOf { it.first }
                val minLon = validCoords.minOf { it.second }
                val maxLon = validCoords.maxOf { it.second }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.001)
                val lonSpan = (maxLon - minLon).coerceAtLeast(0.001)

                val padding = 22.dp.toPx()
                val drawW = w - (padding * 2)
                val drawH = h - (padding * 2)

                fun project(lat: Double, lon: Double): Offset {
                    val normX = ((lon - minLon) / lonSpan).toFloat().coerceIn(0f, 1f)
                    val normY = (1.0f - ((lat - minLat) / latSpan).toFloat()).coerceIn(0f, 1f)
                    return Offset(padding + (normX * drawW), padding + (normY * drawH))
                }

                // 3. Render according to selected visualization mode
                when (selectedMode) {
                    MapVisualizationMode.DENSITY_HEATMAP -> {
                        for (cell in gridCells) {
                            val pos = project(cell.centerLat, cell.centerLon)
                            val radius = (cell.apCount * 2.5f + 14f).coerceIn(12f, 40f)
                            val color = when {
                                cell.apCount >= 15 -> CyberPink.copy(alpha = 0.45f)
                                cell.apCount >= 6 -> CyberYellow.copy(alpha = 0.40f)
                                else -> CyberCyan.copy(alpha = 0.30f)
                            }
                            drawCircle(color = color, radius = radius, center = pos)
                            drawCircle(color = color.copy(alpha = 0.8f), radius = 3.5f, center = pos)
                        }
                    }

                    MapVisualizationMode.OBSERVATION_CLUSTERS -> {
                        for (cell in gridCells) {
                            val pos = project(cell.centerLat, cell.centerLon)
                            val pinRadius = 9f
                            drawCircle(color = CyberCyan.copy(alpha = 0.25f), radius = pinRadius * 2.2f, center = pos)
                            drawCircle(color = CyberCyan, radius = pinRadius, center = pos, style = Stroke(width = 1.5f))
                            drawCircle(color = if (cell.apCount >= 10) CyberPink else CyberCyan, radius = 4f, center = pos)
                        }
                    }

                    MapVisualizationMode.NETWORK_DISTRIBUTION -> {
                        for (obs in observations.take(120)) {
                            if (obs.latitude == 0.0 && obs.longitude == 0.0) continue
                            val pos = project(obs.latitude, obs.longitude)
                            val dotColor = when {
                                obs.capabilities.contains("WPA3", ignoreCase = true) -> Color(0xFFB388FF)
                                obs.capabilities.contains("WPA2", ignoreCase = true) -> CyberCyan
                                obs.capabilities.contains("WEP", ignoreCase = true) -> CyberYellow
                                obs.capabilities.isEmpty() -> CyberPink // Open
                                else -> CyberGreen
                            }
                            drawCircle(color = dotColor.copy(alpha = 0.85f), radius = 3f, center = pos)
                        }
                    }

                    MapVisualizationMode.SIGNAL_STRENGTH_HEATMAP -> {
                        for (cell in gridCells) {
                            val pos = project(cell.centerLat, cell.centerLon)
                            val sigColor = when {
                                cell.avgRssi >= -60 -> CyberGreen.copy(alpha = 0.5f)
                                cell.avgRssi >= -75 -> CyberCyan.copy(alpha = 0.4f)
                                cell.avgRssi >= -85 -> CyberYellow.copy(alpha = 0.35f)
                                else -> CyberPink.copy(alpha = 0.3f)
                            }
                            val sigRadius = (100 + cell.avgRssi.toFloat()).coerceAtLeast(8f) * 0.9f
                            drawCircle(color = sigColor, radius = sigRadius, center = pos)
                            drawCircle(color = sigColor.copy(alpha = 0.9f), radius = 2f, center = pos)
                        }
                    }

                    MapVisualizationMode.COVERAGE_LAYER -> {
                        // Strictly distinguish confirmed observations from model-estimated coverage!
                        val cellW = (drawW / 12f).coerceAtLeast(16f)
                        val cellH = (drawH / 12f).coerceAtLeast(16f)

                        for (cell in gridCells) {
                            val pos = project(cell.centerLat, cell.centerLon)
                            val rectTopLeft = Offset(pos.x - cellW / 2f, pos.y - cellH / 2f)
                            val rectSize = Size(cellW, cellH)

                            if (cell.isPredicted) {
                                // Model-Estimated Coverage: Magenta with Dashed Stroke
                                drawRect(
                                    color = CyberPink.copy(alpha = 0.12f),
                                    topLeft = rectTopLeft,
                                    size = rectSize
                                )
                                drawRect(
                                    color = CyberPink.copy(alpha = 0.65f),
                                    topLeft = rectTopLeft,
                                    size = rectSize,
                                    style = Stroke(
                                        width = 1.2f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                    )
                                )
                            } else {
                                // Confirmed Observed Coverage: Solid CyberCyan
                                drawRect(
                                    color = CyberCyan.copy(alpha = 0.22f),
                                    topLeft = rectTopLeft,
                                    size = rectSize
                                )
                                drawRect(
                                    color = CyberCyan.copy(alpha = 0.85f),
                                    topLeft = rectTopLeft,
                                    size = rectSize,
                                    style = Stroke(width = 1.2f)
                                )
                            }
                        }
                    }
                }

                // Render selected cell highlight
                selectedCell?.let { cell ->
                    val pos = project(cell.centerLat, cell.centerLon)
                    drawCircle(color = Color.White, radius = 14f, center = pos, style = Stroke(width = 2f))
                }
            }

            // Legend Overlay in Top Right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(CyberSurface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .border(1.dp, CyberBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                if (selectedMode == MapVisualizationMode.COVERAGE_LAYER) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(CyberCyan))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Observed", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberCyan)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).border(1.dp, CyberPink))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Estimated (IDW)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberPink)
                        }
                    }
                } else {
                    Text(
                        text = "${gridCells.size} Sectors // ${observations.size} Obs",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = CyberCyan
                    )
                }
            }
        }

        // Tap Info Banner
        selectedCell?.let { cell ->
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberBackground, RoundedCornerShape(6.dp))
                    .border(1.dp, if (cell.isPredicted) CyberPink else CyberCyan, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (cell.isPredicted) "SECTOR: MODEL-ESTIMATED (IDW)" else "SECTOR: CONFIRMED OBSERVED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (cell.isPredicted) CyberPink else CyberCyan
                    )
                    Text(
                        text = "Coords: ${"%.4f".format(cell.centerLat)}, ${"%.4f".format(cell.centerLon)} | APs: ${cell.apCount} | Avg RSSI: ${"%.1f".format(cell.avgRssi)} dBm",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = CyberTextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (cell.densityLevel == "HIGH") CyberPink.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = cell.densityLevel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                        color = if (cell.densityLevel == "HIGH") CyberPink else CyberCyan
                    )
                }
            }
        }
    }
}
