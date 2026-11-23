package net.wigle.wigleandroid.osint.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wigle.wigleandroid.intelligence.ui.*

class GeospatialOsintActivity : ComponentActivity() {

    private val viewModel: OsintViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                GeospatialOsintScreen(
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun GeospatialOsintScreen(
    viewModel: OsintViewModel,
    onFinish: () -> Unit
) {
    val geoLayer by viewModel.geospatialLayer.collectAsStateWithLifecycle()
    val findings by viewModel.wirelessFindings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(16.dp)
    ) {
        // Minimalist Navigation Bar - No Border
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onFinish,
                modifier = Modifier
                    .size(40.dp)
                    .background(CyberSurface, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CyberCyan
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "GEOSPATIAL OSINT",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )
                Text(
                    text = "OPENSTREETMAP & ADMINISTRATIVE BOUNDARIES",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSecondary
                )
            }

            IconButton(
                onClick = { viewModel.loadData() },
                modifier = Modifier
                    .size(40.dp)
                    .background(CyberSurface, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = CyberCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // OSM Sector Header Card - No Border
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = geoLayer?.boundaryName ?: "OSM TILE SECTOR",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CyberCyan,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = geoLayer?.administrativeWard ?: "Municipal Ward Administrative Boundary",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OsintMetricItem(
                        label = "EST. DENSITY",
                        value = "${"%.1f".format(geoLayer?.apDensityPerSqKm ?: 0.0)}/km²",
                        color = CyberCyan
                    )
                    OsintMetricItem(
                        label = "INFRASTRUCTURE",
                        value = "${geoLayer?.infrastructureCount ?: 0}",
                        color = CyberYellow
                    )
                    OsintMetricItem(
                        label = "HOTSPOT RATIO",
                        value = "${"%.1f".format((geoLayer?.publicHotspotRatio ?: 0f) * 100)}%",
                        color = CyberGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // OpenStreetMap Vector / Grid Observation Layer Canvas - No Border
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Draw minimalist OpenStreetMap grid overlay
                    val gridCols = 8
                    val gridRows = 6
                    for (i in 1 until gridCols) {
                        drawLine(
                            color = Color(0xFF1A1D2E),
                            start = Offset(i * w / gridCols, 0f),
                            end = Offset(i * w / gridCols, h),
                            strokeWidth = 1f
                        )
                    }
                    for (j in 1 until gridRows) {
                        drawLine(
                            color = Color(0xFF1A1D2E),
                            start = Offset(0f, j * h / gridRows),
                            end = Offset(w, j * h / gridRows),
                            strokeWidth = 1f
                        )
                    }

                    // Plot passive observations on the OSM projection
                    findings.take(30).forEachIndexed { idx, obs ->
                        val px = (w * 0.15f + ((idx * 37) % (w * 0.7f).toInt())).toFloat()
                        val py = (h * 0.15f + ((idx * 43) % (h * 0.7f).toInt())).toFloat()

                        val pointColor = when {
                            obs.isPublicHotspot -> CyberYellow
                            obs.securityType.contains("WPA3") -> CyberGreen
                            obs.securityType.contains("OPEN") -> CyberPink
                            else -> CyberCyan
                        }

                        drawCircle(
                            color = pointColor.copy(alpha = 0.8f),
                            radius = 5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                Text(
                    text = "OSM TILE: ${geoLayer?.osmTileCoord ?: "15/X/Y"} // PASSIVE OVERLAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSecondary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Infrastructure breakdown list
        Text(
            text = "PUBLIC INFRASTRUCTURE & BOUNDARY DATASETS",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = CyberCyan
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                GeoOsintDetailRow(
                    title = "Administrative Precinct Boundary",
                    subtitle = geoLayer?.administrativeWard ?: "District Overlay",
                    status = "GEO-FENCE ACTIVE",
                    color = CyberCyan
                )
            }
            item {
                GeoOsintDetailRow(
                    title = "Public Hotspots & Transit Beacons",
                    subtitle = "${geoLayer?.infrastructureCount ?: 0} public utility APs mapped to OSM sectors",
                    status = "MAPPED",
                    color = CyberYellow
                )
            }
            item {
                GeoOsintDetailRow(
                    title = "Spatial AP Density Model",
                    subtitle = "Area density ${"%.1f".format(geoLayer?.apDensityPerSqKm ?: 0.0)} nodes/km² based on bounding box",
                    status = "COMPUTED",
                    color = CyberGreen
                )
            }
            item {
                val box = geoLayer?.boundingBox
                GeoOsintDetailRow(
                    title = "Bounding Box Envelope",
                    subtitle = "Lat: [${"%.4f".format(box?.minLat ?: 0.0)}, ${"%.4f".format(box?.maxLat ?: 0.0)}] • Lon: [${"%.4f".format(box?.minLon ?: 0.0)}, ${"%.4f".format(box?.maxLon ?: 0.0)}]",
                    status = "BOUNDS LOCKED",
                    color = CyberCyan
                )
            }
        }
    }
}

@Composable
fun GeoOsintDetailRow(title: String, subtitle: String, status: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberTextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
        }
    }
}

