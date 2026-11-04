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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.foleyit.itflow.data.Capabilities
import com.foleyit.itflow.data.LocalCapabilities
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.Appointment
import com.foleyit.itflow.data.api.DashboardResponse
import com.foleyit.itflow.data.api.TicketSummary
import com.foleyit.itflow.data.timer.ActiveTimer
import com.foleyit.itflow.data.timer.TimerSnapshot
import com.foleyit.itflow.data.timer.formatClock
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.pressScale
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.forPriority
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.fmtDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

// Last good dashboard, so coming back to Home paints instantly instead of a full-screen spinner.
private var cachedDashboard: DashboardResponse? = null

/** How many ranked tickets Home lists; the rest are one tap away under "See all". */
private const val QUEUE_ROWS = 8

@Composable
fun DashboardScreen(navController: NavController) {
    var state by remember { mutableStateOf<Result<DashboardResponse>?>(cachedDashboard?.let { Result.success(it) }) }
    var appointmentsToday by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var activeAlerts by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { ActiveTimer.ensureLoaded(context) }
    val timer by ActiveTimer.state.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer?.running) {
        while (timer?.running == true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val canWrite = LocalCapabilities.current.canWrite(Capabilities.SUPPORT)

    fun load() {
        scope.launch {
            val result = runCatching { ApiClient.service().getDashboard() }
            // Keep showing the last good data if a refresh fails; only surface the error when there is none.
            if (result.isSuccess || state?.isSuccess != true) state = result
            result.getOrNull()?.let { dash ->
                cachedDashboard = dash
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
            val ranked = remember(dash.queue, nowMs / 60_000) { rankQueue(dash.queue, System.currentTimeMillis()) }
            // The ticket the Now card features: the timer's ticket if one is running, else the top-ranked.
            val featured = timer?.let { t -> ranked.firstOrNull { it.id == t.ticketId } } ?: ranked.firstOrNull()
            val listed = ranked.filter { it.id != featured?.id && it.id != timer?.ticketId }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(greeting, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (dash.unread > 0) {
                            Row(
                                modifier = Modifier.clickable { navController.navigate(Screen.Notifications.route) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Notifications, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${dash.unread} unread",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                item {
                    NowCard(
                        timer = timer,
                        nowMs = nowMs,
                        featured = featured,
                        canWrite = canWrite,
                        onOpen = { id -> navController.navigate(Screen.TicketDetail.go(id)) },
                        onPauseResume = { t ->
                            if (t.running) ActiveTimer.pause(context)
                            else ActiveTimer.start(context, t.ticketId, t.number, t.subject, t.client)
                        },
                        onStart = { tk ->
                            if (ActiveTimer.start(context, tk.id, tk.number, tk.subject, tk.client)) {
                                navController.navigate(Screen.TicketDetail.go(tk.id))
                            }
                        }
                    )
                }

                item { QuickActionsRow(navController) }

                item {
                    StatChips(
                        overdue = dash.overdue,
                        alerts = activeAlerts,
                        appointments = appointmentsToday.size,
                        allOpen = dash.allOpen,
                        onsite = dash.onsiteOpen ?: 0,
                        onTickets = { navController.navigate(Screen.Tickets.route) },
                        onAlerts = { navController.navigate(Screen.Alerts.route) },
                        onAppointments = { navController.navigate(Screen.Appointments.route) },
                    )
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

                if (listed.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Then", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${dash.myOpen} assigned to you",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            TextButton(onClick = { navController.navigate(Screen.Tickets.route) }) { Text("See all") }
                        }
                    }
                    items(listed.take(QUEUE_ROWS), key = { it.id }) { ticket ->
                        QueueTicketCard(ticket, overdue = isOverdue(ticket, nowMs)) {
                            navController.navigate(Screen.TicketDetail.go(ticket.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowCard(
    timer: TimerSnapshot?,
    nowMs: Long,
    featured: TicketSummary?,
    canWrite: Boolean,
    onOpen: (Int) -> Unit,
    onPauseResume: (TimerSnapshot) -> Unit,
    onStart: (TicketSummary) -> Unit,
) {
    when {
        timer != null -> {
            val on = MaterialTheme.colorScheme.onPrimaryContainer
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (timer.running) "NOW" else "PAUSED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = on.copy(alpha = 0.75f)
                    )
                    Text(
                        formatClock(timer.elapsedSec(nowMs)),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = on
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "#${timer.number} · ${timer.subject}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        color = on
                    )
                    timer.client?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.75f), maxLines = 1)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onPauseResume(timer) }, modifier = Modifier.weight(1f)) {
                            Icon(if (timer.running) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (timer.running) "Pause" else "Resume")
                        }
                        Button(onClick = { onOpen(timer.ticketId) }, modifier = Modifier.weight(1f)) {
                            Text("Open ticket")
                        }
                    }
                }
            }
        }
        featured != null -> {
            val priorityColor = MaterialTheme.statusColors.forPriority(featured.priority)
            val overdue = isOverdue(featured, nowMs)
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(16.dp).height(IntrinsicSize.Min)) {
                    Surface(color = priorityColor, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.width(4.dp).fillMaxHeight()) {}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "UP NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "#${featured.number} · ${featured.subject}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                        val meta = listOfNotNull(
                            featured.client?.takeIf { it.isNotBlank() },
                            featured.dueAt?.takeIf { it.isNotBlank() }?.let { (if (overdue) "Was due " else "Due ") + fmtDate(it) }
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                maxLines = 2
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canWrite) {
                                Button(onClick = { onStart(featured) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Outlined.PlayCircle, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Start")
                                }
                            }
                            OutlinedButton(onClick = { onOpen(featured.id) }, modifier = Modifier.weight(1f)) { Text("Open") }
                        }
                    }
                }
            }
        }
        else -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Queue is clear", style = MaterialTheme.typography.titleSmall)
                        Text("No tickets assigned to you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

/** One compact row of counts. A count of zero says nothing, so it gets no chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatChips(
    overdue: Int, alerts: Int, appointments: Int, allOpen: Int, onsite: Int,
    onTickets: () -> Unit, onAlerts: () -> Unit, onAppointments: () -> Unit,
) {
    val chips = buildList<Triple<String, Boolean, () -> Unit>> {
        if (overdue > 0) add(Triple("$overdue overdue", true, onTickets))
        if (alerts > 0) add(Triple("$alerts alert${if (alerts != 1) "s" else ""}", true, onAlerts))
        if (appointments > 0) add(Triple("$appointments appt${if (appointments != 1) "s" else ""} today", false, onAppointments))
        if (onsite > 0) add(Triple("$onsite on-site", false, onTickets))
        if (allOpen > 0) add(Triple("$allOpen open", false, onTickets))
    }
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (label, urgent, onClick) ->
            AssistChip(
                onClick = onClick,
                label = { Text(label) },
                colors = if (urgent) AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                ) else AssistChipDefaults.assistChipColors()
            )
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
private fun QueueTicketCard(ticket: TicketSummary, overdue: Boolean, onClick: () -> Unit) {
    val priorityColor = MaterialTheme.statusColors.forPriority(ticket.priority)
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = priorityColor,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.width(4.dp).height(44.dp)
            ) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${ticket.number}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (overdue) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Overdue",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    ticket.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                ticket.client?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
