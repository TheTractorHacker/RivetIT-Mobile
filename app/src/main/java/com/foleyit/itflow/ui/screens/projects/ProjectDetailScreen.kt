package com.foleyit.itflow.ui.screens.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ProjectDetail
import com.foleyit.itflow.data.api.ProjectMilestone
import com.foleyit.itflow.data.api.ProjectTask
import com.foleyit.itflow.data.api.ProjectTicket
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.SectionLabel
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.util.fmtDate
import kotlinx.coroutines.launch

private fun parseHexColor(hex: String?): Color? = try {
    if (hex.isNullOrBlank()) null
    else Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
} catch (_: Exception) { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(id: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<ProjectDetail>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() { scope.launch { state = runCatching { ApiClient.service().getProject(id) } } }
    LaunchedEffect(Unit) { load() }

    val project = state?.getOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(project?.let { "${it.prefix ?: ""}${it.number}" } ?: "Project")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "Error", onRetry = ::load)
            else -> {
                val p = state!!.getOrThrow()
                val ticketsClosed = p.tickets.count { it.closedAt != null }
                val tasksCompleted = p.tasks.count { it.completedAt != null }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    listOfNotNull(p.client, p.manager?.let { "Managed by $it" }).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                val statusLabel = when {
                                    p.archivedAt != null -> "Archived"
                                    p.completedAt != null -> "Completed"
                                    else -> "Open"
                                }
                                val statusColor = when {
                                    p.archivedAt != null -> MaterialTheme.colorScheme.outline
                                    p.completedAt != null -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                    Text(statusLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium, color = statusColor)
                                }
                            }
                        }
                    }

                    // Dates / planning
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                SectionLabel("Timeline")
                                ProjectDetailRow("Start", fmtDate(p.startAt))
                                ProjectDetailRow("Due", fmtDate(p.dueAt))
                                ProjectDetailRow("Created", fmtDate(p.createdAt))
                                ProjectDetailRow("Completed", fmtDate(p.completedAt))
                                if (p.estimatedHours != null) ProjectDetailRow("Estimated Hours", "${p.estimatedHours}")
                                if (p.budgetAmount != null) ProjectDetailRow("Budget", "$${p.budgetAmount}")
                            }
                        }
                    }

                    // Description
                    if (!p.description.isNullOrBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    SectionLabel("Description")
                                    Text(p.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // Progress
                    if (p.tasks.isNotEmpty() || p.tickets.isNotEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    SectionLabel("Progress")
                                    if (p.tasks.isNotEmpty()) {
                                        ProgressBarRow("Tasks", tasksCompleted, p.tasks.size)
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    if (p.tickets.isNotEmpty()) {
                                        ProgressBarRow("Tickets", ticketsClosed, p.tickets.size)
                                    }
                                }
                            }
                        }
                    }

                    // Milestones
                    if (p.milestones.isNotEmpty()) {
                        item { SectionLabel("Milestones") }
                        items(p.milestones, key = { "m${it.id}" }) { m -> MilestoneRow(m) }
                    }

                    // Tasks
                    if (p.tasks.isNotEmpty()) {
                        item { SectionLabel("Tasks") }
                        items(p.tasks, key = { "t${it.id}" }) { t -> TaskRow(t) }
                    }

                    // Linked tickets
                    if (p.tickets.isNotEmpty()) {
                        item { SectionLabel("Linked Tickets") }
                        items(p.tickets, key = { "tk${it.id}" }) { t ->
                            TicketRow(t) { navController.navigate(Screen.TicketDetail.go(t.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProgressBarRow(label: String, done: Int, total: Int) {
    val progress = if (total > 0) done.toFloat() / total.toFloat() else 0f
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$done/$total", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraLarge)
        )
    }
}

@Composable
private fun MilestoneRow(m: ProjectMilestone) {
    val completed = m.status == "completed" || m.completedAt != null
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        ListItem(
            leadingContent = {
                Icon(
                    if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    null,
                    tint = if (completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                )
            },
            headlineContent = {
                Text(m.name, textDecoration = if (completed) TextDecoration.LineThrough else null)
            },
            supportingContent = if (!m.dueAt.isNullOrBlank()) {
                { Text("Due ${fmtDate(m.dueAt)}", style = MaterialTheme.typography.labelSmall) }
            } else null
        )
    }
}

@Composable
private fun TaskRow(t: ProjectTask) {
    val completed = t.completedAt != null
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        ListItem(
            leadingContent = {
                Icon(
                    if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    null,
                    tint = if (completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                )
            },
            headlineContent = {
                Text(t.name, textDecoration = if (completed) TextDecoration.LineThrough else null)
            },
            supportingContent = {
                val parts = listOfNotNull(
                    t.assignedTo,
                    t.dueAt?.let { "Due ${fmtDate(it)}" },
                    t.ticketNumber?.let { "Ticket $it" }
                )
                if (parts.isNotEmpty()) Text(parts.joinToString(" • "), style = MaterialTheme.typography.labelSmall)
            }
        )
    }
}

@Composable
private fun TicketRow(t: ProjectTicket, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick) {
        ListItem(
            leadingContent = {
                val color = parseHexColor(t.statusColor) ?: MaterialTheme.colorScheme.outline
                Box(
                    Modifier.size(10.dp).clip(MaterialTheme.shapes.extraLarge)
                        .background(color)
                )
            },
            headlineContent = { Text(t.subject, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text(listOfNotNull(t.number, t.status).joinToString(" • "), style = MaterialTheme.typography.labelSmall)
            },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }
        )
    }
}
