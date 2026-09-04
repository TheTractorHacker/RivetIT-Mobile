package com.foleyit.itflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A single, shared palette of semantic "meaning" colors — the roles the M3 [MaterialTheme]
 * [androidx.compose.material3.ColorScheme] doesn't have built-in slots for (there's no
 * "warning" or "success" role in Material 3), used everywhere the app needs to color a
 * ticket priority, alert severity/status, or invoice/quote status badge.
 *
 * Before this existed, every screen (Tickets, Appointments, Dashboard, Alerts, Invoices,
 * Quotes) invented its own ad-hoc `Color(0x...)` literals for the same concepts, so "high
 * priority" rendered as five different oranges/reds depending which screen you were on. All
 * of that now maps onto these six roles, so the same meaning always renders the same color.
 *
 * Each role also has a light/dark variant tuned for correct contrast when used as
 * `Surface(color = role.copy(alpha = 0.12f/0.15f)) { Text/Icon(tint = role) }` — the badge
 * pattern used throughout the app — against this theme's surface colors. A single hardcoded
 * hex cannot satisfy that in both themes at once.
 *
 * Where a role's meaning already matches an existing [androidx.compose.material3.ColorScheme]
 * role, we alias it (critical -> error, info -> primary, neutral -> outline) instead of
 * inventing a redundant new hue — one less palette to keep in sync, and it keeps these
 * "extra" states visually tied to the active brand seed. Because those three roles now vary
 * per [ColorSeed] (not just per light/dark), [statusColorsFor] takes the active [ColorScheme]
 * rather than this file exposing static per-theme constants.
 *
 * Domain mapping:
 * - **critical** — ticket/appointment priority "critical", alert severity "critical"/"error",
 *   alert status "new" (needs immediate attention), financial status "overdue"/"declined"
 * - **warning**  — ticket/appointment priority "high", alert severity "warning",
 *   alert status "acknowledged", financial status "partial"
 * - **caution**  — ticket/appointment priority "medium" (a calmer step below warning)
 * - **success**  — alert status "resolved", financial status "paid"/"accepted"
 * - **info**     — alert severity default/"info" (aliases [MaterialTheme]'s primary role)
 * - **neutral**  — ticket/appointment priority "low", any other unrecognized status
 *   (aliases [MaterialTheme]'s outline role)
 *
 * Use via `MaterialTheme.statusColors`, and prefer the `for*` mapping helpers below over
 * re-writing the same `when` block in every screen.
 */
@Immutable
data class StatusColors(
    val critical: Color,
    val warning: Color,
    val caution: Color,
    val success: Color,
    val info: Color,
    val neutral: Color,
)

/**
 * warning/caution/success are seed-independent semantic colors (same across all five
 * [ColorSeed]s, per the design reference) — only critical/info/neutral ride the active seed
 * via the aliased [ColorScheme] roles.
 */
fun statusColorsFor(colorScheme: ColorScheme, darkTheme: Boolean): StatusColors = StatusColors(
    critical = colorScheme.error,
    warning  = if (darkTheme) Color(0xFFFFB77C) else Color(0xFFE65100),
    caution  = if (darkTheme) Color(0xFFFFD968) else Color(0xFFF9A825),
    success  = if (darkTheme) Color(0xFF8BC994) else Color(0xFF1E8E3E),
    info     = colorScheme.primary,
    neutral  = colorScheme.outline,
)

internal val LocalStatusColors = staticCompositionLocalOf {
    statusColorsFor(colorSchemeFor(ColorSeed.DEFAULT, darkTheme = false), darkTheme = false)
}

/** Analogous to `MaterialTheme.colorScheme` — reads the current theme's [StatusColors]. */
val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current

/** Ticket / appointment priority -> color. Unknown or null priority reads as [StatusColors.neutral]. */
fun StatusColors.forPriority(priority: String?): Color = when (priority?.lowercase()) {
    "critical" -> critical
    "high" -> warning
    "medium" -> caution
    else -> neutral
}

/** Alert severity -> color. */
fun StatusColors.forAlertSeverity(severity: String?): Color = when (severity?.lowercase()) {
    "critical", "error" -> critical
    "warning" -> warning
    else -> info
}

/** Alert workflow status -> color. */
fun StatusColors.forAlertStatus(status: String?): Color = when (status?.lowercase()) {
    "new" -> critical
    "acknowledged" -> warning
    "resolved" -> success
    else -> neutral
}

/** Invoice / quote financial status -> color. */
fun StatusColors.forFinancialStatus(status: String?): Color = when (status) {
    "Paid", "Accepted" -> success
    "Overdue", "Declined" -> critical
    "Partial" -> warning
    else -> neutral
}
