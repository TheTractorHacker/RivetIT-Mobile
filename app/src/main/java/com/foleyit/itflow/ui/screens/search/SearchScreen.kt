package com.foleyit.itflow.ui.screens.search

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.AssetSummary
import com.foleyit.itflow.data.api.ClientSummary
import com.foleyit.itflow.data.api.SearchResult
import com.foleyit.itflow.data.api.TicketSummary
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.forPriority
import com.foleyit.itflow.ui.theme.statusColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun search(q: String) {
        searchJob?.cancel()
        error = null
        if (q.length < 2) { result = null; return }
        searchJob = scope.launch {
            delay(300)
            loading = true
            val r = runCatching { ApiClient.service().search(q) }
            result = r.getOrNull()
            error = r.exceptionOrNull()?.localizedMessage
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; search(it) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).focusRequester(focus),
                        placeholder = { Text("Search…", style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; result = null; error = null },
                                    modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Outlined.Clear, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        val r = result
        val hasResults = r != null && (r.tickets.isNotEmpty() || r.clients.isNotEmpty() || r.assets.isNotEmpty())
        when {
            loading -> LoadingScreen()
            error != null -> ErrorScreen(error ?: "Something went wrong")
            query.length < 2 -> EmptyScreen("Type at least 2 characters to search", Icons.Outlined.Search)
            !hasResults -> EmptyScreen("No results for \"$query\"", Icons.Outlined.SearchOff)
            else -> {
                val rr = r!!
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (rr.tickets.isNotEmpty()) {
                        item { SectionHeader("Tickets", Icons.Outlined.ConfirmationNumber) }
                        items(rr.tickets) { t ->
                            TicketResultCard(t) { navController.navigate(Screen.TicketDetail.go(t.id)) }
                        }
                    }
                    if (rr.clients.isNotEmpty()) {
                        item { SectionHeader("Clients", Icons.Outlined.Business) }
                        items(rr.clients) { c ->
                            ClientResultCard(c) { navController.navigate(Screen.ClientDetail.go(c.id)) }
                        }
                    }
                    if (rr.assets.isNotEmpty()) {
                        item { SectionHeader("Assets", Icons.Outlined.Devices) }
                        items(rr.assets) { a ->
                            AssetResultCard(a) { navController.navigate(Screen.AssetDetail.go(a.id)) }
                        }
                    }
                }
            }
        }
    }
}

/** Ticket result row — a thin priority-color accent bar, same idiom as TicketCard on the Tickets list screen. */
@Composable
private fun TicketResultCard(t: TicketSummary, onClick: () -> Unit) {
    val priorityColor = MaterialTheme.statusColors.forPriority(t.priority)
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = priorityColor,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.width(4.dp).height(40.dp)
            ) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "#${t.number} ${t.subject}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${t.status ?: ""} · ${t.client ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Client result row — initial-letter avatar tile, same idiom as the Clients list screen. */
@Composable
private fun ClientResultCard(c: ClientSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick) {
        ListItem(
            headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
            leadingContent = {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            c.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }
        )
    }
}

/** Asset result row — device-icon tile so it reads distinctly from the ticket/client rows above it. */
@Composable
private fun AssetResultCard(a: AssetSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick) {
        ListItem(
            headlineContent = { Text(a.name, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text("${listOfNotNull(a.make, a.model).joinToString(" ")} · ${a.client ?: ""}")
            },
            leadingContent = {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Devices, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
