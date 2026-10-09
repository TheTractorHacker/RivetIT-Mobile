package com.foleyit.itflow.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Counts shown on the navigation entries: approvals waiting on me, and my overdue workflow tasks. */
class FeatureBadges {
    private val _approvals = MutableStateFlow(0)
    private val _overdueTasks = MutableStateFlow(0)
    val approvals: StateFlow<Int> = _approvals.asStateFlow()
    val overdueTasks: StateFlow<Int> = _overdueTasks.asStateFlow()

    fun setApprovals(n: Int) { _approvals.value = n.coerceAtLeast(0) }
    fun setOverdueTasks(n: Int) { _overdueTasks.value = n.coerceAtLeast(0) }
    fun clear() { setApprovals(0); setOverdueTasks(0) }

    companion object { val shared = FeatureBadges() }
}
