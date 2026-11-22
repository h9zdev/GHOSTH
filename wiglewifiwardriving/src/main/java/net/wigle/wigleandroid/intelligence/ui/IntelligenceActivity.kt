


package net.wigle.wigleandroid.intelligence.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.ui.components.*

class IntelligenceActivity : ComponentActivity() {

    private val viewModel: IntelligenceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IntelligenceTheme {
                IntelligenceScreen(viewModel = viewModel, onFinish = { finish() })
            }
        }
    }
}

@Composable
fun IntelligenceScreen(
    viewModel: IntelligenceViewModel,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val selectedModule by viewModel.selectedModule.collectAsStateWithLifecycle()
    val findings by viewModel.filteredFindings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val statusNotice by viewModel.statusNotice.collectAsStateWithLifecycle()

    // Show status toasts
    LaunchedEffect(statusNotice) {
        statusNotice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }
    }

    if (selectedModule != null) {
        ModuleDetailScreen(
            module = selectedModule!!,
            findings = findings,
            filterState = filterState,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onBackClick = { viewModel.selectModule(null) },
            onTimeRangeSelected = { viewModel.setTimeRange(it) },
            onExportPdf = { viewModel.exportReport(context, "pdf") },
            onExportCsv = { viewModel.exportReport(context, "csv") },
            onRetryClick = { viewModel.runReplay() }
        )
    } else {
        // Main Overview Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberBackground)
                .padding(12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "INTELLIGENCE // HUB",
                        style = MaterialTheme.typography.headlineLarge,
                        color = CyberCyan
                    )
                    Text(
                        text = "PASSIVE WIRELESS METADATA ANALYTICS",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberTextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.runReplay() },
                        shape = RoundedCornerShape(3.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(CyberCyan)
                        )
                    ) {
                        Text("REPLAY", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearFindings() },
                        shape = RoundedCornerShape(3.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(CyberPink)
                        )
                    ) {
                        Text("CLEAR", style = MaterialTheme.typography.labelSmall, color = CyberPink)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shared Map Layer
            SharedMapLayer(findings = findings)

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Bar
            FilterBar(
                filterState = filterState,
                onTimeRangeSelected = { viewModel.setTimeRange(it) },
                onBandSelected = { viewModel.setBand(it) },
                onSecuritySelected = { viewModel.setSecurity(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Module Cards & Summary
            val densityFindings = findings.filter { it.analyzerType == AnalyzerType.DENSITY_PREDICTION }
            val anomalyFindings = findings.filter { it.analyzerType == AnalyzerType.ANOMALY_DETECTION }
            val signalFindings = findings.filter { it.analyzerType == AnalyzerType.SIGNAL_INTELLIGENCE }
            val temporalFindings = findings.filter { it.analyzerType == AnalyzerType.TEMPORAL_INTELLIGENCE }
            val clusterFindings = findings.filter { it.analyzerType == AnalyzerType.ML_CLUSTERING }
            val geoFindings = findings.filter { it.analyzerType == AnalyzerType.GEOSPATIAL_ML }
            val predFindings = findings.filter { it.analyzerType == AnalyzerType.PREDICTIVE_ANALYTICS }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "MODULE OVERVIEW",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyberCyan
                    )
                }

                // Module 1: Wireless Density Prediction (HIGH PRIORITY)
                item {
                    val denseSectors = densityFindings.count { it.title.contains("High Network Concentration") }
                    val avgScore = if (densityFindings.isNotEmpty()) densityFindings.map { it.score }.average().toFloat() else 0.4f
                    val sparkline = listOf(0.2f, 0.35f, 0.45f, avgScore, (avgScore * 1.2f).coerceAtMost(1f))

                    ModuleCard(
                        type = AnalyzerType.DENSITY_PREDICTION,
                        headlineMetric = "$denseSectors Concentration Zones",
                        metricSubtitle = "${densityFindings.size} spatial binning & IDW predictions",
                        statusText = if (denseSectors > 0) "ELEVATED DENSITY" else "NORMAL DENSITY",
                        sparklineData = sparkline,
                        accentColor = CyberCyan,
                        onViewDetailsClick = { viewModel.selectModule(AnalyzerType.DENSITY_PREDICTION) }
                    )
                }

                // Module 2: Anomaly Detection (HIGH PRIORITY)
                item {
                    val criticalAnomalies = anomalyFindings.count { it.severity.name == "CRITICAL" }
                    val sparkline = if (anomalyFindings.isEmpty()) listOf(0f, 0f, 0f, 0f) else listOf(0f, 1f, 0.5f, anomalyFindings.size.toFloat())
                    val accent = if (criticalAnomalies > 0) CyberPink else CyberOrange

                    ModuleCard(
                        type = AnalyzerType.ANOMALY_DETECTION,
                        headlineMetric = "${anomalyFindings.size} Anomalies Detected",
                        metricSubtitle = "$criticalAnomalies critical rogue/signal shifts",
                        statusText = if (criticalAnomalies > 0) "THREAT DETECTED" else if (anomalyFindings.isNotEmpty()) "ANOMALOUS" else "ALL CLEAR",
                        sparklineData = sparkline,
                        accentColor = accent,
                        onViewDetailsClick = { viewModel.selectModule(AnalyzerType.ANOMALY_DETECTION) }
                    )
                }

                // Module 3: Signal Intelligence
                item {
                    val sparkline = listOf(0.3f, 0.6f, 0.4f, 0.8f, 0.7f)
                    ModuleCard(
                        type = AnalyzerType.SIGNAL_INTELLIGENCE,
                        headlineMetric = "${signalFindings.size} Spectrum Metrics",
                        metricSubtitle = "Congestion & Tri-band 2.4/5/6 GHz balance",
                        statusText = if (signalFindings.any { it.title.contains("Congestion") }) "CONGESTED" else "OPTIMAL",
                        sparklineData = sparkline,
                        accentColor = CyberGreen,
                        onViewDetailsClick = { viewModel.selectModule(AnalyzerType.SIGNAL_INTELLIGENCE) }
                    )
                }

                // Module 4: Temporal Intelligence
                item {
                    val sparkline = listOf(0.1f, 0.2f, 0.5f, 0.7f, 0.9f)
                    ModuleCard(
                        type = AnalyzerType.TEMPORAL_INTELLIGENCE,
                        headlineMetric = "${temporalFindings.size} Temporal Events",
                        metricSubtitle = "Network emergence & persistent node churn",
                        statusText = "HISTORICAL ACTIVE",
                        sparklineData = sparkline,
                        accentColor = CyberYellow,
                        onViewDetailsClick = { viewModel.selectModule(AnalyzerType.TEMPORAL_INTELLIGENCE) }
                    )
                }

                // Module 5: ML Clustering
                item {
                    val sparkline = listOf(0.5f, 0.7f, 0.6f, 0.8f)
                    ModuleCard(
                        type = AnalyzerType.ML_CLUSTERING,
                        headlineMetric = "${clusterFindings.size} Environment Clusters",
                        metricSubtitle = "Proximity centroid & venue classification",
                        statusText = "CLUSTERED",
                        sparklineData = sparkline,
                        accentColor = CyberCyan,
                        onViewDetailsClick = { viewModel.selectModule(AnalyzerType.ML_CLUSTERING) }
                    )
                }

                // Module 6: Geospatial ML
                item {
                    val sparkline = listOf(0.4f, 0.3f, 0.7f, 0.9f)
                    ModuleCard(
                        type = AnalyzerType.GEOSPATIAL_ML,
                        headlineMetric = "${geoFindings.size} DBSCAN Density Clusters",
                        metricSubtitle = "Spatial core density & mobile AP tracking",
                        statusText = if (geoFindings.any { it.title.contains("Mobile") }) "MOBILE AP DETECTED" else "STATIONARY",
                        sparklineData = sparkline,
                        accentColor = CyberOrange,
                        onViewDetailsClick = { viewModel.selectModule(AnalyzerType.GEOSPATIAL_ML) }
                    )
                }

                // Module 7: Predictive Analytics
                item {
                    val sparkline = listOf(0.2f, 0.4f, 0.6f, 0.75f, 0.95f)
                    ModuleCard(
                        type = AnalyzerType.PREDICTIVE_ANALYTICS,
                        headlineMetric = "${predFindings.size} Trend Forecasts",
                        metricSubtitle = "Velocity projection & trajectory derivative",
                        statusText = "FORECASTING ACTIVE",
                        sparklineData = sparkline,
                        accentColor = CyberCyan,
                        onViewDetailsClick = { viewModel.selectModule(AnalyzerType.PREDICTIVE_ANALYTICS) }
                    )
                }

                // Recent Findings Feed Preview
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "RECENT INTELLIGENCE FEED",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyberCyan
                    )
                }

                if (findings.isEmpty()) {
                    item {
                        FindingsList(
                            findings = emptyList(),
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onRetryClick = { viewModel.runReplay() }
                        )
                    }
                } else {
                    items(findings.take(5), key = { it.id }) { finding ->
                        FindingCard(finding = finding, timeStr = "RECENT")
                    }
                }
            }
        }
    }
}
