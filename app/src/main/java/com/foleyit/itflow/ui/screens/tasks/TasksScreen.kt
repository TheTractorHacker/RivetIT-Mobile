package com.foleyit.itflow.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.foleyit.itflow.R
import com.foleyit.itflow.data.model.TaskStatus
import com.foleyit.itflow.data.model.TaskType
import com.foleyit.itflow.data.model.WorkflowTask
import com.foleyit.itflow.ui.components.*
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.fmtDate
import com.foleyit.itflow.ui.util.userMessage
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(navController: NavController) {
    val vm: TasksViewModel = viewModel(factory = TasksViewModel.Factory)
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var skipTarget by remember { mutableStateOf<WorkflowTask?>(null) }
    var reasonError by remember { mutableStateOf(false) }

    val completedText = stringResource(R.string.tasks_completed)
    val skippedText = stringResource(R.string.tasks_skipped)
    val changedText = stringResource(R.string.tasks_changed)
    val failedFmt = stringResource(R.string.tasks_action_failed_msg)

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is TaskEvent.Completed -> { haptic.performHapticFeedback(HapticFeedbackType.Confirm); snackbar.showSnackbar(completedText) }
                is TaskEvent.Skipped -> { haptic.performHapticFeedback(HapticFeedbackType.Confirm); skipTarget = null; snackbar.showSnackbar(skippedText) }
                is TaskEvent.Failed -> snackbar.showSnackbar(failedFmt.format(userMessage(e.error)))
                TaskEvent.ReasonRequired -> reasonError = true
                TaskEvent.Changed -> { skipTarget = null; snackbar.showSnackbar(changedText) }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            Text(
                stringResource(R.string.tasks_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
            val load = ui.load
            when {
                load.isInitialLoading -> LoadingScreen()
                load.isInitialError -> ErrorScreen(userMessage(load.error!!), onRetry = vm::refresh)
                else -> PullToRefreshBox(isRefreshing = load.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    val groups = remember(ui.items) { TasksLogic.group(ui.items) }
                    if (groups.isEmpty()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (load.error != null) PagedListStatus(false, userMessage(load.error), vm::refresh)
                            Box(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
                                EmptyScreen(stringResource(R.string.tasks_empty), Icons.Outlined.Checklist)
                            }
                        }
                    } else {
                        val now = remember(ui.items) { LocalDateTime.now() }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (load.error != null) item(key = "status") { PagedListStatus(false, userMessage(load.error), vm::refresh) }
                            else if (load.fromCache && load.refreshing) item(key = "stale") {
                                Text(stringResource(R.string.showing_saved_data), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            groups.forEach { g ->
                                item(key = "run_${g.runId}") {
                                    Column(Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) { heading() }) {
                                        Text(g.runTitle.ifBlank { "#${g.runId}" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        if (g.contactName.isNotBlank()) {
                                            Text(stringResource(R.string.tasks_for_person, g.contactName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                items(g.tasks, key = { "task_${it.id}" }) { t ->
                                    TaskCard(
                                        t, now, busy = t.id in ui.acting, modifier = Modifier.animateItem(),
                                        onComplete = { vm.complete(t) },
                                        onSkip = { reasonError = false; skipTarget = t },
                                        onOpenApprovals = { navController.navigate(Screen.Approvals.route) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    skipTarget?.let { t ->
        SkipDialog(
            task = t, error = reasonError, busy = t.id in ui.acting,
            onDismiss = { skipTarget = null; reasonError = false },
            onChange = { reasonError = false },
            onConfirm = { reason -> vm.skip(t, reason) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    t: WorkflowTask, now: LocalDateTime, busy: Boolean, modifier: Modifier,
    onComplete: () -> Unit, onSkip: () -> Unit, onOpenApprovals: () -> Unit,
) {
    val blocked = TasksLogic.isBlocked(t)
    val chip = TasksLogic.dueChip(t, now)
    Card(modifier = modifier.fillMaxWidth().then(if (blocked) Modifier.alpha(0.6f) else Modifier), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.taskTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (chip) {
                    DueChip.OVERDUE -> StatusChip(stringResource(R.string.tasks_overdue) + " · " + fmtDate(t.dueAt), MaterialTheme.statusColors.critical, icon = Icons.Outlined.ErrorOutline)
                    DueChip.DUE_SOON -> StatusChip(stringResource(R.string.tasks_due_soon) + " · " + fmtDate(t.dueAt), MaterialTheme.statusColors.warning, icon = Icons.Outlined.Schedule)
                    DueChip.NONE -> if (t.dueAt != null && t.status != TaskStatus.COMPLETED && t.status != TaskStatus.SKIPPED) {
                        StatusChip(stringResource(R.string.tasks_due, fmtDate(t.dueAt)), MaterialTheme.statusColors.neutral)
                    }
                }
                when (t.status) {
                    TaskStatus.COMPLETED -> StatusChip(stringResource(R.string.tasks_status_completed), MaterialTheme.statusColors.success, icon = Icons.Outlined.CheckCircle)
                    TaskStatus.SKIPPED -> StatusChip(stringResource(R.string.tasks_status_skipped), MaterialTheme.statusColors.neutral)
                    TaskStatus.RUNNING -> StatusChip(stringResource(R.string.tasks_status_running), MaterialTheme.statusColors.info)
                    TaskStatus.REJECTED -> StatusChip(stringResource(R.string.tasks_status_rejected), MaterialTheme.statusColors.critical)
                    TaskStatus.UNKNOWN -> StatusChip(stringResource(R.string.tasks_status_unknown), MaterialTheme.statusColors.neutral)
                    else -> {}
                }
                if (t.type == TaskType.APPROVAL && t.status != TaskStatus.COMPLETED && t.status != TaskStatus.SKIPPED) {
                    StatusChip(stringResource(R.string.tasks_needs_approval), MaterialTheme.statusColors.info, icon = Icons.Outlined.HowToReg)
                }
            }
            if (t.instructions.isNotBlank()) {
                Text(t.instructions, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (blocked && t.blockedBy.isNotEmpty()) {
                Text(
                    stringResource(R.string.tasks_waiting_for, t.blockedBy.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                t.status == TaskStatus.COMPLETED || t.status == TaskStatus.SKIPPED ||
                    t.status == TaskStatus.RUNNING || t.status == TaskStatus.REJECTED || t.status == TaskStatus.UNKNOWN -> {}
                t.type == TaskType.APPROVAL ->
                    if (!blocked) OutlinedButton(onClick = onOpenApprovals) { Text(stringResource(R.string.tasks_open_approvals)) }
                t.type == TaskType.ACTION ->
                    Text(
                        stringResource(if (t.status == TaskStatus.ACTION_FAILED) R.string.tasks_action_failed else R.string.tasks_automatic),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (t.status == TaskStatus.ACTION_FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Blocked tasks stay visible but disabled; the card is already dimmed.
                    Button(onClick = onComplete, enabled = TasksLogic.canAct(t) && !busy) { Text(stringResource(R.string.tasks_complete)) }
                    TextButton(onClick = onSkip, enabled = TasksLogic.canAct(t) && !busy) { Text(stringResource(R.string.tasks_skip)) }
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun SkipDialog(task: WorkflowTask, error: Boolean, busy: Boolean, onDismiss: () -> Unit, onChange: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable(task.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.tasks_skip_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.taskTitle, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it; onChange() },
                    label = { Text(stringResource(R.string.tasks_skip_reason)) },
                    isError = error, enabled = !busy,
                    supportingText = if (error) ({ Text(stringResource(R.string.tasks_skip_reason_required)) }) else null,
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }, enabled = !busy) { Text(stringResource(R.string.tasks_skip_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}
