package com.foleyit.itflow.ui.navigation

/**
 * Shared allowlist for navigable route strings. Any route reaching [androidx.navigation.NavController.navigate]
 * from outside the app's own UI (FCM push data, the `deep_link_route` Intent extra accepted by the exported
 * launcher Activity) must be validated against this before being passed to `navigate()`.
 */
object DeepLinks {
    val ALLOWED_ROUTE = Regex(
        """^(tickets|clients|assets|projects|contracts|credentials|notifications|appointments|worksheets|outtakes|search|reports|scan|profile|kb|alerts)(/\d+(/\w+)?)?$"""
    )

    /**
     * Maps an externally supplied route (FCM `action`, `deep_link_route` extra) to a real nav destination,
     * or null when it is not allowed or has no screen. "scan" is an allowed alias for the barcode scanner
     * (whose route is `scan/barcode`); bare `worksheets`/`outtakes` have no list screen, only per-id ones.
     */
    fun resolve(raw: String): String? {
        if (!ALLOWED_ROUTE.matches(raw)) return null
        return when (raw) {
            "scan" -> Screen.ScanBarcode.route
            "worksheets", "outtakes" -> null
            else -> raw
        }
    }
}
