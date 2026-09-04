package com.foleyit.itflow.ui.navigation

/**
 * Shared allowlist for navigable route strings. Any route reaching [androidx.navigation.NavController.navigate]
 * from outside the app's own UI (FCM push data, the `deep_link_route` Intent extra accepted by the exported
 * launcher Activity) must be validated against this before being passed to `navigate()`.
 */
object DeepLinks {
    val ALLOWED_ROUTE = Regex(
        """^(tickets|clients|assets|credentials|quotes|invoices|expenses|notifications|appointments|worksheets|outtakes|search|reports|scan|profile|kb|alerts)(/\d+(/\w+)?)?$"""
    )
}
