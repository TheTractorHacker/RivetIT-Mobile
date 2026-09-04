package com.foleyit.itflow.ui.screens.clients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.ui.components.*
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.util.rememberPagedList
import com.foleyit.itflow.ui.util.userMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(navController: NavController) {
    var search by remember { mutableStateOf("") }
    val list = rememberPagedList<com.foleyit.itflow.data.api.ClientSummary> { page, q ->
        ApiClient.service().getClients(search = q, page = page)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it; list.onSearchChanged(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search clients…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (search.isNotEmpty()) IconButton(onClick = { search = ""; list.onSearchChanged("") }) { Icon(Icons.Outlined.Clear, "Clear search") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        val ls = list.state
        when {
            ls.isRefreshing -> LoadingScreen()
            ls.error != null -> ErrorScreen(userMessage(ls.error), onRetry = list::retry)
            ls.items.isEmpty() -> EmptyScreen("No clients found", Icons.Outlined.Business)
            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ls.items, key = { it.id }) { c ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = { navController.navigate(Screen.ClientDetail.go(c.id)) }) {
                            ListItem(
                                headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                                supportingContent = if (c.city != null) {{ Text("${c.city}, ${c.state ?: ""}") }} else null,
                                leadingContent = {
                                    Surface(shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)) {
                                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            Text(c.name.first().uppercaseChar().toString(),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                },
                                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }
                            )
                        }
                    }
                    if (ls.hasMore) {
                        item(key = "load_more") { LoadMoreRow(ls.isLoadingMore, list::loadMore) }
                    }
                }
            }
        }
    }
}
