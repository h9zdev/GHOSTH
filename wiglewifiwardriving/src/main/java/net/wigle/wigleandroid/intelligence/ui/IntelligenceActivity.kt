package net.wigle.wigleandroid.intelligence.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.wigle.wigleandroid.osint.ui.GeospatialOsintActivity
import net.wigle.wigleandroid.osint.ui.WirelessOsintActivity

/**
 * Redesigned Minimalist Intelligence Hub.
 *
 * Dark cyber background, NO borders, centered minimalist icons and text.
 * Each module card launches a dedicated distinct Intent upon click.
 *
 * Sections:
 * 1. Wireless Intelligence (7 Modules, including High Priority & TinyML)
 * 2. OSINT Analysis (Wireless OSINT & Geospatial OSINT)
 */
class IntelligenceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                IntelligenceHubScreen(onFinish = { finish() })
            }
        }
    }
}

@Composable
fun IntelligenceHubScreen(onFinish: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(16.dp)
    ) {
        // Top Header - Minimalist, No Border
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
                    contentDescription = "Back to Home",
                    tint = CyberCyan
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "INTELLIGENCE // HUB",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = CyberCyan
                )
                Text(
                    text = "PASSIVE WIRELESS METADATA ANALYTICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSecondary
                )
            }

            // Balanced spacing
            Spacer(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // SECTION 1: WIRELESS INTELLIGENCE
            // ==========================================
            item {
                SectionHeader(
                    title = "WIRELESS INTELLIGENCE",
                    subtitle = "PASSIVE ML & ANOMALY DETECTION ENGINE"
                )
            }

            // 1. Wireless Density Prediction (HIGH PRIORITY)
            item {
                MinimalModuleCard(
                    icon = Icons.Default.SignalWifi4Bar,
                    iconTint = CyberCyan,
                    title = "Wireless Density Prediction",
                    priorityBadge = "HIGH PRIORITY",
                    badgeColor = CyberCyan,
                    bullets = listOf(
                        "Predict Wi-Fi access point density",
                        "Identify areas with high network concentration",
                        "Generate geographic coverage heatmaps",
                        "Estimate signal distribution in poorly sampled areas"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, DensityPredictionActivity::class.java))
                    }
                )
            }

            // 2. Anomaly Detection (HIGH PRIORITY)
            item {
                MinimalModuleCard(
                    icon = Icons.Default.Security,
                    iconTint = CyberPink,
                    title = "Anomaly Detection",
                    priorityBadge = "HIGH PRIORITY",
                    badgeColor = CyberPink,
                    bullets = listOf(
                        "Identify unusual changes in network observations",
                        "Detect duplicate or inconsistent access point records",
                        "Flag unexpected signal-strength changes (>2.5σ)",
                        "Identify unusual activity & sudden signal shifts"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, AnomalyDetectionActivity::class.java))
                    }
                )
            }

            // 3. Signal Intelligence
            item {
                MinimalModuleCard(
                    icon = Icons.Default.Sensors,
                    iconTint = CyberGreen,
                    title = "Signal Intelligence",
                    priorityBadge = null,
                    badgeColor = CyberGreen,
                    bullets = listOf(
                        "Analyze RSSI distribution & quality classification",
                        "Compare 2.4 GHz, 5 GHz and 6 GHz tri-band observations",
                        "Identify channel congestion & optimal spectrum recommendations",
                        "Analyze signal-strength patterns over time"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, SignalIntelligenceActivity::class.java))
                    }
                )
            }

            // 4. Temporal Intelligence
            item {
                MinimalModuleCard(
                    icon = Icons.Default.Schedule,
                    iconTint = CyberYellow,
                    title = "Temporal Intelligence",
                    priorityBadge = null,
                    badgeColor = CyberYellow,
                    bullets = listOf(
                        "Track changes in observed networks over time",
                        "Compare historical and recent observations",
                        "Identify newly observed or no-longer-observed networks",
                        "Generate daily, weekly and monthly persistence reports"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, TemporalIntelligenceActivity::class.java))
                    }
                )
            }

            // 5. ML Clustering
            item {
                MinimalModuleCard(
                    icon = Icons.Default.Hub,
                    iconTint = CyberCyan,
                    title = "ML Clustering",
                    priorityBadge = null,
                    badgeColor = CyberCyan,
                    bullets = listOf(
                        "Group observations by geographic proximity",
                        "Cluster similar wireless environments (Enterprise/Hospitality/Urban)",
                        "Identify dense observation regions",
                        "Discover spatial and taxonomic wireless patterns"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, MlClusteringActivity::class.java))
                    }
                )
            }

            // 6. Geospatial ML
            item {
                MinimalModuleCard(
                    icon = Icons.Default.LocationOn,
                    iconTint = CyberOrange,
                    title = "Geospatial ML",
                    priorityBadge = null,
                    badgeColor = CyberOrange,
                    bullets = listOf(
                        "DBSCAN spatial clustering (Core vs Noise identification)",
                        "Network density maps & spatial dispersion models",
                        "Geographic observation patterns",
                        "Mobile transmitter detection (>300m dispersion variance)"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, GeospatialMlActivity::class.java))
                    }
                )
            }

            // 7. Predictive Analytics (USE TINY ML MODEL)
            item {
                MinimalModuleCard(
                    icon = Icons.Default.Memory,
                    iconTint = CyberCyan,
                    title = "Predictive Analytics",
                    priorityBadge = "TINY ML MODEL",
                    badgeColor = CyberCyan,
                    bullets = listOf(
                        "On-device TinyML Multi-Layer Perceptron neural inference",
                        "Observation trends & scanning velocity derivatives",
                        "Historical density comparisons",
                        "Network density forecasting for upcoming 5m/15m intervals"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, PredictiveAnalyticsActivity::class.java))
                    }
                )
            }

            // ==========================================
            // SECTION 2: OSINT ANALYSIS
            // ==========================================
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(
                    title = "OSINT ANALYSIS",
                    subtitle = "OPEN SOURCE WIRELESS & GEOSPATIAL INTELLIGENCE"
                )
            }

            // 1. Wireless OSINT
            item {
                MinimalModuleCard(
                    icon = Icons.Default.Public,
                    iconTint = CyberCyan,
                    title = "Wireless OSINT",
                    priorityBadge = "OSINT ENGINE",
                    badgeColor = CyberCyan,
                    bullets = listOf(
                        "WiGLE API integration & query endpoints",
                        "Public wireless network observations & taxonomy",
                        "SSID and BSSID record enrichment",
                        "Manufacturer identification using IEEE MAC OUI databases",
                        "Historical observation comparison & baseline auditing"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, WirelessOsintActivity::class.java))
                    }
                )
            }

            // 2. Geospatial OSINT
            item {
                MinimalModuleCard(
                    icon = Icons.Default.Map,
                    iconTint = CyberYellow,
                    title = "Geospatial OSINT",
                    priorityBadge = "OSINT MAP",
                    badgeColor = CyberYellow,
                    bullets = listOf(
                        "Geographic observation mapping & coordinate projections",
                        "OpenStreetMap integration (Slippy tile calculations)",
                        "Administrative boundary overlays & municipal wards",
                        "Public infrastructure datasets (Hotspots, Transit, Cells)",
                        "Geographic density visualization & spatial metrics"
                    ),
                    onClick = {
                        context.startActivity(Intent(context, GeospatialOsintActivity::class.java))
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = CyberCyan,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = CyberTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Minimalist Card with NO Borders, Dark Background, Centered Icon, and Centered Text.
 */
@Composable
fun MinimalModuleCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    priorityBadge: String?,
    badgeColor: Color,
    bullets: List<String>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat, no elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally // Centered layout
        ) {
            // Centered Minimalist Icon Container
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(iconTint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Centered Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Centered Priority Badge (if present)
            if (priorityBadge != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = priorityBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centered Features / Bullets
            bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = CyberTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
