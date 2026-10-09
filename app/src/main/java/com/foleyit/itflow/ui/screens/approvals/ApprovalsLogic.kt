package com.foleyit.itflow.ui.screens.approvals

enum class RiskBand { LOW, MEDIUM, HIGH }

object ApprovalsLogic {
    /** Bands for the 0-100 risk score chip. */
    fun riskBand(score: Int): RiskBand = when {
        score >= 70 -> RiskBand.HIGH
        score >= 40 -> RiskBand.MEDIUM
        else -> RiskBand.LOW
    }

    /** Parses "kind:id" back from a deep link's two segments; null when either is unusable. */
    fun key(kind: String?, id: Int?): String? = if (kind != null && id != null && id > 0) "$kind:$id" else null
}
