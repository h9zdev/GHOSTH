package net.wigle.wigleandroid.intelligence.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.wigle.wigleandroid.intelligence.ui.*

@Composable
fun FilterBar(
    filterState: FilterState,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onBandSelected: (BandFilter) -> Unit,
    onSecuritySelected: (SecurityFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        // Time Range Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TIME:",
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

        Spacer(modifier = Modifier.height(6.dp))

        // Band Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BAND:",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan
            )
            BandFilter.values().forEach { band ->
                val selected = (filterState.band == band)
                FilterChip(
                    label = band.label,
                    selected = selected,
                    onClick = { onBandSelected(band) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Security Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SEC:",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan
            )
            SecurityFilter.values().forEach { sec ->
                val selected = (filterState.security == sec)
                FilterChip(
                    label = sec.label,
                    selected = selected,
                    onClick = { onSecuritySelected(sec) }
                )
            }
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (selected) CyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(3.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) CyberCyan else CyberBorder,
                shape = RoundedCornerShape(3.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) CyberCyan else CyberTextSecondary
        )
    }
}

