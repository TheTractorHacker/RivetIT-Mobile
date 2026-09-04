package com.foleyit.itflow.ui.screens.tickets

import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.*
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.util.fmtDate
import java.text.NumberFormat
import java.util.Locale
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.PriorityBadge
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(id: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<TicketDetail>?>(null) }
    var statuses by remember { mutableStateOf<List<TicketStatus>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Timer
    var timerRunning by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var timerStart by remember { mutableLongStateOf(0L) }

    // Sheets
    var showReply by remember { mutableStateOf(false) }
    var showStatusPicker by remember { mutableStateOf(false) }
    var showAddWorksheet by remember { mutableStateOf(false) }
    var showAddOuttake by remember { mutableStateOf(false) }
    var showResolveConfirm by remember { mutableStateOf(false) }
    var charges by remember { mutableStateOf<ChargesResponse?>(null) }
    var worksheets by remember { mutableStateOf<List<WorksheetSummary>>(emptyList()) }
    var outtakes by remember { mutableStateOf<List<OuttakeSummary>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            timerStart = System.currentTimeMillis() - elapsed * 1000
            while (timerRunning) {
                elapsed = (System.currentTimeMillis() - timerStart) / 1000
                delay(1000)
            }
        }
    }

    val timerDisplay = remember(elapsed) {
        val h = elapsed / 3600; val m = (elapsed % 3600) / 60; val s = elapsed % 60
        "$h:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
    }
    val timeWorkedString = remember(elapsed) {
        "${(elapsed/3600).toString().padStart(2,'0')}:${((elapsed%3600)/60).toString().padStart(2,'0')}:00"
    }

    fun load() {
        scope.launch {
            state = runCatching { ApiClient.service().getTicket(id) }
            charges = runCatching { ApiClient.service().getTicketCharges(id) }.getOrNull()
            worksheets = runCatching { ApiClient.service().getTicketWorksheets(id) }.getOrDefault(emptyList())
            outtakes = runCatching { ApiClient.service().getTicketOuttakes(id) }.getOrDefault(emptyList())
            if (statuses.isEmpty()) {
                statuses = runCatching { ApiClient.service().getTicketStatuses() }.getOrDefault(emptyList())
            }
        }
    }
    LaunchedEffect(refresh) { load() }

    // Reload whenever this screen comes back to the foreground — e.g. returning from signing a
    // worksheet/outtake or the fill-worksheet screen, none of which otherwise trigger a refresh.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showAddWorksheet) {
        SelectTemplateSheet(
            title = "New Worksheet",
            onDismiss = { showAddWorksheet = false },
            onCreate = { templateId ->
                scope.launch {
                    runCatching {
                        ApiClient.service().createWorksheet(id, CreateWorksheetRequest(templateId, 0))
                    }.onSuccess { ws ->
                        load()
                        ws["id"]?.let { wsId -> navController.navigate(Screen.FillWorksheet.go(wsId)) }
                    }.onFailure {
                        snackbar.showSnackbar("Failed to create worksheet: ${userMessage(it)}")
                    }
                }
                showAddWorksheet = false
            }
        )
    }

    if (showAddOuttake) {
        OuttakeSheet(
            onDismiss = { showAddOuttake = false },
            onCreate = {
                scope.launch {
                    runCatching {
                        ApiClient.service().createOuttake(id, CreateWorksheetRequest())
                    }.onSuccess { form ->
                        load()
                        form["id"]?.let { formId -> navController.navigate(Screen.OuttakeSign.go(formId)) }
                    }.onFailure {
                        snackbar.showSnackbar("Failed to create outtake form: ${userMessage(it)}")
                    }
                }
                showAddOuttake = false
            }
        )
    }

    if (showReply) {
        ReplySheet(
            defaultTimeWorked = if (elapsed > 0) timeWorkedString else "",
            statuses = statuses,
            onDismiss = { showReply = false },
            onSubmit = { reply, type, timeWorked, onsite, statusId ->
                scope.launch {
                    runCatching {
                        ApiClient.service().addReply(id, reply, type = type,
                            timeWorked = timeWorked.ifBlank { null }, onsite = onsite, statusId = statusId)
                    }.onSuccess {
                        load()
                        if (elapsed > 0) elapsed = 0L
                    }.onFailure {
                        snackbar.showSnackbar("Failed to save note: ${userMessage(it)}")
                    }
                }
                showReply = false
            }
        )
    }

    // "Closed" is the one status name the backend treats specially (sets resolved/closed
    // timestamps); matched by exact name here to mirror that server-side check.
    val closedStatusId = statuses.firstOrNull { it.name == "Closed" }?.id
    val isResolved = state?.getOrNull()?.status == "Closed"

    if (showResolveConfirm) {
        AlertDialog(
            onDismissRequest = { showResolveConfirm = false },
            title = { Text("Resolve Ticket?") },
            text = { Text("This will mark the ticket as Closed.") },
            confirmButton = {
                TextButton(onClick = {
                    showResolveConfirm = false
                    closedStatusId?.let { csid ->
                        scope.launch {
                            runCatching { ApiClient.service().updateTicketStatus(id, mapOf("status_id" to csid)) }
                                .onSuccess { load(); snackbar.showSnackbar("Ticket resolved") }
                                .onFailure { snackbar.showSnackbar("Failed to resolve ticket: ${userMessage(it)}") }
                        }
                    }
                }) { Text("Resolve") }
            },
            dismissButton = {
                TextButton(onClick = { showResolveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showStatusPicker && statuses.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showStatusPicker = false }) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("Change Status", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                statuses.forEach { s ->
                    val isCurrentStatus = state?.getOrNull()?.status == s.name
                    val statusColor = parseStatusColor(s.color)
                    Surface(
                        onClick = {
                            showStatusPicker = false
                            scope.launch {
                                try {
                                    ApiClient.service().updateTicketStatus(id, mapOf("status_id" to s.id))
                                    load()
                                } catch (e: Exception) {
                                    snackbar.showSnackbar("Failed to update status: ${userMessage(e)}")
                                }
                            }
                        },
                        color = if (isCurrentStatus) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(statusIcon(s.name), null, Modifier.size(20.dp), tint = statusColor)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(s.name, style = MaterialTheme.typography.bodyMedium)
                            if (isCurrentStatus) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Outlined.Check, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { state?.getOrNull()?.let { Text("#${it.number}") } ?: Text("Ticket") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.TicketChat.go(id)) }) {
                        Icon(Icons.Outlined.Forum, "Live Chat")
                    }
                    IconButton(onClick = { timerRunning = !timerRunning }) {
                        Icon(
                            if (timerRunning) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                            "Timer",
                            tint = if (timerRunning) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (statuses.isNotEmpty()) {
                        IconButton(onClick = { showStatusPicker = true }) {
                            Icon(Icons.Outlined.SwapVert, "Change Status")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isResolved && closedStatusId != null) {
                        OutlinedButton(
                            onClick = { showResolveConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Resolve")
                        }
                    }
                    Button(
                        onClick = { showReply = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.StickyNote2, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Add Note")
                    }
                }
            }
        }
    ) { padding ->
        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "Error", onRetry = ::load)
            else -> {
                val ticket = state!!.getOrThrow()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Timer bar
                    if (elapsed > 0 || timerRunning) {
                        item {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Timer, null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.width(8.dp))
                                    Text(timerDisplay, fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { showReply = true }) {
                                        Text("Log Time")
                                    }
                                }
                            }
                        }
                    }

                    // Header card
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    PriorityBadge(ticket.priority)
                                    ticket.status?.let { status ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.small,
                                            onClick = { showStatusPicker = true }
                                        ) {
                                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Text(status, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Outlined.ArrowDropDown, null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (ticket.billable) {
                                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = MaterialTheme.shapes.small) {
                                            Text("Billable", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(ticket.subject, style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(12.dp))
                                ticket.client?.let { InfoRow(Icons.Outlined.Business, it) }
                                ticket.assignedTo?.let { InfoRow(Icons.Outlined.PersonOutline, "Assigned: $it") }
                                ticket.contactName?.let { InfoRow(Icons.Outlined.ContactPage, it) }
                                ticket.contactPhone?.let { InfoRow(Icons.Outlined.Phone, it) }
                                ticket.dueAt?.let { InfoRow(Icons.Outlined.Schedule, "Due: ${fmtDate(it)}") }
                                ticket.createdAt?.let { InfoRow(Icons.Outlined.CalendarToday, "Opened: ${fmtDate(it)}") }
                            }
                        }
                    }

                    // Description
                    if (!ticket.details.isNullOrBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Description", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text(stripHtml(ticket.details))
                                }
                            }
                        }
                    }

                    // Charges
                    item {
                        ChargesCard(charges, onSaveCharge = { name, desc, qty, price ->
                            scope.launch {
                                runCatching { ApiClient.service().addCharge(id, AddChargeRequest(name, desc, qty, price)) }
                                    .onSuccess { load() }
                                    .onFailure { snackbar.showSnackbar("Failed to add charge: ${userMessage(it)}") }
                            }
                        })
                    }

                    // Worksheets + Outtake Forms (separate sections)
                    item {
                        WorksheetsCard(
                            worksheets = worksheets,
                            outtakes = outtakes,
                            navController = navController,
                            onAddWorksheet = { showAddWorksheet = true },
                            onAddOuttake = { showAddOuttake = true },
                            onDeleteWorksheet = { wsId ->
                                scope.launch {
                                    runCatching { ApiClient.service().deleteWorksheet(wsId) }
                                        .onSuccess { load() }
                                        .onFailure { snackbar.showSnackbar("Failed to delete worksheet: ${userMessage(it)}") }
                                }
                            },
                            onToggleWorksheetComplete = { wsId, completed ->
                                scope.launch {
                                    runCatching { ApiClient.service().completeWorksheet(wsId, CompleteWorksheetRequest(completed)) }
                                        .onSuccess { load() }
                                        .onFailure { snackbar.showSnackbar("Failed to update worksheet: ${userMessage(it)}") }
                                }
                            },
                            onDeleteOuttake = { otId ->
                                scope.launch {
                                    runCatching { ApiClient.service().deleteOuttake(otId) }
                                        .onSuccess { load() }
                                        .onFailure { snackbar.showSnackbar("Failed to delete outtake form: ${userMessage(it)}") }
                                }
                            }
                        )
                    }

                    // Reply count header
                    if (ticket.replies.isNotEmpty()) {
                        item {
                            Text("${ticket.replies.size} ${if (ticket.replies.size == 1) "reply" else "replies"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    // Replies
                    items(ticket.replies) { reply ->
                        ReplyCard(
                            reply, ticketId = id,
                            onDeleted = { refresh++ },
                            onDeleteFailed = { msg ->
                                scope.launch { snackbar.showSnackbar("Failed to delete: $msg") }
                            }
                        )
                    }
                }
            }
        }
    }
}

fun parseStatusColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
} catch (_: Exception) { Color.Gray }

// Statuses are admin-configurable on the server, so names beyond this common set can't be
// predicted — anything unrecognized falls back to a plain dot-in-circle treatment.
private fun statusIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (name.lowercase()) {
        "open", "new" -> Icons.Outlined.RadioButtonUnchecked
        "in progress" -> Icons.Outlined.Sync
        "waiting", "pending" -> Icons.Outlined.Schedule
        "closed", "resolved" -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Circle
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplySheet(
    defaultTimeWorked: String,
    statuses: List<TicketStatus>,
    onDismiss: () -> Unit,
    onSubmit: (reply: String, type: String, timeWorked: String, onsite: Boolean, statusId: Int?) -> Unit
) {
    var reply by remember { mutableStateOf("") }
    val (initialH, initialM) = remember(defaultTimeWorked) { parseHoursMinutes(defaultTimeWorked) }
    var hours by remember { mutableIntStateOf(initialH) }
    var minutes by remember { mutableIntStateOf(initialM) }
    var onsite by remember { mutableStateOf(false) }
    var selectedStatusId by remember { mutableStateOf<Int?>(null) }

    // Mirrors the web app's reply-form "Submit & set status to…" dropdown, which excludes
    // "New" (not a status a reply returns a ticket to) and "Closed" (only ever reached via
    // "Resolved", which the backend remaps automatically).
    val statusOptions = remember(statuses) {
        statuses.filter { it.name != "New" && it.name != "Closed" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()) {
            Text("Add Note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            // Remote / On-Site toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !onsite,
                    onClick = { onsite = false },
                    label = { Text("Remote") },
                    leadingIcon = { Icon(Icons.Outlined.Wifi, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = onsite,
                    onClick = { onsite = true },
                    label = { Text("On-Site") },
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp)) }
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = reply, onValueChange = { reply = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 4, maxLines = 8
            )
            Spacer(Modifier.height(16.dp))
            Text("Time worked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TimeStepper(label = "hr", value = hours, step = 1, range = 0..23, onChange = { hours = it })
                TimeStepper(label = "min", value = minutes, step = 5, range = 0..59, onChange = { minutes = it })
            }
            if (statusOptions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Set status (optional)", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedStatusId == null,
                        onClick = { selectedStatusId = null },
                        label = { Text("No change") }
                    )
                    statusOptions.forEach { s ->
                        FilterChip(
                            selected = selectedStatusId == s.id,
                            onClick = { selectedStatusId = s.id },
                            label = { Text(s.name) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (reply.isNotBlank()) {
                            val timeWorked = if (hours > 0 || minutes > 0)
                                "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:00"
                            else ""
                            onSubmit(reply, "note", timeWorked, onsite, selectedStatusId)
                        }
                    },
                    enabled = reply.isNotBlank()
                ) { Text("Add Note") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun parseHoursMinutes(hhmmss: String): Pair<Int, Int> {
    val parts = hhmmss.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h to m
}

@Composable
private fun TimeStepper(label: String, value: Int, step: Int, range: IntRange, onChange: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { onChange((value - step).coerceIn(range)) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Remove, "Decrease $label", Modifier.size(18.dp))
            }
            Text(
                "$value $label",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 48.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { onChange((value + step).coerceIn(range)) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Add, "Increase $label", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(modifier = Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReplyCard(reply: TicketReply, ticketId: Int, onDeleted: () -> Unit, onDeleteFailed: (String) -> Unit = {}) {
    // Backend type is 'Internal' going forward (matches the web app's own customer-hiding
    // filter); 'note' is kept for reading older entries created before that fix.
    val isNote = reply.type.equals("note", ignoreCase = true) || reply.type.equals("Internal", ignoreCase = true)
    val isFromCustomer = reply.type.equals("Client", ignoreCase = true)
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${if (isNote) "Note" else "Reply"}?") },
            text = { Text("This will permanently remove this ${if (isNote) "note" else "reply"}. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        runCatching { ApiClient.service().deleteReply(ticketId, reply.id) }
                            .onSuccess { onDeleted() }
                            .onFailure { onDeleteFailed(userMessage(it)) }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Surface(
        color = if (isNote) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isNote) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(reply.by?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(reply.by ?: "", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(fmtDate(reply.createdAt), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
                if (isNote) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraSmall) {
                        Text("Note", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                if (isFromCustomer) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall) {
                        Text("Customer", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                reply.onsite?.let { ons ->
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        color = if (ons) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (ons) Icons.Outlined.LocationOn else Icons.Outlined.Wifi,
                                null, Modifier.size(11.dp),
                                tint = if (ons) MaterialTheme.colorScheme.onSecondaryContainer
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                if (ons) "On-Site" else "Remote",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ons) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                reply.timeWorked?.let { tw ->
                    Spacer(Modifier.width(4.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraSmall) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Timer, null, Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(2.dp))
                            Text(tw, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Text(stripHtml(reply.body), modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(28.dp).padding(start = 4.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun ChargesCard(cr: ChargesResponse?, onSaveCharge: ((name: String, desc: String, qty: Double, price: Double) -> Unit)? = null) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Charges", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cr != null && cr.charges.isNotEmpty()) {
                        Text(currency.format(cr.total), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                    }
                    onSaveCharge?.let {
                        FilledTonalIconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
                                if (expanded) "Close" else "Add Charge",
                                Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            if (cr == null || cr.charges.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("No charges on this ticket.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            } else {
                Spacer(Modifier.height(12.dp))
                cr.charges.forEachIndexed { i, charge ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(charge.name, fontWeight = FontWeight.Medium)
                            if (!charge.description.isNullOrBlank()) {
                                Text(charge.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${charge.quantity} × ${currency.format(charge.unitPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(currency.format(charge.total), fontWeight = FontWeight.Medium)
                            if (charge.invoiced) {
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall) {
                                    Text("Invoiced", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                    if (i < cr.charges.size - 1) HorizontalDivider()
                }
            }
            if (onSaveCharge != null) {
                AnimatedVisibility(visible = expanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        AddChargeForm(
                            onCancel = { expanded = false },
                            onSave = { name, desc, qty, price ->
                                onSaveCharge(name, desc, qty, price)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorksheetsCard(
    worksheets: List<WorksheetSummary>,
    outtakes: List<OuttakeSummary>,
    navController: NavController,
    onAddWorksheet: (() -> Unit)? = null,
    onAddOuttake: (() -> Unit)? = null,
    onDeleteWorksheet: ((Int) -> Unit)? = null,
    onDeleteOuttake: ((Int) -> Unit)? = null,
    onToggleWorksheetComplete: ((Int, Boolean) -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            // ── Worksheets ───────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Worksheets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                onAddWorksheet?.let { action ->
                    FilledTonalIconButton(onClick = action, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Add, "Add Worksheet", Modifier.size(16.dp))
                    }
                }
            }
            if (worksheets.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("No worksheets.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            } else {
                Spacer(Modifier.height(8.dp))
                WorksheetItems(worksheets, navController, onDeleteWorksheet, onToggleWorksheetComplete)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ── Outtake Forms ────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Outtake Forms", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Client signs on pickup", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
                onAddOuttake?.let { action ->
                    FilledTonalIconButton(onClick = action, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Add, "Add Outtake Form", Modifier.size(16.dp))
                    }
                }
            }
            if (outtakes.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("No outtake forms.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            } else {
                Spacer(Modifier.height(8.dp))
                OuttakeItems(outtakes, navController, onDeleteOuttake)
            }
        }
    }
}

@Composable
private fun WorksheetItems(
    entries: List<WorksheetSummary>,
    navController: NavController,
    onDelete: ((Int) -> Unit)? = null,
    onToggleComplete: ((Int, Boolean) -> Unit)? = null
) {
    var deleteTarget by remember { mutableStateOf<WorksheetSummary?>(null) }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Worksheet?") },
            text = { Text("\"${deleteTarget!!.templateName ?: "Worksheet"}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete?.invoke(deleteTarget!!.id)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    entries.forEachIndexed { i, ws ->
        val completed = ws.completedAt != null
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.FillWorksheet.go(ws.id)) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = completed,
                onCheckedChange = { onToggleComplete?.invoke(ws.id, it) },
                enabled = !ws.signed
            )
            Column(Modifier.weight(1f)) {
                Text(ws.templateName ?: "Worksheet", fontWeight = FontWeight.Medium)
                Text(
                    when {
                        ws.signed -> "Signed by ${ws.signedName}"
                        completed -> "Completed · by ${ws.createdBy ?: ""}"
                        else -> "Not started · by ${ws.createdBy ?: ""}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (completed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = { deleteTarget = ws }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.DeleteOutline, "Delete",
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
        if (i < entries.size - 1) HorizontalDivider()
    }
}

@Composable
private fun OuttakeItems(
    entries: List<OuttakeSummary>,
    navController: NavController,
    onDelete: ((Int) -> Unit)? = null
) {
    var deleteTarget by remember { mutableStateOf<OuttakeSummary?>(null) }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Outtake Form?") },
            text = { Text("This outtake form will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete?.invoke(deleteTarget!!.id)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    entries.forEachIndexed { i, ot ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ot.signed) Icons.Outlined.CheckCircle else Icons.Outlined.Draw,
                null, modifier = Modifier.size(20.dp),
                tint = if (ot.signed) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Outtake Form", fontWeight = FontWeight.Medium)
                Text(
                    if (ot.signed) "Signed by ${ot.signedName}"
                    else "Not signed · by ${ot.createdBy ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ot.signed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                )
            }
            if (!ot.signed) {
                FilledTonalButton(
                    onClick = { navController.navigate(Screen.OuttakeSign.go(ot.id)) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.Draw, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sign", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { deleteTarget = ot }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.DeleteOutline, "Delete",
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
        if (i < entries.size - 1) HorizontalDivider()
    }
}

// Inline "Add Charge" panel rendered directly inside ChargesCard (an expanding panel, not a
// separate sheet route) — same product-catalog quick-pick, fields, and live total as before.
@Composable
private fun AddChargeForm(
    onCancel: () -> Unit,
    onSave: (name: String, desc: String, qty: Double, price: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var qty by remember { mutableIntStateOf(1) }
    var price by remember { mutableStateOf("") }
    var productSearch by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    val total = remember(qty, price) { qty * (price.toDoubleOrNull() ?: 0.0) }
    val currency = NumberFormat.getCurrencyInstance(Locale.US)

    LaunchedEffect(Unit) {
        products = runCatching { ApiClient.service().getProducts() }.getOrDefault(emptyList())
    }

    // Show the first several products as quick picks even before typing; narrow to a text match once searching.
    val filteredProducts = remember(productSearch, products) {
        if (productSearch.isBlank()) products.take(8)
        else products.filter {
            it.name.contains(productSearch, ignoreCase = true) ||
            it.description?.contains(productSearch, ignoreCase = true) == true
        }.take(8)
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Add Charge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        // Product catalog search
        if (products.isNotEmpty()) {
            Text("From Catalog", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = productSearch,
                onValueChange = { productSearch = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search products & services…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (productSearch.isNotEmpty()) {
                        IconButton(onClick = { productSearch = "" }) {
                            Icon(Icons.Outlined.Clear, "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )
            if (filteredProducts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                filteredProducts.forEach { product ->
                    Surface(
                        onClick = {
                            name = product.name
                            desc = product.description ?: ""
                            price = product.price.toString()
                            qty = 1
                            productSearch = ""
                        },
                        shape = MaterialTheme.shapes.medium,
                        // Contrasts against this form's own surfaceVariant panel background.
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium)
                                product.type?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Text(currency.format(product.price), fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
        }

        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("Item Name *") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = desc, onValueChange = { desc = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Quantity", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                QtyStepper(value = qty, onChange = { qty = it })
            }
            OutlinedTextField(
                value = price, onValueChange = { price = it }, label = { Text("Unit Price") },
                leadingIcon = { Text("$") }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("New line total", style = MaterialTheme.typography.labelLarge)
                Text(currency.format(total), fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (name.isNotBlank()) onSave(name, desc, qty.toDouble(), price.toDoubleOrNull() ?: 0.0)
                },
                enabled = name.isNotBlank()
            ) { Text("Add Charge") }
        }
    }
}

@Composable
private fun QtyStepper(value: Int, onChange: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Remove, "Decrease quantity", Modifier.size(18.dp))
            }
            Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { onChange(value + 1) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Add, "Increase quantity", Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OuttakeSheet(onDismiss: () -> Unit, onCreate: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Draw, null, Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Outtake Form", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Creates a sign-off form so the client can sign when picking up their device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Draw, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create & Sign")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectTemplateSheet(
    title: String,
    onDismiss: () -> Unit,
    onCreate: (templateId: Int) -> Unit
) {
    var templates by remember { mutableStateOf<List<WorksheetTemplate>>(emptyList()) }

    LaunchedEffect(Unit) {
        templates = runCatching { ApiClient.service().getWorksheetTemplates() }.getOrDefault(emptyList())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (templates.isEmpty()) {
                Text("No worksheet templates available.", color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Select Template", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                templates.forEach { t ->
                    Surface(
                        onClick = { onCreate(t.id) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t.name, fontWeight = FontWeight.Medium)
                            t.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
