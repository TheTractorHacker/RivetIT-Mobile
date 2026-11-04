package com.foleyit.itflow.ui.screens.dashboard

import androidx.compose.foundation.background
import com.foleyit.itflow.ui.util.userMessage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.Appointment
import com.foleyit.itflow.data.api.DashboardResponse
import com.foleyit.itflow.data.api.TicketSummary
import com.foleyit.itflow.data.local.WorkClock
import com.foleyit.itflow.data.local.WorkSession
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.pressScale
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.forPriority
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.fmtDate
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun DashboardScreen(navController: NavController) {
    var state by remember { mutableStateOf<Result<DashboardResponse>?>(null) }
    var appointmentsToday by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var activeAlerts by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { WorkClock.attach(context) }
    val clock by WorkClock.state.collectAsState()
    // Read-only roles cannot add time to a ticket, so they get no start button.
    val canClock = com.foleyit.itflow.data.LocalCapabilities.current.canWrite(com.foleyit.itflow.data.Capabilities.SUPPORT)

    fun load() {
        scope.launch {
            val result = runCatching { ApiClient.service().getDashboard() }
            state = result
            result.getOrNull()?.let { dash ->
                context.getSharedPreferences("widget_cache", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putInt("open", dash.allOpen)
                    .putInt("overdue", dash.overdue)
                    .apply()
                val intent = android.content.Intent("android.appwidget.action.APPWIDGET_UPDATE")
                context.sendBroadcast(intent)
            }
        }
        scope.launch {
            appointmentsToday = runCatching {
                ApiClient.service().getAppointments(when_ = "today", mine = 1)
            }.getOrDefault(emptyList())
        }
        scope.launch {
            activeAlerts = runCatching { ApiClient.service().getAlerts(status = "new").total }.getOrDefault(0)
        }
    }

    LaunchedEffect(Unit) { load() }

    when {
        state == null -> LoadingScreen()
        state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.let { userMessage(it) } ?: "Error", onRetry = ::load)
        else -> {
            val dash = state!!.getOrThrow()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11  -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..20 -> "Good evening"
                else      -> "Working late?"
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Greeting header
                item {
                    Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                        Text(
                            greeting,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        if (dash.unread > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Notifications, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${dash.unread} unread notification${if (dash.unread != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                "All caught up",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Quick actions
                item {
                    QuickActionsRow(navController)
                }

                // One-line pulse: only the counts that are not zero, so nothing takes space to say "0".
                item {
                    val pulse = buildList {
                        if (dash.overdue > 0) add(PulseChip(dash.overdue, "overdue", true) { navController.navigate(Screen.Tickets.route) })
                        add(PulseChip(dash.allOpen, "open", false) { navController.navigate(Screen.Tickets.route) })
                        if ((dash.dueToday ?: 0) > 0) add(PulseChip(dash.dueToday!!, "due today", false) { navController.navigate(Screen.Tickets.route) })
                        if ((dash.onsiteOpen ?: 0) > 0) add(PulseChip(dash.onsiteOpen!!, "on-site", false) { navController.navigate(Screen.Tickets.route) })
                        if (activeAlerts > 0) add(PulseChip(activeAlerts, "alerts", true) { navController.navigate(Screen.Alerts.route) })
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pulse.forEach { PulseChipView(it) }
                    }
                }

                if (appointmentsToday.isNotEmpty()) {
                    item {
                        Text(
                            "Today's Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(appointmentsToday) { appt ->
                        AppointmentSummaryCard(appt) {
                            navController.navigate(Screen.TicketDetail.go(appt.ticketId))
                        }
                    }
                }

                // My Queue section header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "My Queue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (dash.queue.isNotEmpty()) {
                                Text(
                                    "${dash.queue.size} ticket${if (dash.queue.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        TextButton(onClick = { navController.navigate(Screen.Tickets.route) }) {
                            Text("See all")
                        }
                    }
                }

                if (dash.queue.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Queue is clear!", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "No tickets assigned to you",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    items(dash.queue.take(5), key = { it.id }) { ticket ->
                        QueueTicketCard(
                            ticket,
                            session = clock.sessionFor(ticket.id),
                            canClock = canClock,
                            onToggleClock = {
                                val running = clock.sessionFor(ticket.id)?.running == true
                                if (running) WorkClock.pause(ticket.id)
                                else WorkClock.start(ticket.id, ticket.number, ticket.subject, ticket.client)
                            },
                        ) {
                            navController.navigate(Screen.TicketDetail.go(ticket.id))
                        }
                    }
                }
            }
        }
    }
}

private data class PulseChip(val value: Int, val label: String, val urgent: Boolean, val onClick: () -> Unit)

@Composable
private fun PulseChipView(chip: PulseChip) {
    Surface(
        onClick = chip.onClick,
        shape = MaterialTheme.shapes.large,
        color = if (chip.urgent) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (chip.urgent) MaterialTheme.colorScheme.onErrorContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(chip.value.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(5.dp))
            Text(chip.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun QuickActionsRow(navController: NavController) {
    val glowColor = MaterialTheme.colorScheme.primary
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (com.foleyit.itflow.data.LocalCapabilities.current.canWrite(com.foleyit.itflow.data.Capabilities.SUPPORT)) {
        Surface(
            onClick = { navController.navigate(Screen.CreateTicket.route) },
            modifier = Modifier
                .weight(1f)
                .pressScale()
                .shadow(
                    elevation = 10.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    ambientColor = glowColor,
                    spotColor = glowColor
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gradient)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Ticket",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        }
        OutlinedButton(
            onClick = { navController.navigate(Screen.ScanBarcode.route) },
            modifier = Modifier.weight(1f).pressScale(),
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Scan", style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick = { navController.navigate(Screen.Search.route) },
            modifier = Modifier.weight(1f).pressScale(),
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Search", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AppointmentSummaryCard(appt: Appointment, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (appt.onsite) Icons.Outlined.LocationOn else Icons.Outlined.VideoCall,
                null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                appt.schedule?.let {
                    Text(fmtDate(it), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                }
                Text(appt.subject, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 1)
                appt.client?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun QueueTicketCard(
    ticket: TicketSummary,
    session: WorkSession?,
    canClock: Boolean,
    onToggleClock: () -> Unit,
    onClick: () -> Unit,
) {
    val priorityColor = MaterialTheme.statusColors.forPriority(ticket.priority)
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = priorityColor,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.width(4.dp).height(56.dp)
            ) {}
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${ticket.number}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    ticket.assignedTo?.let { assigned ->
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.Person, null, Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(2.dp))
                        Text(assigned, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    ticket.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Business, null, Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    Text(ticket.client ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                ticket.status?.let { status ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            status,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                if (canClock) {
                    val running = session?.running == true
                    FilledTonalIconButton(
                        onClick = onToggleClock,
                        modifier = Modifier.padding(top = 6.dp).size(40.dp),
                    ) {
                        Icon(
                            if (running) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                            contentDescription = if (running) "Pause timer on #${ticket.number}"
                                                 else "Start timer on #${ticket.number}",
                        )
                    }
                }
            }
        }
    }
}
