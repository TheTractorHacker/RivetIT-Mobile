package com.foleyit.itflow.data

import androidx.compose.runtime.compositionLocalOf

/**
 * What the signed-in user may see and change, built from `/me` (`is_admin` and per-module `permissions`
 * levels: 0 none, 1 read, 2 write, 3 full). Used only to avoid showing actions that would be refused;
 * the server still enforces every permission.
 *
 * A server that doesn't report permissions (older builds) leaves [permissions] null, which keeps the
 * previous behaviour of showing everything.
 */
data class Capabilities(
    val isAdmin: Boolean = false,
    val permissions: Map<String, Int>? = null,
    /** `/me` `limited`: a module-only or otherwise restricted login that must not see approvals, requests or tasks. */
    val limited: Boolean = false,
) {

    fun canView(module: String): Boolean = level(module) >= 1
    fun canWrite(module: String): Boolean = level(module) >= 2

    /** Approvals and workflow tasks: any full (non-limited) login; the server answers 403 to a non-approver. */
    fun canUseApprovals(): Boolean = !limited
    fun canUseTasks(): Boolean = !limited

    /** Filing a request creates a ticket, so it needs the same right as creating one. */
    fun canRequest(): Boolean = !limited && canWrite(SUPPORT)

    private fun level(module: String): Int = when {
        isAdmin || permissions == null -> FULL
        else -> permissions[module] ?: 0
    }

    companion object {
        const val SUPPORT = "module_support"
        const val CLIENT = "module_client"
        const val CREDENTIAL = "module_credential"
        const val KB = "module_kb"
        const val REPORTING = "module_reporting"
        const val ASSETS = "module_assets"
        const val RMM_ALERTS = "module_rmm_alerts"
        private const val FULL = 3

        val Unrestricted = Capabilities()
    }
}

val LocalCapabilities = compositionLocalOf { Capabilities.Unrestricted }
