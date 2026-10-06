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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.foleyit.itflow.R
import com.foleyit.itflow.data.model.TicketAttachment
import com.foleyit.itflow.data.api.FeatureParsers
import com.foleyit.itflow.ui.screens.tickets.attachments.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

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

    // Read-only roles still get the ticket; the controls that would be refused are hidden.
    val canWrite = com.foleyit.itflow.data.LocalCapabilities.current.canWrite(com.foleyit.itflow.data.Capabilities.SUPPORT)

    // Timer
    var timerRunning by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var timerStart by remember { mutableLongStateOf(0L) }

    // Sheets
    var showReply by rememberSaveable { mutableStateOf(false) }
    var replySubmitting by remember { mutableStateOf(false) }
    var replyError by remember { mutableStateOf<String?>(null) }
    var showStatusPicker by remember { mutableStateOf(false) }
    var showAddWorksheet by remember { mutableStateOf(false) }
    var showAddOuttake by remember { mutableStateOf(false) }
    var showResolveConfirm by remember { mutableStateOf(false) }
    var charges by remember { mutableStateOf<ChargesResponse?>(null) }
    var worksheets by remember { mutableStateOf<List<WorksheetSummary>>(emptyList()) }
    var outtakes by remember { mutableStateOf<List<OuttakeSummary>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var chargesEnabled by remember { mutableStateOf(false) }

    // Attachments: files listed on the ticket, and the files queued for the reply being written.
    val ctx = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val canViewAttachments = com.foleyit.itflow.data.LocalCapabilities.current.canView(com.foleyit.itflow.data.Capabilities.SUPPORT)
    var attachments by remember { mutableStateOf<List<TicketAttachment>>(emptyList()) }
    var attachmentsFailed by remember { mutableStateOf(false) }
    var openingAttachmentId by remember { mutableStateOf<Int?>(null) }
    var queue by remember { mutableStateOf(AttachmentQueue()) }
    var replyPosted by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    val maxUploadBytes = AttachmentRules.DEFAULT_MAX_BYTES
    // Temp files (downloads, camera shots) are removed when the screen closes; recent ones stay so a viewer that was
    // just launched can still read its file.
    DisposableEffect(Unit) { onDispose { AttachmentTransfer.clearTemp(ctx, olderThanMs = 10 * 60_000L) } }

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
            chargesEnabled = runCatching { ApiClient.profile() }.getOrNull()?.modules?.ticketChargesEnabled ?: false
            worksheets = runCatching { ApiClient.service().getTicketWorksheets(id) }.getOrDefault(emptyList())
            outtakes = runCatching { ApiClient.service().getTicketOuttakes(id) }.getOrDefault(emptyList())
            if (canViewAttachments) {
                runCatching { FeatureParsers.attachments(ApiClient.service().getTicketAttachments(id)) }
                    .onSuccess { attachments = it; attachmentsFailed = false }
                    .onFailure { e ->
                        // An older server without the endpoint (404) just has no section; other failures say so.
                        attachmentsFailed = (e as? retrofit2.HttpException)?.code() != 404
                    }
            }
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
                ApiClient.service().createWorksheet(id, CreateWorksheetRequest(templateId, 0))["id"]
                    ?.takeIf { it > 0 } ?: throw MissingFormIdException()
            },
            onCreated = { wsId ->
                showAddWorksheet = false
                load()
                navController.navigate(Screen.FillWorksheet.go(wsId))
            }
        )
    }

    if (showAddOuttake) {
        OuttakeSheet(
            onDismiss = { showAddOuttake = false },
            onCreate = {
                ApiClient.service().createOuttake(id, CreateWorksheetRequest())["id"]
                    ?.takeIf { it > 0 } ?: throw MissingFormIdException()
            },
            onCreated = { formId ->
                showAddOuttake = false
                load()
                navController.navigate(Screen.OuttakeSign.go(formId))
            }
        )
    }

    val pickers = rememberAttachmentPickers(
        onPicked = { uris ->
            val (q, rejected) = addPicked(ctx, queue, uris, maxUploadBytes)
            queue = q
            if (rejected != null) scope.launch { snackbar.showSnackbar(rejectionMessage(resources, rejected.first, rejected.second, maxUploadBytes)) }
        },
        onMessage = { id -> scope.launch { snackbar.showSnackbar(resources.getString(id)) } },
    )
    if (showAttachSheet) AttachSourceSheet(pickers, onDismiss = { showAttachSheet = false })

    fun closeReply() {
        showReply = false; replyError = null; replyPosted = false; queue = AttachmentQueue()
        AttachmentTransfer.clearTemp(ctx, olderThanMs = 10 * 60_000L)
    }

    // Uploads every queued file in order after the reply is stored, updating progress per file. A failure keeps the
    // file in the queue with a Retry; files already uploaded are never sent twice.
    suspend fun uploadQueued() {
        for (item in queue.toUpload) {
            queue = queue.uploading(item.id, 0)
            try {
                AttachmentTransfer.upload(ctx, id, item) { pct -> queue = queue.uploading(item.id, pct) }
                queue = queue.done(item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                queue = queue.failed(item.id, userMessage(e))
            }
        }
    }

    if (showReply) {
        ReplySheet(
            defaultTimeWorked = if (elapsed > 0) timeWorkedString else "",
            statuses = statuses,
            submitting = replySubmitting,
            errorMessage = replyError,
            canAttach = canWrite,
            attachments = queue,
            replyPosted = replyPosted,
            onAttach = { showAttachSheet = true },
            onRemoveAttachment = { queue = queue.remove(it) },
            onRetryAttachment = { queue = queue.retry(it) },
            onDismiss = {
                if (!replySubmitting) {
                    val failed = replyPosted && !queue.allDone
                    closeReply()
                    if (failed) { load(); scope.launch { snackbar.showSnackbar(resources.getString(R.string.attach_reply_sent_partial)) } }
                }
            },
            onSubmit = { reply, type, timeWorked, onsite, statusId ->
                if (!replySubmitting) {
                    replySubmitting = true
                    replyError = null
                    scope.launch {
                        try {
                            if (!replyPosted) {
                                ApiClient.service().addReply(id, reply, type = type,
                                    timeWorked = timeWorked.ifBlank { null }, onsite = onsite, statusId = statusId)
                                replyPosted = true
                                if (elapsed > 0) elapsed = 0L
                            }
                            uploadQueued()
                            load()
                            if (queue.hasFailures) {
                                snackbar.showSnackbar(resources.getString(R.string.attach_reply_sent_partial))
                            } else {
                                val n = queue.items.size
                                closeReply()
                                if (n > 0) snackbar.showSnackbar(resources.getQuantityString(R.plurals.attach_reply_sent_all, n, n))
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            replyError = "Could not confirm the save. Check ticket history before trying again: ${userMessage(e)}"
                        } finally {
                            replySubmitting = false
                        }
                    }
                }
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
                    if (canWrite) IconButton(onClick = { timerRunning = !timerRunning }) {
                        Icon(
                            if (timerRunning) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                            "Timer",
                            tint = if (timerRunning) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (canWrite && statuses.isNotEmpty()) {
                        IconButton(onClick = { showStatusPicker = true }) {
                            Icon(Icons.Outlined.SwapVert, "Change Status")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state?.isSuccess == true && com.foleyit.itflow.data.LocalCapabilities.current.canWrite(com.foleyit.itflow.data.Capabilities.SUPPORT)) {
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
                        Spacer(Modifier.width(6.dp)); Text("Reply / note")
                    }
                }
            }
            }
        }
    ) { padding ->
        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.let { userMessage(it) } ?: "Something went wrong. Please try again.", onRetry = ::load)
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

                    // Files on the ticket (and on its replies); tap downloads through the authenticated endpoint and opens.
                    if (attachments.isNotEmpty()) {
                        item(key = "attachments") {
                            AttachmentsCard(attachments, openingAttachmentId, onOpen = { att ->
                                openingAttachmentId = att.id
                                scope.launch {
                                    try {
                                        val intent = if (AttachmentRules.openMime(att.name) == null) null else {
                                            val f = AttachmentTransfer.download(ctx, att)
                                            AttachmentTransfer.viewIntent(ctx, f, att.name)
                                        }
                                        if (intent == null) snackbar.showSnackbar(resources.getString(R.string.attach_cannot_open_type))
                                        else try { ctx.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
                                            snackbar.showSnackbar(resources.getString(R.string.attach_no_viewer))
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(resources.getString(R.string.attach_open_failed, userMessage(e)))
                                    } finally {
                                        openingAttachmentId = null
                                    }
                                }
                            })
                        }
                    } else if (attachmentsFailed) {
                        item(key = "attachments_error") {
                            Text(stringResource(R.string.attach_load_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // The internal edition hides charges when this module is disabled.
                    if (chargesEnabled) {
                        item {
                            ChargesCard(charges, onSaveCharge = { name, desc, qty, price ->
                                scope.launch {
                                    runCatching { ApiClient.service().addCharge(id, AddChargeRequest(name, desc, qty, price)) }
                                        .onSuccess { load() }
                                        .onFailure { snackbar.showSnackbar("Failed to add charge: ${userMessage(it)}") }
                                }
                            })
                        }
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
    submitting: Boolean,
    errorMessage: String?,
    canAttach: Boolean,
    attachments: AttachmentQueue,
    replyPosted: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onRetryAttachment: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (reply: String, type: String, timeWorked: String, onsite: Boolean, statusId: Int?) -> Unit
) {
    var reply by rememberSaveable { mutableStateOf("") }
    var replyType by rememberSaveable { mutableStateOf("note") }
    val (initialH, initialM) = remember(defaultTimeWorked) { parseHoursMinutes(defaultTimeWorked) }
    var hours by rememberSaveable { mutableIntStateOf(initialH) }
    var minutes by rememberSaveable { mutableIntStateOf(initialM) }
    var onsite by rememberSaveable { mutableStateOf(false) }
    var selectedStatusId by rememberSaveable { mutableStateOf<Int?>(null) }

    // Mirrors the web app's reply-form "Submit & set status to…" dropdown, which excludes
    // "New" (not a status a reply returns a ticket to) and "Closed" (only ever reached via
    // "Resolved", which the backend remaps automatically).
    val statusOptions = remember(statuses) {
        statuses.filter { it.name != "New" && it.name != "Closed" }
    }

    ModalBottomSheet(onDismissRequest = { if (!submitting) onDismiss() }) {
        Column(modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()) {
            Text("Reply to ticket", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Visibility", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = replyType == "note", onClick = { replyType = "note" },
                    label = { Text("Internal note") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = replyType == "reply", onClick = { replyType = "reply" },
                    label = { Text("Public reply") },
                    leadingIcon = { Icon(Icons.Outlined.Public, null, Modifier.size(16.dp)) }
                )
            }
            Text(if (replyType == "note") "Only technicians can see this note."
                 else "Department contacts can see this reply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                enabled = !replyPosted,
                label = { Text(if (replyType == "note") "Internal note" else "Public reply") },
                minLines = 4, maxLines = 8
            )
            if (canAttach) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAttach, enabled = !submitting && attachments.items.size < AttachmentRules.MAX_FILES) {
                    Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.attach_button))
                }
                AttachmentChips(attachments, enabled = !submitting && !replyPosted, onRemove = onRemoveAttachment, onRetry = onRetryAttachment)
            }
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
            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !submitting) {
                    Text(if (replyPosted) stringResource(R.string.action_close) else "Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (reply.isNotBlank() && !submitting) {
                            val timeWorked = if (hours > 0 || minutes > 0)
                                "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:00"
                            else ""
                            onSubmit(reply, replyType, timeWorked, onsite, selectedStatusId)
                        }
                    },
                    enabled = reply.isNotBlank() && !submitting
                ) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(
                        when {
                            replyPosted -> stringResource(R.string.attach_retry_uploads)
                            replyType == "note" -> "Add internal note"
                            else -> "Send public reply"
                        }
                    )
                }
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
    val isPublicReply = reply.type.equals("reply", ignoreCase = true) || reply.type.equals("agent", ignoreCase = true)
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
                if (isPublicReply) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall) {
                        Text("Public reply", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
                if (com.foleyit.itflow.data.LocalCapabilities.current.canWrite(com.foleyit.itflow.data.Capabilities.SUPPORT)) IconButton(onClick = { showDeleteDialog = true },
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
                    onSaveCharge?.takeIf { com.foleyit.itflow.data.LocalCapabilities.current.canWrite(com.foleyit.itflow.data.Capabilities.SUPPORT) }?.let {
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
                onAddWorksheet?.takeIf { com.foleyit.itflow.data.LocalCapabilities.current.canWrite(com.foleyit.itflow.data.Capabilities.SUPPORT) }?.let { action ->
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
                    Text("Contact signs on pickup", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
                onAddOuttake?.takeIf { com.foleyit.itflow.data.LocalCapabilities.current.canWrite(com.foleyit.itflow.data.Capabilities.SUPPORT) }?.let { action ->
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
    val total = remember(qty, price) { qty * (price.toDoubleOrNull() ?: 0.0) }
    val currency = NumberFormat.getCurrencyInstance(Locale.US)

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Add Charge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

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
private fun OuttakeSheet(onDismiss: () -> Unit, onCreate: suspend () -> Int, onCreated: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = { if (!submitting) onDismiss() }) {
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
                "Creates a sign-off form so the contact can sign when picking up their device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            submitError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                if (submitting) return@Button
                submitting = true
                submitError = null
                scope.launch {
                    try {
                        val formId = onCreate()
                        onCreated(formId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        submitError = createFormError("outtake form", e)
                    } finally {
                        submitting = false
                    }
                }
            }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Draw, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (submitting) "Creating…" else "Create & Sign")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectTemplateSheet(
    title: String,
    onDismiss: () -> Unit,
    onCreate: suspend (templateId: Int) -> Int,
    onCreated: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<Result<List<WorksheetTemplate>>?>(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var submittingTemplateId by remember { mutableStateOf<Int?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loadAttempt) {
        templates = runCatching { ApiClient.service().getWorksheetTemplates() }
    }

    ModalBottomSheet(onDismissRequest = { if (!submitting) onDismiss() }) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            when {
                templates == null -> CircularProgressIndicator(Modifier.size(24.dp))
                templates!!.isFailure -> {
                    Text("Could not load worksheet templates: ${userMessage(templates!!.exceptionOrNull()!!)}",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { templates = null; loadAttempt++ }) { Text("Retry") }
                }
                templates!!.getOrThrow().isEmpty() -> Text("No worksheet templates available.",
                    color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                else -> {
                    Text("Select Template", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    templates!!.getOrThrow().forEach { t ->
                        Surface(
                            onClick = {
                                if (submitting) return@Surface
                                submitting = true
                                submittingTemplateId = t.id
                                submitError = null
                                scope.launch {
                                    try {
                                        val worksheetId = onCreate(t.id)
                                        onCreated(worksheetId)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        submitError = createFormError("worksheet", e)
                                    } finally {
                                        submitting = false
                                        submittingTemplateId = null
                                    }
                                }
                            },
                            enabled = !submitting,
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
                                if (submittingTemplateId == t.id) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
            submitError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private class MissingFormIdException : IllegalStateException()

private fun createFormError(name: String, error: Exception): String = if (error is IOException || error is MissingFormIdException) {
    "Could not confirm the $name. Check the ticket before trying again."
} else {
    "Could not create $name: ${userMessage(error)}"
}


/** Same text as the picker rejections, for use outside composition (snackbars). */
private fun rejectionMessage(resources: android.content.res.Resources, name: String, r: Rejection, maxBytes: Long): String = when (r) {
    is Rejection.Extension -> if (r.ext.isEmpty()) resources.getString(R.string.attach_err_extension_none) else resources.getString(R.string.attach_err_extension, ".${r.ext}")
    is Rejection.TooLarge -> resources.getString(R.string.attach_err_too_large, name, AttachmentRules.formatSize(maxBytes))
    Rejection.Empty -> resources.getString(R.string.attach_err_empty, name)
    Rejection.TooMany -> resources.getString(R.string.attach_err_too_many, AttachmentRules.MAX_FILES)
    Rejection.Duplicate -> resources.getString(R.string.attach_err_duplicate, name)
}
