package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Shared "headline stat" card for report detail screens — e.g. "Avg. Resolution Time" on the
 * CSAT/RMM/Service Desk reports. Every report screen currently renders its top-line number as
 * plain Text with no card treatment at all; this is the one shared piece the design spec calls
 * for ("hero stat card, primary-container") that didn't already exist anywhere in this file, so
 * screens can adopt it directly instead of each hand-rolling their own version.
 */
@Composable
fun ReportHeroStat(
    label: String,
    value: String,
    icon: ImageVector = Icons.Outlined.Insights,
    modifier: Modifier = Modifier,
    // Overridable for reports whose headline number carries its own semantic (e.g. a Profit &
    // Loss card that should read as an error/loss state, not brand-primary, when negative) —
    // all four default to the original fixed primary/primaryContainer look, so every call site
    // that doesn't pass these renders identically to before this parameter existed.
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = accentColor,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = onAccentColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** A labeled value with a bar proportional to [fraction] (0f..1f) of the series' max — used
 * for client/tech/category/priority/status breakdowns across the reports section. */
@Composable
fun ReportBarRow(
    label: String,
    value: String,
    fraction: Float,
    subtitle: String? = null,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium, maxLines = 1)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Text(value, fontWeight = FontWeight.SemiBold, color = barColor)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(barColor, MaterialTheme.shapes.extraSmall)
            )
        }
    }
}

private val MONTH_LABELS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/** Lightweight month-over-month bar chart (no external charting dependency). [values] and
 * [labels] must be the same length; [labels] defaults to the 12 fixed month initials used by
 * the `?year=` reports — callers driven by a variable-length `from`/`to` range pass their own. */
@Composable
fun MonthlyTrendChart(
    values: List<Double>,
    labels: List<String> = MONTH_LABELS,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val barCount = values.size.coerceAtLeast(1)
            val gap = size.width * 0.015f
            val barWidth = (size.width - gap * (barCount - 1)) / barCount
            values.forEachIndexed { i, v ->
                val barHeight = (v / max).toFloat().coerceIn(0f, 1f) * size.height
                val x = i * (barWidth + gap)
                drawRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height)
                )
                if (barHeight > 0f) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(
                    l,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** Simple prev/year/next selector shared by every `?year=` report. */
@Composable
fun YearPicker(year: Int, onYearChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onYearChange(year - 1) }) {
            Icon(Icons.Outlined.ChevronLeft, "Previous year")
        }
        Text(year.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onYearChange(year + 1) }) {
            Icon(Icons.Outlined.ChevronRight, "Next year")
        }
    }
}

fun formatSeconds(seconds: Long?): String {
    if (seconds == null) return "-"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Preset date ranges for the `from`/`to`-based reports (CSAT, RMM Health, Service Desk,
 * Technician Utilization) — a lightweight alternative to a full calendar picker, consistent
 * with the rest of the reports section's minimal chrome. */
enum class DateRangePreset(val label: String) {
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_90_DAYS("Last 90 Days"),
    THIS_YEAR("This Year")
}

private val reportDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** Computes the (from, to) 'yyyy-MM-dd' pair for a preset, anchored on "today". */
fun DateRangePreset.toRange(): Pair<String, String> {
    val to = Calendar.getInstance()
    val from = Calendar.getInstance()
    when (this) {
        DateRangePreset.THIS_MONTH -> from.set(Calendar.DAY_OF_MONTH, 1)
        DateRangePreset.LAST_30_DAYS -> from.add(Calendar.DAY_OF_YEAR, -30)
        DateRangePreset.LAST_90_DAYS -> from.add(Calendar.DAY_OF_YEAR, -90)
        DateRangePreset.THIS_YEAR -> from.set(Calendar.DAY_OF_YEAR, 1)
    }
    return reportDateFormat.format(from.time) to reportDateFormat.format(to.time)
}

/** Single-select preset chip row shared by every `from`/`to`-based report screen. */
@Composable
fun DateRangePresetPicker(selected: DateRangePreset, onSelect: (DateRangePreset) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateRangePreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { onSelect(preset) },
                label = { Text(preset.label) }
            )
        }
    }
}
