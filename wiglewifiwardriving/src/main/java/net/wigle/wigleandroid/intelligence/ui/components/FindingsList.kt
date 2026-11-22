package net.wigle.wigleandroid.intelligence.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Severity
import net.wigle.wigleandroid.intelligence.ui.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FindingsList(
    findings: List<Finding>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Box(modifier = modifier.fillMaxWidth()) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = CyberCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "PROCESSING INTELLIGENCE FEED...",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                }
            }
            errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, CyberPink, RoundedCornerShape(6.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "INTELLIGENCE ENGINE ALERT",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberPink
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberTextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRetryClick,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(CyberPink)
                        )
                    ) {
                        Text("RETRY", style = MaterialTheme.typography.labelSmall, color = CyberPink)
                    }
                }
            }
            findings.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NO FINDINGS IN CURRENT FILTER",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Run a live passive scan or replay a recorded session to generate analytics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberBorder
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(findings, key = { it.id }) { finding ->
                        FindingCard(finding = finding, timeStr = dateFormat.format(Date(finding.timestamp)))
                    }
                }
            }
        }
    }
}

@Composable
fun FindingCard(
    finding: Finding,
    timeStr: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val severityColor = when (finding.severity) {
        Severity.CRITICAL -> CyberPink
        Severity.HIGH -> CyberOrange
        Severity.MEDIUM -> CyberYellow
        else -> CyberCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = CyberSurface
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Severity Badge + Title + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(severityColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .border(1.dp, severityColor, RoundedCornerShape(2.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = finding.severity.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = severityColor
                        )
                    }

                    Text(
                        text = finding.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberTextPrimary,
                        maxLines = 1
                    )
                }

                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Summary
            Text(
                text = finding.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = CyberTextPrimary
            )

            // Metadata footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LOC: ${"%.4f".format(finding.latitude)}, ${"%.4f".format(finding.longitude)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberBorder
                )
                Text(
                    text = if (expanded) "LESS ▲" else "MORE ▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(CyberBackground, RoundedCornerShape(2.dp))
                        .padding(8.dp)
                ) {
                    if (!finding.ssid.isNullOrBlank()) {
                        Text(
                            text = "SSID: ${finding.ssid}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyberCyan
                        )
                    }
                    if (!finding.bssid.isNullOrBlank()) {
                        Text(
                            text = "BSSID: ${finding.bssid}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyberTextPrimary
                        )
                    }
                    Text(
                        text = "SCORE: ${"%.2f".format(finding.score)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = severityColor
                    )
                    if (finding.details.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "DETAILS: ${finding.details}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyberTextSecondary
                        )
                    }
                }
            }
        }
    }
}
