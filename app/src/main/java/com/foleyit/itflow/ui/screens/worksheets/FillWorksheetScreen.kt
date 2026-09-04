package com.foleyit.itflow.ui.screens.worksheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.*
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillWorksheetScreen(worksheetId: Int, navController: NavController) {
    var worksheet by remember { mutableStateOf<WorksheetDetail?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var fieldValues by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun load() {
        loading = true; loadError = null
        scope.launch {
            val result = runCatching { ApiClient.service().getWorksheet(worksheetId) }
            worksheet = result.getOrNull()
            loadError = if (result.isFailure) result.exceptionOrNull()?.message ?: "Failed to load" else null
            worksheet?.fields?.forEach { f ->
                if (!f.value.isNullOrEmpty()) fieldValues = fieldValues + (f.id to f.value)
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    fun saveResponses() {
        saving = true
        scope.launch {
            try {
                val responses = fieldValues.map { (fid, v) -> FieldResponse(fid, v) }
                withContext(Dispatchers.IO) {
                    ApiClient.service().saveResponses(worksheetId, SaveResponsesRequest(responses))
                }
                snackbar.showSnackbar("Worksheet saved")
                navController.popBackStack()
            } catch (e: Exception) {
                snackbar.showSnackbar("Failed: ${e.message}")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(worksheet?.templateName ?: "Fill Worksheet") },
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
                        onClick = { saveResponses() },
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
                            Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (loading) { LoadingScreen(); return@Scaffold }
        if (loadError != null) { ErrorScreen(loadError!!, onRetry = ::load); return@Scaffold }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            worksheet?.fields?.let { fields ->
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            fields.forEach { field ->
                                when (field.type) {
                                    "heading" -> Text(field.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp))
                                    "checkbox" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = fieldValues[field.id] == "true",
                                            onCheckedChange = { fieldValues = fieldValues + (field.id to it.toString()) }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(field.name)
                                    }
                                    "select" -> {
                                        val options = field.options?.split(",")?.map { it.trim() } ?: emptyList()
                                        var expanded by remember { mutableStateOf(false) }
                                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                            OutlinedTextField(
                                                value = fieldValues[field.id] ?: "",
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text(field.name) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                options.forEach { opt ->
                                                    DropdownMenuItem(text = { Text(opt) }, onClick = {
                                                        fieldValues = fieldValues + (field.id to opt)
                                                        expanded = false
                                                    })
                                                }
                                            }
                                        }
                                    }
                                    "textarea" -> OutlinedTextField(
                                        value = fieldValues[field.id] ?: "",
                                        onValueChange = { fieldValues = fieldValues + (field.id to it) },
                                        label = { Text(field.name) },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3, maxLines = 6
                                    )
                                    "signature" -> Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                                    ) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Draw, null, tint = MaterialTheme.colorScheme.outline)
                                            Spacer(Modifier.width(8.dp))
                                            Text("${field.name} — not collected on mobile",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    else -> OutlinedTextField(
                                        value = fieldValues[field.id] ?: "",
                                        onValueChange = { fieldValues = fieldValues + (field.id to it) },
                                        label = { Text(field.name) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
