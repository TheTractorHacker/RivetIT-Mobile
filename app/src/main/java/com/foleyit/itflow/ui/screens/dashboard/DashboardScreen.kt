package com.foleyit.itflow.ui.screens.dashboard

import androidx.compose.foundation.background
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
        state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "Error", onRetry = ::load)
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

                // Today: appointments + active alerts — the "while on the go" summary
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            label = "Today's Appts",
                            value = appointmentsToday.size.toString(),
                            icon = Icons.Outlined.CalendarMonth,
                            bg = if (appointmentsToday.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer
                                 else MaterialTheme.colorScheme.surfaceVariant,
                            fg = if (appointmentsToday.isNotEmpty()) MaterialTheme.colorScheme.onTertiaryContainer
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.Appointments.route) }
                        )
                        StatCard(
                            label = "Active Alerts",
                            value = activeAlerts.toString(),
                            icon = Icons.Outlined.NotificationImportant,
                            bg = if (activeAlerts > 0) MaterialTheme.colorScheme.errorContainer
                                 else MaterialTheme.colorScheme.surfaceVariant,
                            fg = if (activeAlerts > 0) MaterialTheme.colorScheme.onErrorContainer
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Screen.Alerts.route) }
                        )
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

                // Hero: My Open
                item {
                    MyOpenHeroCard(
                        value = dash.myOpen,
                        onClick = { navController.navigate(Screen.Tickets.route) }
                    )
                }

                // Secondary stats — 2x2 grid
                item {
                    val secondaryStats = buildList {
                        add(
                            SecondaryStat(
                                label = "All Open",
                                value = dash.allOpen.toString(),
                                icon = Icons.Outlined.ConfirmationNumber,
                                bg = MaterialTheme.colorScheme.secondaryContainer,
                                fg = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { navController.navigate(Screen.Tickets.route) }
                            )
                        )
                        add(
                            SecondaryStat(
                                label = "Overdue",
                                value = dash.overdue.toString(),
                                icon = Icons.Outlined.Warning,
                                bg = MaterialTheme.colorScheme.errorContainer,
                                fg = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = null
                            )
                        )
                        dash.dueToday?.let { dueToday ->
                            add(
                                SecondaryStat(
                                    label = "Due Today",
                                    value = dueToday.toString(),
                                    icon = Icons.Outlined.Schedule,
                                    bg = MaterialTheme.colorScheme.tertiaryContainer,
                                    fg = MaterialTheme.colorScheme.onTertiaryContainer,
                                    onClick = { navController.navigate(Screen.Tickets.route) }
                                )
                            )
                        }
                        dash.onsiteOpen?.let { onsiteOpen ->
                            add(
                                SecondaryStat(
                                    label = "On-Site",
                                    value = onsiteOpen.toString(),
                                    icon = Icons.Outlined.LocationOn,
                                    bg = MaterialTheme.colorScheme.surfaceVariant,
                                    fg = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = { navController.navigate(Screen.Tickets.route) }
                                )
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        secondaryStats.chunked(2).forEach { rowStats ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowStats.forEach { stat ->
                                    StatCard(
                                        label = stat.label,
                                        value = stat.value,
                                        icon = stat.icon,
                                        bg = stat.bg,
                                        fg = stat.fg,
                                        modifier = Modifier.weight(1f),
                                        onClick = stat.onClick
                                    )
                                }
                                if (rowStats.size == 1) Spacer(Modifier.weight(1f))
                            }
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
                    items(dash.queue.take(4)) { ticket ->
                        QueueTicketCard(ticket) {
                            navController.navigate(Screen.TicketDetail.go(ticket.id))
                        }
                    }
                }
            }
        }
    }
}

private data class SecondaryStat(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color,
    val onClick: (() -> Unit)?
)

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
private fun MyOpenHeroCard(value: Int, onClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.Person, null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    value.toString(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.W800,
                    color = onColor
                )
                Text(
                    "tickets in My Open",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onColor.copy(alpha = 0.8f)
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = onColor)
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    bg: Color,
    fg: Color,
    modifier: Modifier,
    onClick: (() -> Unit)? = null
) {
    val base = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(color = bg, shape = MaterialTheme.shapes.large, modifier = base) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, null, tint = fg.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = fg
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
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
private fun QueueTicketCard(ticket: TicketSummary, onClick: () -> Unit) {
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
        }
    }
}
