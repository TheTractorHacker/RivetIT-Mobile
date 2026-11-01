package com.foleyit.itflow.ui.screens.approvals

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
import com.foleyit.itflow.data.model.ApprovalItem
import com.foleyit.itflow.data.model.ApprovalKind
import com.foleyit.itflow.ui.components.*
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.fmtDate
import com.foleyit.itflow.ui.util.userMessage

/**
 * Approvals waiting on the signed-in user. [openKey] ("kind:id") is set by a push or deep link and opens that
 * item's detail once the list has loaded; if it is no longer waiting the user is told instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(navController: NavController, openKey: String? = null) {
    val vm: ApprovalsViewModel = viewModel(factory = ApprovalsViewModel.Factory)
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOpen by rememberSaveable(openKey) { mutableStateOf(openKey) }
    var commentError by remember { mutableStateOf(false) }

    val approvedText = stringResource(R.string.approvals_approved)
    val rejectedText = stringResource(R.string.approvals_rejected)
    val gone = stringResource(R.string.approvals_no_longer_waiting)
    val failedFmt = stringResource(R.string.approvals_decision_failed)

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is ApprovalEvent.Decided -> {
                    haptic.performHapticFeedback(if (e.approved) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                    selectedKey = null
                    commentError = false
                    snackbar.showSnackbar(if (e.approved) approvedText else rejectedText)
                }
                is ApprovalEvent.Failed -> snackbar.showSnackbar(failedFmt.format(userMessage(e.error)))
                ApprovalEvent.CommentRequired -> commentError = true
                ApprovalEvent.NoLongerWaiting -> { selectedKey = null; snackbar.showSnackbar(gone) }
            }
        }
    }

    // Deep link / push: open the item once the list is fresh enough to know whether it still exists.
    LaunchedEffect(pendingOpen, ui.load.refreshing, ui.load.data) {
        val key = pendingOpen ?: return@LaunchedEffect
        if (ui.load.data == null || ui.load.refreshing) return@LaunchedEffect
        pendingOpen = null
        if (ui.items.any { it.key == key }) selectedKey = key else snackbar.showSnackbar(gone)
    }

    val selected = ui.items.firstOrNull { it.key == selectedKey }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            Text(
                stringResource(R.string.approvals_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
            val load = ui.load
            when {
                load.isInitialLoading -> LoadingScreen()
                load.isInitialError -> ErrorScreen(userMessage(load.error!!), onRetry = vm::refresh)
                else -> PullToRefreshBox(isRefreshing = load.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    if (ui.items.isEmpty()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (load.error != null) PagedListStatus(false, userMessage(load.error), vm::refresh)
                            Box(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
                                EmptyScreen(stringResource(R.string.approvals_empty), Icons.Outlined.TaskAlt)
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (load.error != null) {
                                item(key = "status") { PagedListStatus(false, userMessage(load.error), vm::refresh) }
                            } else if (load.fromCache && load.refreshing) {
                                item(key = "stale") {
                                    Text(
                                        stringResource(R.string.showing_saved_data),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(ui.items, key = { it.key }) { item ->
                                ApprovalCard(item, modifier = Modifier.animateItem()) { selectedKey = item.key; commentError = false }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selected != null) {
        ApprovalSheet(
            item = selected,
            deciding = selected.key in ui.deciding,
            commentError = commentError,
            onCommentChange = { commentError = false },
            onDismiss = { selectedKey = null; commentError = false },
            onOpenTicket = { id -> selectedKey = null; navController.navigate(Screen.TicketDetail.go(id)) },
            onDecide = { approve, comment -> vm.decide(selected, approve, comment) },
        )
    }
}

@Composable
private fun riskColor(score: Int) = when (ApprovalsLogic.riskBand(score)) {
    RiskBand.HIGH -> MaterialTheme.statusColors.critical
    RiskBand.MEDIUM -> MaterialTheme.statusColors.warning
    RiskBand.LOW -> MaterialTheme.statusColors.success
}

@Composable
private fun riskLabel(score: Int) = stringResource(
    when (ApprovalsLogic.riskBand(score)) {
        RiskBand.HIGH -> R.string.risk_high
        RiskBand.MEDIUM -> R.string.risk_medium
        RiskBand.LOW -> R.string.risk_low
    }
)

@Composable
private fun RiskChip(score: Int) {
    val desc = stringResource(R.string.approvals_risk_desc, score, riskLabel(score))
    StatusChip(
        stringResource(R.string.approvals_risk, score), riskColor(score),
        modifier = Modifier.semantics { contentDescription = desc },
    )
}

@Composable
private fun ApprovalCard(item: ApprovalItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(
                    item.requester.takeIf { it.isNotBlank() }?.let { stringResource(R.string.approvals_requested_by, it) },
                    item.requestedAt?.let { fmtDate(it) }?.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.summary.isNotBlank()) {
                Text(item.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusChip(
                    stringResource(if (item.kind == ApprovalKind.CATALOG_REQUEST) R.string.approvals_kind_request else R.string.approvals_kind_task),
                    MaterialTheme.statusColors.info,
                )
                item.riskScore?.let { RiskChip(it) }
                item.step?.let { StatusChip(stringResource(R.string.approvals_step, it), MaterialTheme.statusColors.neutral) }
                item.dueAt?.let { StatusChip(stringResource(R.string.approvals_due, fmtDate(it)), MaterialTheme.statusColors.caution) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ApprovalSheet(
    item: ApprovalItem,
    deciding: Boolean,
    commentError: Boolean,
    onCommentChange: () -> Unit,
    onDismiss: () -> Unit,
    onOpenTicket: (Int) -> Unit,
    onDecide: (approve: Boolean, comment: String) -> Unit,
) {
    var comment by rememberSaveable(item.key) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { if (!deciding) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(
                listOfNotNull(
                    item.requester.takeIf { it.isNotBlank() }?.let { stringResource(R.string.approvals_requested_by, it) },
                    item.requestedAt?.let { stringResource(R.string.approvals_requested_at, fmtDate(it)) },
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.riskScore?.let { RiskChip(it) }
                item.step?.let { StatusChip(stringResource(R.string.approvals_step, it), MaterialTheme.statusColors.neutral) }
                item.dueAt?.let { StatusChip(stringResource(R.string.approvals_due, fmtDate(it)), MaterialTheme.statusColors.caution) }
            }
            if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodyLarge)

            SectionLabel(stringResource(R.string.approvals_answers))
            if (item.fields.isEmpty()) {
                Text(stringResource(R.string.approvals_no_answers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                item.fields.forEach { f ->
                    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                        Text(f.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(f.value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item.ticketId?.let { id ->
                OutlinedButton(onClick = { onOpenTicket(id) }, enabled = !deciding) {
                    Icon(Icons.Outlined.ConfirmationNumber, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.approvals_open_ticket, id))
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it; onCommentChange() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !deciding,
                label = { Text(stringResource(R.string.approvals_comment_label)) },
                placeholder = { Text(stringResource(R.string.approvals_comment_hint)) },
                isError = commentError,
                supportingText = if (commentError) ({ Text(stringResource(R.string.approvals_comment_required)) }) else null,
                minLines = 2, maxLines = 5,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onDecide(true, comment) }, enabled = !deciding) { Text(stringResource(R.string.approvals_approve)) }
                OutlinedButton(
                    onClick = { onDecide(false, comment) }, enabled = !deciding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.approvals_reject)) }
                if (deciding) {
                    val desc = stringResource(R.string.approvals_deciding)
                    CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically).semantics { contentDescription = desc }, strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
