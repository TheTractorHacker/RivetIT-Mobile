package com.foleyit.itflow.ui.screens.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ProjectSummary
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadMoreRow
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.util.fmtDate
import com.foleyit.itflow.ui.util.rememberPagedList
import com.foleyit.itflow.ui.util.userMessage

private val STATUS_FILTERS = listOf("open" to "Open", "completed" to "Completed", "all" to "All")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(navController: NavController) {
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("open") }

    val list = rememberPagedList<ProjectSummary>(status) { page, q ->
        ApiClient.service().getProjects(search = q, page = page, status = status)
    }

    Scaffold { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(bottom = scaffoldPadding.calculateBottomPadding())) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; list.onSearchChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(48.dp),
                placeholder = { Text("Search projects…", style = MaterialTheme.typography.bodyMedium) },
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
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(STATUS_FILTERS) { (value, label) ->
                    FilterChip(
                        selected = status == value,
                        onClick = { status = value },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            val ls = list.state
            when {
                ls.isRefreshing -> LoadingScreen()
                ls.error != null -> ErrorScreen(userMessage(ls.error), onRetry = list::retry)
                ls.items.isEmpty() -> EmptyScreen("No projects found", Icons.Outlined.AccountTree)
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ls.items, key = { it.id }) { p ->
                            ProjectCard(p) { navController.navigate(Screen.ProjectDetail.go(p.id)) }
                        }
                        if (ls.hasMore) {
                            item(key = "load_more") { LoadMoreRow(ls.isLoadingMore, list::loadMore) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(p: ProjectSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick) {
        Column {
            ListItem(
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.AccountTree, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                },
                headlineContent = { Text(p.name, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    Text(listOfNotNull("${p.prefix ?: ""}${p.number}", p.client).joinToString(" • "))
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (!p.dueAt.isNullOrBlank()) {
                            Text("Due ${fmtDate(p.dueAt)}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                        Icon(Icons.Outlined.ChevronRight, null)
                    }
                }
            )
            if (p.taskCount > 0) {
                val progress = p.taskCompletedCount.toFloat() / p.taskCount.toFloat()
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraLarge)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${p.taskCompletedCount}/${p.taskCount} tasks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
