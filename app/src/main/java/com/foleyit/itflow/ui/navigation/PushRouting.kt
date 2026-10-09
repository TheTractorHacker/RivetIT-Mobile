package com.foleyit.itflow.ui.navigation

import com.foleyit.itflow.data.model.ApprovalKind

/**
 * Turns push data and notification-list entries into an in-app route. Everything returned has already passed
 * [DeepLinks.ALLOWED_ROUTE]; unknown or malformed input yields null (the notification then just opens the app).
 */
object PushRouting {
    const val TYPE_APPROVAL = "approval"

    /** `{"type":"approval","kind":"catalog_request"|"workflow_task","id":N}` -> `approvals/<kind>/<id>`. */
    fun approvalRoute(kind: String?, id: String?): String? {
        val k = ApprovalKind.fromWire(kind) ?: return null
        val n = id?.trim()?.takeIf { it.length in 1..9 && it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return "approvals/${k.wire}/$n".takeIf { DeepLinks.ALLOWED_ROUTE.matches(it) }
    }

    /** Route for an FCM data payload: the typed approval payload first, then the generic `action` route. */
    fun routeForPush(data: Map<String, String>): String? {
        if (data["type"] == TYPE_APPROVAL) {
            approvalRoute(data["kind"], data["id"])?.let { return it }
            return "approvals"
        }
        return data["action"]?.takeIf { DeepLinks.ALLOWED_ROUTE.matches(it) }
    }

    /**
     * Route for a notifications-list entry: its server-provided `action` route when it is allowed, otherwise the
     * stable `type` (an approval notification opens the approvals list).
     */
    fun routeForNotification(type: String?, action: String?, kind: String? = null, refId: String? = null): String? {
        if (type == TYPE_APPROVAL) return approvalRoute(kind, refId) ?: "approvals"
        action?.takeIf { DeepLinks.ALLOWED_ROUTE.matches(it) }?.let { return it }
        return null
    }
}
