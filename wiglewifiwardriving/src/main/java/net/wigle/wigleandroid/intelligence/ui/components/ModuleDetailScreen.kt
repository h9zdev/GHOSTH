package net.wigle.wigleandroid.intelligence.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            .padding(12.dp)
    ) {
        // Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
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
                        style = MaterialTheme.typography.headlineMedium,
                        color = CyberCyan
                    )
                    Text(
                        text = "MODULE DETAIL VIEW // PASSIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberTextSecondary
                    )
                }
            }

            // Export action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(3.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(CyberCyan)
                    )
                ) {
                    Text("CSV", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                }

                OutlinedButton(
                    onClick = onExportPdf,
                    shape = RoundedCornerShape(3.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(CyberPink)
                    )
                ) {
                    Text("PDF", style = MaterialTheme.typography.labelSmall, color = CyberPink)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Time Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TIME RANGE:",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan
            )
            TimeRange.values().forEach { range ->
                val selected = (filterState.timeRange == range)
                FilterChip(
                    label = range.label,
                    selected = selected,
                    onClick = { onTimeRangeSelected(range) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Map layer for this module
        SharedMapLayer(findings = findings)

        Spacer(modifier = Modifier.height(10.dp))

        // Findings count header
        Text(
            text = "FINDINGS (${findings.size})",
            style = MaterialTheme.typography.titleMedium,
            color = CyberCyan
        )

        Spacer(modifier = Modifier.height(6.dp))

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

