package com.foleyit.itflow.ui.screens.contracts

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ContractSummary
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadMoreRow
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.util.fmtDate
import com.foleyit.itflow.ui.util.rememberPagedList
import com.foleyit.itflow.ui.util.userMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractsScreen(navController: NavController) {
    var search by remember { mutableStateOf("") }
    var expiringOnly by remember { mutableStateOf(false) }

    val list = rememberPagedList<ContractSummary>(expiringOnly) { page, q ->
        ApiClient.service().getContracts(search = q, page = page, expiring = if (expiringOnly) 1 else 0)
    }

    Scaffold { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(bottom = scaffoldPadding.calculateBottomPadding())) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; list.onSearchChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(48.dp),
                placeholder = { Text("Search contracts…", style = MaterialTheme.typography.bodyMedium) },
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
                item {
                    FilterChip(selected = !expiringOnly, onClick = { expiringOnly = false }, label = { Text("All") })
                }
                item {
                    FilterChip(
                        selected = expiringOnly, onClick = { expiringOnly = true },
                        label = { Text("Expiring / Expired") },
                        leadingIcon = if (expiringOnly) { { Icon(Icons.Outlined.EventBusy, null, Modifier.size(16.dp)) } } else null
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            val ls = list.state
            when {
                ls.isRefreshing -> LoadingScreen()
                ls.error != null -> ErrorScreen(userMessage(ls.error), onRetry = list::retry)
                ls.items.isEmpty() -> EmptyScreen("No contracts found", Icons.Outlined.Description)
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ls.items, key = { it.id }) { c ->
                            ContractCard(c) { navController.navigate(Screen.ContractDetail.go(c.id)) }
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
private fun ContractCard(c: ContractSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick) {
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            },
            headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text(listOfNotNull(c.type, c.client).joinToString(" • "))
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    when {
                        c.isExpired -> Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                            Text("Expired", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        c.isDueSoon -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                            Text("Due Soon", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        !c.renewalDate.isNullOrBlank() -> Text(
                            "Renews ${fmtDate(c.renewalDate)}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        )
    }
}
