package com.foleyit.itflow.ui.screens.quotes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.ui.components.*
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.forFinancialStatus
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.rememberPagedList
import com.foleyit.itflow.ui.util.userMessage
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesScreen(navController: NavController) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val list = rememberPagedList<com.foleyit.itflow.data.api.QuoteSummary> { page, _ ->
        ApiClient.service().getQuotes(page = page)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Quotes") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) }) { padding ->
        val ls = list.state
        when {
            ls.isRefreshing -> LoadingScreen()
            ls.error != null -> ErrorScreen(userMessage(ls.error), onRetry = list::retry)
            ls.items.isEmpty() -> EmptyScreen("No quotes", Icons.Outlined.RequestQuote)
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ls.items, key = { it.id }) { q ->
                        val statusColor = MaterialTheme.statusColors.forFinancialStatus(q.status)
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = { navController.navigate(Screen.QuoteDetail.go(q.id)) }) {
                            ListItem(
                                headlineContent = { Text(q.subject ?: "", fontWeight = FontWeight.Medium) },
                                supportingContent = { Text("${q.client ?: ""} · ${q.date ?: ""}") },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(currency.format(q.total ?: 0.0), fontWeight = FontWeight.Bold)
                                        Surface(color = statusColor.copy(alpha = 0.12f), shape = MaterialTheme.shapes.extraSmall) { Text(q.status ?: "", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = statusColor) }
                                    }
                                }
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
