package net.wigle.wigleandroid.intelligence.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.wigle.wigleandroid.intelligence.model.*
import net.wigle.wigleandroid.intelligence.service.PipelineMetrics
import net.wigle.wigleandroid.intelligence.ui.components.MapVisualizationMode
import net.wigle.wigleandroid.intelligence.ui.components.SharedMapLayer
import net.wigle.wigleandroid.osint.ui.GeospatialOsintActivity
import net.wigle.wigleandroid.osint.ui.WirelessOsintActivity

/**
 * Main Wireless Intelligence Dashboard
 * Fully functional implementation backed by actual application database records.
 *
 * 6 Central Dashboard Sections:
 * 1. Wireless Overview
 * 2. Density Intelligence
 * 3. Anomaly Detection
 * 4. Signal Intelligence
 * 5. Historical Analytics
 * 6. Data Quality
 */
class IntelligenceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                MainIntelligenceDashboard(onFinish = { finish() })
            }
        }
    }
}

val DashboardSections = listOf(
    "Overview",
    "Density",
    "Anomalies",
    "Signal Intel",
    "Historical",
    "Data Quality"
)

@Composable
fun MainIntelligenceDashboard(
    viewModel: IntelligenceViewModel = viewModel(),
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val metrics by viewModel.pipelineMetrics.collectAsState()
    val gridCells by viewModel.gridCells.collectAsState()
    val observations by viewModel.processedObservations.collectAsState()
    val findings by viewModel.filteredFindings.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val mapMode by viewModel.mapMode.collectAsState()
    val selectedSection by viewModel.selectedSectionIndex.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(
                    onClick = onFinish,
                    modifier = Modifier
                        .size(38.dp)
                        .background(CyberSurface, RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyberCyan
                    )
                }
                Column {
                    Text(
                        text = "WIRELESS INTELLIGENCE",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = CyberCyan
                    )
                    Text(
                        text = "PASSIVE ANALYTICS ENGINE // ACTUAL DB",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = CyberTextSecondary
                    )
                }
            }

            // Refresh & Export actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .clickable { viewModel.refreshActualData() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("SYNC", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp), color = CyberCyan)
                }
                Box(
                    modifier = Modifier
                        .background(CyberPink.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .clickable { viewModel.exportReport(context, "pdf") }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("REPORT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp), color = CyberPink)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status Notice / Error Toast
        AnimatedVisibility(visible = statusNotice != null || errorMessage != null) {
            val text = errorMessage ?: statusNotice ?: ""
            val isErr = errorMessage != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isErr) CyberPink.copy(alpha = 0.15f) else CyberCyan.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .border(1.dp, if (isErr) CyberPink else CyberCyan, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isErr) CyberPink else CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "✕",
                        color = CyberTextSecondary,
                        modifier = Modifier.clickable { viewModel.clearNotice() }.padding(horizontal = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 2. Dashboard Sections Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DashboardSections.forEachIndexed { index, sectionName ->
                val isSelected = index == selectedSection
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) CyberCyan.copy(alpha = 0.22f) else CyberSurface,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) CyberCyan else CyberBorder.copy(alpha = 0.6f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.selectSection(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = sectionName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        color = if (isSelected) CyberCyan else CyberTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Section Content Renderer
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Processing wireless observations from database...", style = MaterialTheme.typography.labelSmall, color = CyberTextSecondary)
                }
            }
        } else if (metrics.totalObservations == 0 && observations.isEmpty() && findings.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.SensorsOff, contentDescription = null, tint = CyberBorder, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("NO WIRELESS OBSERVATIONS FOUND", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = CyberTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "The application database currently contains 0 wireless records. Run passive scanning in the main interface to collect RF beacons, or tap Sync below to reload.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberTextSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refreshActualData() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("RETRY DATABASE SYNC", color = CyberCyan, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        } else {
            // Render Selected Section
            when (selectedSection) {
                0 -> OverviewSection(metrics, gridCells, observations, findings, mapMode, onMapModeChanged = { viewModel.setMapMode(it) })
                1 -> DensitySection(metrics, gridCells, observations, findings, filterState, mapMode, onMapModeChanged = { viewModel.setMapMode(it) }, onRadiusChanged = { viewModel.setAnalysisRadius(it) }, onTimeRangeChanged = { viewModel.setTimeRange(it) })
                2 -> AnomalySection(metrics, findings, onFilterChange = { viewModel.setSearchQuery(it) })
                3 -> SignalSection(metrics, observations, findings)
                4 -> HistoricalSection(metrics, findings, filterState, onTimeRangeChanged = { viewModel.setTimeRange(it) })
                5 -> DataQualitySection(metrics, observations)
            }
        }
    }
}

// ==========================================
// SECTION 1: WIRELESS OVERVIEW
// ==========================================
@Composable
fun OverviewSection(
    metrics: PipelineMetrics,
    gridCells: List<net.wigle.wigleandroid.intelligence.service.GridCell>,
    observations: List<Observation>,
    findings: List<Finding>,
    mapMode: MapVisualizationMode,
    onMapModeChanged: (MapVisualizationMode) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // KPI Grid (6 Overview Cards)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard(label = "OBSERVATIONS", value = "${metrics.totalObservations}", color = CyberCyan, modifier = Modifier.weight(1f))
                KpiCard(label = "UNIQUE APS", value = "${metrics.uniqueAccessPoints}", color = CyberGreen, modifier = Modifier.weight(1f))
                KpiCard(label = "ANOMALIES", value = "${metrics.anomaliesCount}", color = if (metrics.anomaliesCount > 0) CyberPink else CyberCyan, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard(label = "REGIONS", value = "${metrics.geographicRegionsCount}", color = CyberYellow, modifier = Modifier.weight(1f))
                KpiCard(label = "OBS COVERAGE", value = "${"%.3f".format(metrics.observedCoverageSqKm)} km²", color = CyberCyan, modifier = Modifier.weight(1f))
                KpiCard(label = "AVG DENSITY", value = "${"%.0f".format(metrics.averageDensityPerSqKm)} /km²", color = Color(0xFFB388FF), modifier = Modifier.weight(1f))
            }
        }

        // Shared Interactive Map
        item {
            SharedMapLayer(
                findings = findings,
                gridCells = gridCells,
                observations = observations,
                selectedMode = mapMode,
                onModeChanged = onMapModeChanged
            )
        }

        // Fast Action Cards to Dedicated Sub-Activities
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("INTELLIGENCE MODULES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                Text("LAUNCH DEDICATED INTENTS", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberTextSecondary)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleTile(title = "Wireless Density", desc = "IDW & TinyML Prediction", icon = Icons.Default.SignalWifi4Bar, color = CyberCyan, modifier = Modifier.weight(1f)) {
                    context.startActivity(Intent(context, DensityPredictionActivity::class.java))
                }
                ModuleTile(title = "Anomaly Detection", desc = "EWMA & Metadata Review", icon = Icons.Default.Security, color = CyberPink, modifier = Modifier.weight(1f)) {
                    context.startActivity(Intent(context, AnomalyDetectionActivity::class.java))
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleTile(title = "Signal Intel", desc = "Tri-Band & Congestion", icon = Icons.Default.Sensors, color = CyberGreen, modifier = Modifier.weight(1f)) {
                    context.startActivity(Intent(context, SignalIntelligenceActivity::class.java))
                }
                ModuleTile(title = "Temporal Intel", desc = "Emergence & Drift", icon = Icons.Default.Schedule, color = CyberYellow, modifier = Modifier.weight(1f)) {
                    context.startActivity(Intent(context, TemporalIntelligenceActivity::class.java))
                }
            }
        }
    }
}

// ==========================================
// SECTION 2: DENSITY INTELLIGENCE
// ==========================================
@Composable
fun DensitySection(
    metrics: PipelineMetrics,
    gridCells: List<net.wigle.wigleandroid.intelligence.service.GridCell>,
    observations: List<Observation>,
    findings: List<Finding>,
    filterState: FilterState,
    mapMode: MapVisualizationMode,
    onMapModeChanged: (MapVisualizationMode) -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onTimeRangeChanged: (TimeRange) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // Interactive Map with 5 Modes
            SharedMapLayer(
                findings = findings,
                gridCells = gridCells,
                observations = observations,
                selectedMode = mapMode,
                onModeChanged = onMapModeChanged
            )
        }

        // Analysis Radius Selector
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GEOGRAPHIC ANALYSIS RADIUS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                        Text("${filterState.analysisRadiusMeters} meters", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberTextPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(50, 100, 250, 500, 1000).forEach { r ->
                            val isSel = filterState.analysisRadiusMeters == r
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSel) CyberCyan.copy(alpha = 0.25f) else CyberBackground, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (isSel) CyberCyan else CyberBorder.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .clickable { onRadiusChanged(r) }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${r}m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (isSel) CyberCyan else CyberTextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Density Statistics & Explicit Coverage Separation
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DENSITY COVERAGE ANALYSIS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    Divider(color = CyberBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

                    DensityMetricRow("Confirmed Observed Coverage", "${"%.3f".format(metrics.observedCoverageSqKm)} km²", CyberCyan)
                    DensityMetricRow("Model-Estimated Coverage (IDW)", "${"%.3f".format(metrics.estimatedCoverageSqKm)} km²", CyberPink)
                    DensityMetricRow("Average AP Concentration", "${"%.0f".format(metrics.averageDensityPerSqKm)} APs / km²", CyberGreen)
                    DensityMetricRow("High Density Sectors (>=15 APs)", "${gridCells.count { it.densityLevel == "HIGH" }}", CyberPink)
                    DensityMetricRow("Medium Density Sectors (5-14 APs)", "${gridCells.count { it.densityLevel == "MEDIUM" }}", CyberYellow)
                    DensityMetricRow("Low Density Sectors (<5 APs)", "${gridCells.count { it.densityLevel == "LOW" }}", CyberTextSecondary)

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "NOTE: Actual observed coverage is distinguished from model-estimated coverage. Never presented as confirmed observations.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = CyberTextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ==========================================
// SECTION 3: ANOMALY DETECTION
// ==========================================
@Composable
fun AnomalySection(
    metrics: PipelineMetrics,
    findings: List<Finding>,
    onFilterChange: (String) -> Unit
) {
    val anomalyFindings = findings.filter { it.analyzerType == AnalyzerType.ANOMALY_DETECTION }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Anomaly Summary Dashboard Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ANOMALY DETECTION ENGINE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberPink)
                        Text("${anomalyFindings.size} Flagged Observations", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberTextPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Analyzes records for metadata inconsistencies, coordinate outliers, duplicate timestamps, and sudden RSSI shifts (>2.5σ). An unusual observation is not proof of an attack.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = CyberTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Category Breakdown Pills
                    val catMap = anomalyFindings.groupingBy { it.category.ifBlank { "Uncategorized" } }.eachCount()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        catMap.forEach { (cat, count) ->
                            Box(
                                modifier = Modifier
                                    .background(CyberBackground, RoundedCornerShape(4.dp))
                                    .border(1.dp, CyberPink.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .clickable { onFilterChange(cat) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("$cat ($count)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberPink)
                            }
                        }
                    }
                }
            }
        }

        // List of Flagged Observations with required metadata
        items(anomalyFindings) { finding ->
            AnomalyFindingCard(finding)
        }
    }
}

@Composable
fun AnomalyFindingCard(finding: Finding) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberSurface, RoundedCornerShape(8.dp))
            .border(1.dp, if (finding.severity == Severity.CRITICAL || finding.severity == Severity.HIGH) CyberPink.copy(alpha = 0.8f) else CyberBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = finding.anomalyId.ifBlank { "ANOM-MET-${finding.id}" },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CyberPink
                )
                Text(
                    text = finding.severity.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (finding.severity == Severity.HIGH || finding.severity == Severity.CRITICAL) CyberPink else CyberCyan
                )
            }

            Text(
                text = finding.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = CyberTextPrimary
            )

            Text(
                text = finding.summary,
                style = MaterialTheme.typography.bodySmall,
                color = CyberTextSecondary
            )

            Divider(color = CyberBorder.copy(alpha = 0.4f), thickness = 0.6.dp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ref: ${finding.bssid ?: "N/A"}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = CyberCyan)
                Text("Confidence: ${"%.0f".format(finding.score * 100)}%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = CyberYellow)
            }

            if (finding.recommendedAction.isNotBlank()) {
                Text(
                    text = "Action: ${finding.recommendedAction}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = CyberGreen
                )
            }
        }
    }
}

// ==========================================
// SECTION 4: SIGNAL INTELLIGENCE
// ==========================================
@Composable
fun SignalSection(
    metrics: PipelineMetrics,
    observations: List<Observation>,
    findings: List<Finding>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. RSSI Distribution Bar Chart
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text("RSSI DISTRIBUTION HISTOGRAM", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Received signal strength distribution across observed networks", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberTextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))

                    RssiHistogramChart(observations)
                }
            }
        }

        // 2. Frequency Band Analysis (2.4 GHz vs 5 GHz vs 6 GHz)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TRI-BAND FREQUENCY DISTRIBUTION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    metrics.bandDistributionCounts.forEach { (band, count) ->
                        val total = metrics.totalObservations.coerceAtLeast(1)
                        val pct = (count * 100) / total
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(band, style = MaterialTheme.typography.labelSmall, color = CyberTextPrimary)
                            Text("$count APs ($pct%)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                        }
                    }
                }
            }
        }

        // 3. Channel Analytics & Congestion Chart
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CHANNEL CONCENTRATION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                        Text("OBSERVED AP COUNTS", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberYellow)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ChannelDistributionChart(metrics.channelDistribution)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "DISCLAIMER: Clearly distinguishes observed channel counts from actual RF spectrum utilization. Passive observations alone cannot establish true channel occupancy.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = CyberTextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ==========================================
// SECTION 5: HISTORICAL ANALYTICS
// ==========================================
@Composable
fun HistoricalSection(
    metrics: PipelineMetrics,
    findings: List<Finding>,
    filterState: FilterState,
    onTimeRangeChanged: (TimeRange) -> Unit
) {
    val temporalFindings = findings.filter { it.analyzerType == AnalyzerType.TEMPORAL_INTELLIGENCE || it.analyzerType == AnalyzerType.PREDICTIVE_ANALYTICS }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Time Range Filter Pills
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimeRange.values().forEach { tr ->
                    val isSel = filterState.timeRange == tr
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSel) CyberCyan.copy(alpha = 0.25f) else CyberSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, if (isSel) CyberCyan else CyberBorder, RoundedCornerShape(6.dp))
                            .clickable { onTimeRangeChanged(tr) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (isSel) CyberCyan else CyberTextSecondary)
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TEMPORAL NETWORK VELOCITY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    Divider(color = CyberBorder.copy(alpha = 0.4f), thickness = 0.6.dp)
                    DensityMetricRow("Analysis Timeframe", filterState.timeRange.label, CyberCyan)
                    DensityMetricRow("Active Network Baseline", "${metrics.uniqueAccessPoints} APs", CyberGreen)
                    DensityMetricRow("Temporal Findings Logged", "${temporalFindings.size}", CyberYellow)
                }
            }
        }

        items(temporalFindings) { finding ->
            AnomalyFindingCard(finding)
        }
    }
}

// ==========================================
// SECTION 6: DATA QUALITY
// ==========================================
@Composable
fun DataQualitySection(
    metrics: PipelineMetrics,
    observations: List<Observation>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary Quality Status Cards
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("OBSERVATION DATA QUALITY AUDIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    Divider(color = CyberBorder.copy(alpha = 0.4f), thickness = 0.6.dp)

                    DataQualityStatus.values().forEach { status ->
                        val count = metrics.dataQualityCounts[status] ?: 0
                        val color = when (status) {
                            DataQualityStatus.NORMAL -> CyberGreen
                            DataQualityStatus.INCONSISTENT -> CyberPink
                            DataQualityStatus.DUPLICATE -> CyberYellow
                            DataQualityStatus.INCOMPLETE -> CyberCyan
                            DataQualityStatus.REQUIRES_REVIEW -> Color(0xFFB388FF)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(status.label, style = MaterialTheme.typography.labelSmall, color = CyberTextPrimary)
                            Text("$count Records", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
                        }
                    }
                }
            }
        }

        // List of Observations Requiring Review or Flagged
        val nonNormalObs = observations.filter { it.qualityStatus != DataQualityStatus.NORMAL }.take(40)
        items(nonNormalObs) { obs ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(obs.bssid, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                        Text(obs.qualityStatus.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = CyberPink)
                    }
                    Text("SSID: ${obs.ssid.ifBlank { "<Hidden>" }} | RSSI: ${obs.rssi} dBm | Ch: ${obs.channel}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = CyberTextSecondary)
                    if (obs.qualityIssues.isNotEmpty()) {
                        Text("Issues: ${obs.qualityIssues.joinToString("; ")}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberYellow)
                    }
                }
            }
        }
    }
}

// ==========================================
// REUSABLE HUD WIDGETS
// ==========================================
@Composable
fun KpiCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CyberSurface, RoundedCornerShape(6.dp))
            .border(1.dp, CyberBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = CyberTextSecondary)
        }
    }
}

@Composable
fun DensityMetricRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CyberTextSecondary)
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
fun ModuleTile(title: String, desc: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(CyberSurface, RoundedCornerShape(6.dp))
            .border(1.dp, CyberBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberTextPrimary)
                Text(desc, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberTextSecondary)
            }
        }
    }
}

@Composable
fun RssiHistogramChart(observations: List<Observation>) {
    val bins = listOf(-95, -85, -75, -65, -55, -45)
    val binLabels = listOf("<-90", "-85", "-75", "-65", "-55", ">-50")
    val counts = IntArray(bins.size)

    for (obs in observations) {
        when {
            obs.rssi <= -90 -> counts[0]++
            obs.rssi <= -80 -> counts[1]++
            obs.rssi <= -70 -> counts[2]++
            obs.rssi <= -60 -> counts[3]++
            obs.rssi <= -50 -> counts[4]++
            else -> counts[5]++
        }
    }
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width
        val h = size.height
        val barW = (w / counts.size) * 0.7f
        val gap = (w / counts.size) * 0.3f

        for (i in counts.indices) {
            val barH = (counts[i].toFloat() / maxCount) * (h - 16f)
            val x = i * (barW + gap) + gap / 2f
            val y = h - barH - 12f
            val col = when (i) {
                0, 1 -> CyberPink
                2 -> CyberYellow
                3 -> CyberCyan
                else -> CyberGreen
            }
            drawRect(color = col.copy(alpha = 0.85f), topLeft = Offset(x, y), size = Size(barW, barH))
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        binLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = CyberTextSecondary) }
    }
}

@Composable
fun ChannelDistributionChart(channelMap: Map<Int, Int>) {
    val topChannels = channelMap.entries.sortedByDescending { it.value }.take(8)
    val maxCount = topChannels.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        topChannels.forEach { (channel, count) ->
            val pct = count.toFloat() / maxCount
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ch $channel", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = CyberCyan)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(CyberBackground, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(pct)
                            .background(if (channel in listOf(1, 6, 11)) CyberPink else CyberCyan, RoundedCornerShape(2.dp))
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("$count", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CyberTextSecondary)
            }
        }
    }
}
