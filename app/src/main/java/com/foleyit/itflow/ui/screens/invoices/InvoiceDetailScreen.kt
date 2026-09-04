package com.foleyit.itflow.ui.screens.invoices

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.InvoiceDetail
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.theme.forFinancialStatus
import com.foleyit.itflow.ui.theme.statusColors
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(id: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<InvoiceDetail>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    fun load() { scope.launch { state = runCatching { ApiClient.service().getInvoice(id) } } }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.getOrNull()?.let { "Invoice #${it.number}" } ?: "Invoice") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
    when {
        state == null -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingScreen() }
        state!!.isFailure -> Box(Modifier.fillMaxSize().padding(padding)) { ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load) }
        else -> {
            val inv = state!!.getOrThrow()
            val statusColor: Color = MaterialTheme.statusColors.forFinancialStatus(inv.status)
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Invoice #${inv.number}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Row {
                                    Surface(color = statusColor.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) { Text(inv.status ?: "", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = statusColor, fontWeight = FontWeight.SemiBold) }
                                    inv.guestUrl?.let { url -> IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ApiClient.serverUrl}$url"))) }) { Icon(Icons.Outlined.OpenInBrowser, "Open in browser") } }
                                }
                            }
                            Text(inv.client ?: "", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Row {
                                Column(Modifier.weight(1f)) { Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Text(inv.date ?: "") }
                                Column(Modifier.weight(1f)) { Text("Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Text(inv.dueDate ?: "", color = if (inv.status == "Overdue") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
                                Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.End) { Text("Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Text(currency.format(inv.balance ?: inv.total ?: 0.0), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Line Items", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            inv.items.forEach { item ->
                                Row(Modifier.padding(bottom = 12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.description ?: "", fontWeight = FontWeight.Medium)
                                        Text("${item.quantity} × ${currency.format(item.unitPrice ?: 0.0)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text(currency.format(item.total ?: 0.0), fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider(); Spacer(Modifier.height(8.dp))
                            listOf("Subtotal" to inv.subtotal, "Tax" to inv.tax, "Total" to inv.total).forEach { (l, v) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(l, fontWeight = if (l == "Total") FontWeight.Bold else FontWeight.Normal)
                                    Text(currency.format(v ?: 0.0), fontWeight = if (l == "Total") FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
