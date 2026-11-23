package net.wigle.wigleandroid.intelligence.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.ui.components.ModuleDetailScreen

/**
 * Base helper composable for dedicated module activities.
 */
@Composable
fun ModuleActivityContent(
    moduleType: AnalyzerType,
    viewModel: IntelligenceViewModel,
    onFinish: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(moduleType) {
        viewModel.selectModule(moduleType)
    }

    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val findings by viewModel.filteredFindings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val statusNotice by viewModel.statusNotice.collectAsStateWithLifecycle()

    LaunchedEffect(statusNotice) {
        statusNotice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }
    }

    ModuleDetailScreen(
        module = moduleType,
        findings = findings,
        filterState = filterState,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onBackClick = onFinish,
        onTimeRangeSelected = { viewModel.setTimeRange(it) },
        onExportPdf = { viewModel.exportReport(context, "pdf") },
        onExportCsv = { viewModel.exportReport(context, "csv") },
        onRetryClick = { viewModel.runReplay() }
    )
}

/**
 * 1. Wireless Density Prediction Activity (HIGH PRIORITY)
 */
class DensityPredictionActivity : ComponentActivity() {
    private val viewModel: IntelligenceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                ModuleActivityContent(
                    moduleType = AnalyzerType.DENSITY_PREDICTION,
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

/**
 * 2. Anomaly Detection Activity (HIGH PRIORITY)
 */
class AnomalyDetectionActivity : ComponentActivity() {
    private val viewModel: IntelligenceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                ModuleActivityContent(
                    moduleType = AnalyzerType.ANOMALY_DETECTION,
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

/**
 * 3. Signal Intelligence Activity
 */
class SignalIntelligenceActivity : ComponentActivity() {
    private val viewModel: IntelligenceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                ModuleActivityContent(
                    moduleType = AnalyzerType.SIGNAL_INTELLIGENCE,
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

/**
 * 4. Temporal Intelligence Activity
 */
class TemporalIntelligenceActivity : ComponentActivity() {
    private val viewModel: IntelligenceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                ModuleActivityContent(
                    moduleType = AnalyzerType.TEMPORAL_INTELLIGENCE,
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

/**
 * 5. ML Clustering Activity
 */
class MlClusteringActivity : ComponentActivity() {
    private val viewModel: IntelligenceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                ModuleActivityContent(
                    moduleType = AnalyzerType.ML_CLUSTERING,
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

/**
 * 6. Geospatial ML Activity
 */
class GeospatialMlActivity : ComponentActivity() {
    private val viewModel: IntelligenceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                ModuleActivityContent(
                    moduleType = AnalyzerType.GEOSPATIAL_ML,
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

/**
 * 7. Predictive Analytics Activity (TINY ML MODEL)
 */
class PredictiveAnalyticsActivity : ComponentActivity() {
    private val viewModel: IntelligenceViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                ModuleActivityContent(
                    moduleType = AnalyzerType.PREDICTIVE_ANALYTICS,
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}
