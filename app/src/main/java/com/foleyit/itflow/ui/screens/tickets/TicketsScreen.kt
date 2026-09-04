package com.foleyit.itflow.ui.screens.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.SavedTicketView
import com.foleyit.itflow.data.api.TicketCategory
import com.foleyit.itflow.data.api.TicketSummary
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadMoreRow
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.SectionLabel
import com.foleyit.itflow.ui.components.pressScale
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.forPriority
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.fmtDate
import com.foleyit.itflow.ui.util.rememberPagedList
import com.foleyit.itflow.ui.util.userMessage

private fun ticketStatusColor(hex: String?): Color = try {
    Color(android.graphics.Color.parseColor(if (hex?.startsWith("#") == true) hex else "#${hex}"))
} catch (_: Exception) { Color.Gray }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(navController: NavController) {
    var search by remember { mutableStateOf("") }
    var mineOnly by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var priorityFilter by remember { mutableStateOf<String?>(null) }
    var onsiteFilter by remember { mutableStateOf<Int?>(null) }  // null=all, 1=onsite, 0=remote
    var categoryFilter by remember { mutableStateOf<Int?>(null) }
    var categories by remember { mutableStateOf<List<TicketCategory>>(emptyList()) }
    var savedViews by remember { mutableStateOf<List<SavedTicketView>>(emptyList()) }
    var activeView by remember { mutableStateOf<SavedTicketView?>(null) }
    var showViewsMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val filtersActive = priorityFilter != null || onsiteFilter != null || categoryFilter != null

    val list = rememberPagedList(selectedTab, mineOnly, priorityFilter, onsiteFilter, categoryFilter, activeView) { page, q ->
        val view = activeView
        if (view != null) {
            val p = view.params
            ApiClient.service().getTickets(
                status = p["status"] ?: "open",
                mine = p["mine"]?.toIntOrNull() ?: 0,
                search = p["search"] ?: q,
                priority = p["priority"]?.takeIf { it.isNotBlank() },
                onsite = p["onsite"]?.toIntOrNull(),
                categoryId = p["category_id"]?.toIntOrNull(),
                overdue = p["overdue"]?.toIntOrNull(),
                dueToday = p["due_today"]?.toIntOrNull(),
                page = page
            )
        } else {
            ApiClient.service().getTickets(
                status = if (selectedTab == 0) "open" else "closed",
                mine = if (mineOnly) 1 else 0,
                search = q,
                priority = priorityFilter?.takeIf { it.isNotBlank() },
                onsite = onsiteFilter,
                categoryId = categoryFilter,
                page = page
            )
        }
    }

    fun applyView(view: SavedTicketView) { activeView = view }

    LaunchedEffect(Unit) {
        runCatching { categories = ApiClient.service().getTicketCategories() }
        runCatching { savedViews = ApiClient.service().getSavedTicketViews() }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.CreateTicket.route) },
                modifier = Modifier.pressScale(0.90f),
            ) {
                Icon(Icons.Outlined.Add, "New Ticket")
            }
        }
    ) { scaffoldPadding ->
    // Only use bottom padding — top is handled by the outer MainScreen Scaffold
    Column(Modifier.fillMaxSize().padding(bottom = scaffoldPadding.calculateBottomPadding())) {
        // Compact search + Mine filter
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; list.onSearchChanged(it) },
                modifier = Modifier.weight(1f).height(48.dp),
                placeholder = { Text("Search tickets…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = ""; list.onSearchChanged("") }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Clear, "Clear search", Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            FilterChip(
                selected = mineOnly,
                onClick = { mineOnly = !mineOnly },
                label = { Text("Mine", style = MaterialTheme.typography.labelMedium) }
            )
            Box {
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(Icons.Outlined.Tune, "Filters")
                }
                if (filtersActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    )
                }
            }
            if (savedViews.isNotEmpty()) {
                Box {
                    IconButton(onClick = { showViewsMenu = true }) {
                        Icon(Icons.Outlined.BookmarkBorder, "Saved Views")
                    }
                    DropdownMenu(expanded = showViewsMenu, onDismissRequest = { showViewsMenu = false }) {
                        savedViews.forEach { view ->
                            DropdownMenuItem(
                                text = { Text(view.name) },
                                onClick = { showViewsMenu = false; applyView(view) }
                            )
                        }
                    }
                }
            }
        }

        activeView?.let { view ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(view.name, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        IconButton(onClick = { activeView = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Close, "Clear view", Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Segmented pill control for Open/Closed
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                listOf("Open" to 0, "Closed" to 1).forEach { (label, index) ->
                    val selected = selectedTab == index
                    Surface(
                        selected = selected,
                        onClick = { selectedTab = index },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        val ls = list.state
        when {
            ls.isRefreshing -> LoadingScreen()
            ls.error != null -> ErrorScreen(userMessage(ls.error), onRetry = list::retry)
            ls.items.isEmpty() -> EmptyScreen("No tickets found", Icons.Outlined.ConfirmationNumber)
            selectedTab == 0 -> {
                // Open tickets — grouped by status
                val tickets = ls.items
                val statusOrder = tickets.map { it.status ?: "Unknown" }.distinct()
                val grouped = tickets.groupBy { it.status ?: "Unknown" }
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    statusOrder.forEach { status ->
                        val group = grouped[status] ?: return@forEach
                        val color = group.firstOrNull()?.statusColor
                        item(key = "header_$status") {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = ticketStatusColor(color),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "$status  ·  ${group.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(group, key = { it.id }) { t ->
                            TicketCard(t) { navController.navigate(Screen.TicketDetail.go(t.id)) }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (ls.hasMore) {
                        item(key = "load_more") {
                            LoadMoreRow(ls.isLoadingMore, list::loadMore)
                        }
                    }
                }
            }
            else -> {
                // Closed tickets — simple flat list
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ls.items, key = { it.id }) { t ->
                        TicketCard(t) { navController.navigate(Screen.TicketDetail.go(t.id)) }
                    }
                    if (ls.hasMore) {
                        item(key = "load_more") {
                            LoadMoreRow(ls.isLoadingMore, list::loadMore)
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filters", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        priorityFilter = null
                        onsiteFilter = null
                        categoryFilter = null
                    }) {
                        Text("Clear all")
                    }
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel("Priority")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(null to "All", "critical" to "Critical", "high" to "High",
                           "medium" to "Medium", "low" to "Low").forEach { (value, label) ->
                        FilterChip(
                            selected = priorityFilter == value,
                            onClick = { priorityFilter = if (priorityFilter == value && value != null) null else value },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionLabel("Location")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = onsiteFilter == 1,
                        onClick = { onsiteFilter = if (onsiteFilter == 1) null else 1 },
                        label = { Text("On-Site") },
                        leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = onsiteFilter == 0,
                        onClick = { onsiteFilter = if (onsiteFilter == 0) null else 0 },
                        label = { Text("Remote") },
                        leadingIcon = { Icon(Icons.Outlined.Wifi, null, Modifier.size(14.dp)) }
                    )
                }

                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Category")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = categoryFilter == cat.id,
                                onClick = { categoryFilter = if (categoryFilter == cat.id) null else cat.id },
                                label = { Text(cat.name) },
                                leadingIcon = {
                                    Surface(
                                        color = ticketStatusColor(cat.color),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier.size(10.dp)
                                    ) {}
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
    } // end Scaffold
}

@Composable
fun TicketCard(ticket: TicketSummary, onClick: () -> Unit) {
    val priorityColor = MaterialTheme.statusColors.forPriority(ticket.priority)
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp)) {
            Surface(
                color = priorityColor,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.width(4.dp).height(64.dp)
            ) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${ticket.number}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    ticket.assignedTo?.let { assigned ->
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.Person, null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(2.dp))
                        Text(assigned, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(ticket.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Business, null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    Text(ticket.client ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    ticket.dueAt?.let { due ->
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.Schedule, null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(2.dp))
                        Text(fmtDate(due), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
