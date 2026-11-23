package net.wigle.wigleandroid.intelligence.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.ui.*

@Composable
fun ModuleDetailScreen(
    module: AnalyzerType,
    findings: List<Finding>,
    filterState: FilterState,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(16.dp)
    ) {
        // Navigation Header - Flat, No Border
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
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

                Column {
                    Text(
                        text = module.displayName.uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = CyberCyan
                    )
                    Text(
                        text = "MODULE DETAIL VIEW // PASSIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberTextSecondary
                    )
                }
            }

            // Export action buttons - Clean flat design, no border
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .clickable { onExportCsv() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("CSV", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                }

                Box(
                    modifier = Modifier
                        .background(CyberPink.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .clickable { onExportPdf() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("PDF", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberPink)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Time Filter Pills - Flat, No Border
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TIME:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CyberCyan
            )
            TimeRange.values().forEach { range ->
                val selected = (filterState.timeRange == range)
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) CyberCyan.copy(alpha = 0.25f) else CyberSurface,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onTimeRangeSelected(range) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = range.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) CyberCyan else CyberTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Map layer for this module
        SharedMapLayer(findings = findings)

        Spacer(modifier = Modifier.height(14.dp))

        // Findings count header
        Text(
            text = "FINDINGS (${findings.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = CyberCyan
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Findings list
        FindingsList(
            findings = findings,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onRetryClick = onRetryClick,
            modifier = Modifier.weight(1f)
        )
    }
}
