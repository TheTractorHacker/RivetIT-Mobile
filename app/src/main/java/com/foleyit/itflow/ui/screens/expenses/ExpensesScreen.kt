package com.foleyit.itflow.ui.screens.expenses

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
import com.foleyit.itflow.ui.util.rememberPagedList
import com.foleyit.itflow.ui.util.userMessage
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(navController: NavController) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val list = rememberPagedList<com.foleyit.itflow.data.api.ExpenseSummary> { page, _ ->
        ApiClient.service().getExpenses(page = page)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Expenses") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.AddExpense.route) },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add Expense") },
                modifier = Modifier.pressScale(0.90f),
            )
        }
    ) { padding ->
        val ls = list.state
        when {
            ls.isRefreshing -> LoadingScreen()
            ls.error != null -> ErrorScreen(userMessage(ls.error), onRetry = list::retry)
            ls.items.isEmpty() -> EmptyScreen("No expenses. Tap + to add one.", Icons.Outlined.Receipt)
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ls.items, key = { it.id }) { e ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            ListItem(
                                headlineContent = { Text(e.description ?: "", fontWeight = FontWeight.Medium) },
                                supportingContent = { Text("${e.date ?: ""} · ${e.paymentMethod ?: ""}") },
                                leadingContent = {
                                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(40.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(if (e.hasReceipt) Icons.AutoMirrored.Outlined.ReceiptLong else Icons.Outlined.Receipt, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp)) }
                                    }
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(currency.format(e.amount ?: 0.0), fontWeight = FontWeight.Bold)
                                        e.client?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
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
