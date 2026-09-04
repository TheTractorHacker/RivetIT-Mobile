package com.foleyit.itflow.ui.screens.tickets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(navController: NavController) {
    var subject by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("low") }
    var selectedClientId by remember { mutableStateOf<Int?>(null) }
    var selectedClientName by remember { mutableStateOf("") }
    var clients by remember { mutableStateOf<ClientsResponse?>(null) }
    var showClientPicker by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<TicketCategory>>(emptyList()) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        // Load all pages until we have enough clients for the picker
        clients = runCatching { ApiClient.service().getClients(search = "", page = 1) }.getOrNull()
        categories = runCatching { ApiClient.service().getTicketCategories() }.getOrDefault(emptyList())
    }

    fun submit() {
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
                error = userMessage(e)
            } finally { saving = false }
        }
    }

    if (showClientPicker) {
        ModalBottomSheet(onDismissRequest = { showClientPicker = false }) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("Select Client", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Surface(onClick = { selectedClientId = null; selectedClientName = ""; showClientPicker = false },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("— No Client —", Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
                clients?.data?.forEach { c ->
                    Surface(onClick = {
                        selectedClientId = c.id; selectedClientName = c.name; showClientPicker = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(c.name, Modifier.padding(16.dp))
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCategoryPicker) {
        ModalBottomSheet(onDismissRequest = { showCategoryPicker = false }) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("Select Category", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Surface(onClick = { selectedCategoryId = null; showCategoryPicker = false },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("— No Category —", Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
                categories.forEach { cat ->
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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

            // Client & category card
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Client")
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
                                    selectedClientName.ifBlank { "Select client (optional)" },
                                    color = if (selectedClientName.isBlank()) MaterialTheme.colorScheme.outline
                                            else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                    if (categories.isNotEmpty()) {
                        val selectedCategoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name
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
