package net.wigle.wigleandroid.osint.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wigle.wigleandroid.intelligence.ui.*
import net.wigle.wigleandroid.osint.model.WirelessOsintFinding

class WirelessOsintActivity : ComponentActivity() {

    private val viewModel: OsintViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntelligenceTheme {
                WirelessOsintScreen(
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun WirelessOsintScreen(
    viewModel: OsintViewModel,
    onFinish: () -> Unit
) {
    val findings by viewModel.wirelessFindings.collectAsStateWithLifecycle()
    val filter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val filteredFindings = remember(findings, filter) {
        when (filter) {
            "PUBLIC" -> findings.filter { it.isPublicHotspot }
            "OUI_IDENTIFIED" -> findings.filter { !it.manufacturer.contains("Unknown") }
            "HIGH_SECURITY" -> findings.filter { it.securityType.contains("WPA3") || it.securityType.contains("802.1X") }
            else -> findings
        }
    }

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
                    text = "WIRELESS OSINT",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )
                Text(
                    text = "PASSIVE RECONNAISSANCE & OUI ENRICHMENT",
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

        // Minimalist Centered Metrics Card - No Border
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
                    text = "WiGLE API & IEEE OUI DATABASE",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CyberCyan,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Passive Wireless Intelligence & SSID Enrichment",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OsintMetricItem(label = "OBSERVED", value = "${findings.size}", color = CyberCyan)
                    OsintMetricItem(label = "PUBLIC", value = "${findings.count { it.isPublicHotspot }}", color = CyberYellow)
                    OsintMetricItem(label = "OUI RESOLVED", value = "${findings.count { !it.manufacturer.contains("Unknown") }}", color = CyberGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Minimal Filter Pills - No Border
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "PUBLIC", "OUI_IDENTIFIED", "HIGH_SECURITY").forEach { filterKey ->
                val selected = filter == filterKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selected) CyberCyan.copy(alpha = 0.2f) else CyberSurface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.setFilter(filterKey) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filterKey.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) CyberCyan else CyberTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Enriched Findings List
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyberCyan)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFindings, key = { it.id }) { item ->
                    WirelessOsintFindingCard(item)
                }
            }
        }
    }
}

@Composable
fun OsintMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CyberTextSecondary
        )
    }
}

@Composable
fun WirelessOsintFindingCard(finding: WirelessOsintFinding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = finding.ssid,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (finding.isPublicHotspot) CyberYellow.copy(alpha = 0.15f) else CyberCyan.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (finding.isPublicHotspot) "PUBLIC HOTSPOT" else finding.securityType,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (finding.isPublicHotspot) CyberYellow else CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "BSSID: ${finding.bssid}  •  OUI: ${finding.manufacturer}",
                style = MaterialTheme.typography.bodySmall,
                color = CyberGreen
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = finding.publicCategory,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSecondary
                )
                Text(
                    text = "${finding.frequency} MHz  (${finding.rssi} dBm)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSecondary
                )
            }
        }
    }
}

