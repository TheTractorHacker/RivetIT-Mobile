package com.foleyit.itflow.ui.screens.tickets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ClientsResponse
import com.foleyit.itflow.data.api.CreateTicketRequest
import com.foleyit.itflow.data.api.TicketCategory
import com.foleyit.itflow.ui.components.SectionLabel
import com.foleyit.itflow.ui.theme.forPriority
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(navController: NavController) {
    var subject by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("low") }
    var selectedClientId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedClientName by rememberSaveable { mutableStateOf("") }
    var clients by remember { mutableStateOf<Result<ClientsResponse>?>(null) }
    var clientSearch by remember { mutableStateOf("") }
    var clientLoadAttempt by remember { mutableIntStateOf(0) }
    var showClientPicker by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<Result<List<TicketCategory>>?>(null) }
    var categoryLoadAttempt by remember { mutableIntStateOf(0) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val hasDraft = subject.isNotBlank() || details.isNotBlank() || priority != "low" ||
        selectedClientId != null || selectedCategoryId != null

    fun goBack() {
        if (saving) return
        if (hasDraft) showDiscardConfirm = true else navController.popBackStack()
    }
    BackHandler(enabled = hasDraft || saving) { if (!saving) showDiscardConfirm = true }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard ticket draft?") },
            text = { Text("Your ticket details have not been saved.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; navController.popBackStack() }) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") } }
        )
    }

    LaunchedEffect(showClientPicker, clientSearch, clientLoadAttempt) {
        if (!showClientPicker) return@LaunchedEffect
        // The API retains client_id; the app calls these records departments.
        clients = null
        if (clientSearch.isNotBlank()) delay(250)
        clients = try {
            Result.success(ApiClient.service().getClients(search = clientSearch.trim(), page = 1))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    LaunchedEffect(categoryLoadAttempt) {
        categories = try {
            Result.success(ApiClient.service().getTicketCategories())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun submit() {
        if (saving) return
        if (subject.isBlank()) { error = "Subject required"; return }
        saving = true; error = null
        scope.launch {
            try {
                ApiClient.service().createTicket(
                    CreateTicketRequest(
                        subject = subject.trim(),
                        details = details.trim(),
                        clientId = selectedClientId,
                        priority = priority,
                        categoryId = selectedCategoryId
                    )
                )
                navController.popBackStack()
            } catch (e: Exception) {
                error = "Could not confirm ticket creation. Check Tickets before trying again: ${userMessage(e)}"
            } finally { saving = false }
        }
    }

    if (showClientPicker) {
        ModalBottomSheet(
            onDismissRequest = { showClientPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.heightIn(max = 600.dp).padding(16.dp).navigationBarsPadding()) {
                Text("Select Department", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientSearch, onValueChange = { clientSearch = it },
                    label = { Text("Search departments") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Surface(onClick = { selectedClientId = null; selectedClientName = ""; showClientPicker = false },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("— No Department —", Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
                when {
                    clients == null -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                    clients!!.isFailure -> {
                        Text("Could not load departments: ${userMessage(clients!!.exceptionOrNull()!!)}",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { clients = null; clientLoadAttempt++ }) { Text("Retry") }
                    }
                    else -> {
                        val response = clients!!.getOrThrow()
                        if (response.data.isEmpty()) Text("No departments found", Modifier.padding(16.dp))
                        LazyColumn(Modifier.heightIn(max = 480.dp)) {
                            items(response.data, key = { it.id }) { c ->
                                Surface(onClick = {
                                    selectedClientId = c.id; selectedClientName = c.name; showClientPicker = false
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text(c.name, Modifier.padding(16.dp))
                                }
                                HorizontalDivider()
                            }
                        }
                        if (response.total > response.data.size) {
                            Text("Search by name to find more departments.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())
                .padding(16.dp).navigationBarsPadding()) {
                Text("Select Category", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Surface(onClick = { selectedCategoryId = null; showCategoryPicker = false },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("— No Category —", Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
                when {
                    categories == null -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                    categories!!.isFailure -> {
                        Text("Could not load categories: ${userMessage(categories!!.exceptionOrNull()!!)}",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { categories = null; categoryLoadAttempt++ }) { Text("Retry") }
                    }
                    categories!!.getOrThrow().isEmpty() -> Text("No categories available", Modifier.padding(16.dp))
                    else -> categories!!.getOrThrow().forEach { cat ->
                        Surface(onClick = {
                            selectedCategoryId = cat.id; showCategoryPicker = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(cat.name, Modifier.padding(16.dp))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = ::goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Box(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                    Button(
                        onClick = ::submit,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Ticket", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro row
            Row(verticalAlignment = Alignment.CenterVertically) {
                val gradient = Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(gradient, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.ConfirmationNumber, null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Log a new ticket",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Capture the details so your team can jump on it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Details card
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Details")
                    OutlinedTextField(
                        value = subject, onValueChange = { subject = it },
                        label = { Text("Subject *") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = details, onValueChange = { details = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8
                    )
                }
            }

            // Department & category card
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Department")
                        Surface(
                            onClick = { showClientPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Business, null, tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    selectedClientName.ifBlank { "Select department (optional)" },
                                    color = if (selectedClientName.isBlank()) MaterialTheme.colorScheme.outline
                                            else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                    if (categories?.getOrNull()?.isNotEmpty() == true || categories == null || categories?.isFailure == true) {
                        val selectedCategoryName = categories?.getOrNull()?.firstOrNull { it.id == selectedCategoryId }?.name
                        Column(
                            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SectionLabel("Category")
                            Surface(
                                onClick = { showCategoryPicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Label, null, tint = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        selectedCategoryName ?: "Select category (optional)",
                                        color = if (selectedCategoryName == null) MaterialTheme.colorScheme.outline
                                                else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }

            // Priority card
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Priority")
                    val priorities = listOf("low" to "Low", "medium" to "Medium",
                                             "high" to "High", "critical" to "Critical")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        priorities.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (value, label) ->
                                    PriorityTile(
                                        label = label,
                                        selected = priority == value,
                                        color = MaterialTheme.statusColors.forPriority(value),
                                        onClick = { priority = value },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PriorityTile(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) color.copy(alpha = 0.12f) else Color.Transparent,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) color else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
