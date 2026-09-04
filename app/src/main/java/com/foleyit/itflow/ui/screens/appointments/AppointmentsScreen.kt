package com.foleyit.itflow.ui.screens.appointments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.foleyit.itflow.ui.util.addAppointmentToCalendar
import com.foleyit.itflow.ui.util.fmtDate
import com.foleyit.itflow.ui.util.userMessage
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.Appointment
import com.foleyit.itflow.data.api.CreateAppointmentRequest
import com.foleyit.itflow.data.api.TicketSummary
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.pressScale
import com.foleyit.itflow.ui.theme.forPriority
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(navController: NavController) {
    var when_ by remember { mutableStateOf("future") }
    var mineOnly by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<Result<List<Appointment>>?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun load() {
        scope.launch {
            state = runCatching { ApiClient.service().getAppointments(when_, if (mineOnly) 1 else 0) }
        }
    }
    LaunchedEffect(when_, mineOnly) { load() }

    if (showAdd) {
        AddAppointmentSheet(
            onDismiss = { showAdd = false },
            onCreate = { ticketId, start, end, onsite, notes ->
                scope.launch {
                    runCatching {
                        ApiClient.service().createAppointment(
                            CreateAppointmentRequest(ticketId, start, end, onsite, notes)
                        )
                    }.onSuccess {
                        load()
                        snackbar.showSnackbar("Appointment added")
                    }.onFailure {
                        snackbar.showSnackbar("Failed to add appointment: ${userMessage(it)}")
                    }
                }
                showAdd = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.pressScale(0.90f),
            ) {
                Icon(Icons.Outlined.Add, "Add Appointment")
            }
        }
    ) { scaffoldPadding ->
    Column(Modifier.fillMaxSize().padding(bottom = scaffoldPadding.calculateBottomPadding())) {
        // When filters
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("past" to "Past", "today" to "Today", "future" to "Upcoming").forEach { (key, label) ->
                FilterChip(selected = when_ == key, onClick = { when_ = key }, label = { Text(label) })
            }
            Spacer(Modifier.weight(1f))
            FilterChip(selected = mineOnly, onClick = { mineOnly = !mineOnly }, label = { Text("Mine") })
        }

        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "Error", onRetry = ::load)
            else -> {
                val appts = state!!.getOrThrow()
                if (appts.isEmpty()) {
                    EmptyScreen(
                        message = when {
                            mineOnly && when_ == "today" -> "No appointments scheduled for you today"
                            when_ == "today" -> "No appointments scheduled today"
                            when_ == "past" -> "No past appointments"
                            else -> "No upcoming appointments"
                        },
                        icon = Icons.Outlined.CalendarMonth
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(appts) { appt ->
                            AppointmentCard(appt) {
                                navController.navigate(Screen.TicketDetail.go(appt.ticketId))
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AppointmentCard(appt: Appointment, onClick: () -> Unit) {
    val priorityColor = MaterialTheme.statusColors.forPriority(appt.priority)
    val ctx = LocalContext.current
    val fullAddress = listOfNotNull(appt.address, appt.city, appt.state).joinToString(", ")
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp)) {
            // Color strip
            Surface(color = priorityColor, shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.width(4.dp).height(72.dp)) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // Date/time
                appt.schedule?.let { schedule ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (appt.onsite) Icons.Outlined.LocationOn else Icons.Outlined.VideoCall,
                            null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            fmtDate(schedule) + (appt.scheduleEnd?.let { " – ${fmtDate(it)}" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(appt.subject, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    appt.client?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Business, null, Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(3.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    appt.assignedTo?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, null, Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(3.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                appt.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Spacer(Modifier.height(4.dp))
                    Text(notes, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                appt.address?.let {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(3.dp))
                        Text(fullAddress, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    appt.address?.let {
                        OutlinedButton(
                            onClick = {
                                ctx.startActivity(Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(fullAddress))
                                ))
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Outlined.Directions, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Drive", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    appt.contactPhone?.let { phone ->
                        OutlinedButton(
                            onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Outlined.Phone, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Call", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("#${appt.number}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                appt.status?.let {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small) {
                        Text(it, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                if (appt.schedule != null) {
                    FilledTonalIconButton(onClick = { addAppointmentToCalendar(ctx, appt) },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, "Add to Calendar", Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private val sqlDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:00", Locale.US)
private val displayDateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
private val displayTimeFormat = SimpleDateFormat("h:mm a", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppointmentSheet(
    onDismiss: () -> Unit,
    onCreate: (ticketId: Int, start: String, end: String?, onsite: Boolean, notes: String) -> Unit
) {
    val ctx = LocalContext.current
    var ticketSearch by remember { mutableStateOf("") }
    var tickets by remember { mutableStateOf<List<TicketSummary>>(emptyList()) }
    var selectedTicket by remember { mutableStateOf<TicketSummary?>(null) }
    var onsite by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var hasEnd by remember { mutableStateOf(false) }

    val startCal = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1); set(Calendar.MINUTE, 0) } }
    var startMillis by remember { mutableLongStateOf(startCal.timeInMillis) }
    var endMillis by remember { mutableLongStateOf(startCal.timeInMillis + 60 * 60 * 1000) }

    LaunchedEffect(ticketSearch) {
        if (selectedTicket != null) return@LaunchedEffect
        delay(300)
        tickets = runCatching {
            ApiClient.service().getTickets(status = "open", search = ticketSearch).data
        }.getOrDefault(emptyList())
    }

    fun pickDateTime(initial: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(ctx, { _, y, m, d ->
            cal.set(y, m, d)
            TimePickerDialog(ctx, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                onPicked(cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()
        ) {
            Text("New Appointment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            if (selectedTicket == null) {
                OutlinedTextField(
                    value = ticketSearch,
                    onValueChange = { ticketSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ticket") },
                    placeholder = { Text("Search open tickets…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge
                )
                Spacer(Modifier.height(6.dp))
                tickets.take(8).forEach { t ->
                    Surface(
                        onClick = { selectedTicket = t },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.subject, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text("#${t.number} · ${t.client ?: ""}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedTicket!!.subject, fontWeight = FontWeight.Medium)
                            Text("#${selectedTicket!!.number} · ${selectedTicket!!.client ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        IconButton(onClick = { selectedTicket = null; ticketSearch = "" }) {
                            Icon(Icons.Outlined.Close, "Change ticket")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Start", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { pickDateTime(startMillis) { picked -> startMillis = picked; if (endMillis <= startMillis) endMillis = startMillis + 60 * 60 * 1000 } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("${displayDateFormat.format(startMillis)} · ${displayTimeFormat.format(startMillis)}")
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasEnd, onCheckedChange = { hasEnd = it })
                Text("Set an end time")
            }
            if (hasEnd) {
                OutlinedButton(
                    onClick = { pickDateTime(endMillis) { picked -> endMillis = picked } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${displayDateFormat.format(endMillis)} · ${displayTimeFormat.format(endMillis)}")
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = onsite, onCheckedChange = { onsite = it })
                Spacer(Modifier.width(8.dp))
                Text(if (onsite) "Onsite visit" else "Remote")
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
            )

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        selectedTicket?.let { t ->
                            onCreate(
                                t.id,
                                sqlDateTimeFormat.format(startMillis),
                                if (hasEnd) sqlDateTimeFormat.format(endMillis) else null,
                                onsite,
                                notes
                            )
                        }
                    },
                    enabled = selectedTicket != null
                ) { Text("Create") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
