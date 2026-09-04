package com.foleyit.itflow.ui.screens.quotes

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
import com.foleyit.itflow.data.api.QuoteDetail
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.theme.forFinancialStatus
import com.foleyit.itflow.ui.theme.statusColors
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDetailScreen(id: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<QuoteDetail>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    fun load() { scope.launch { state = runCatching { ApiClient.service().getQuote(id) } } }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.getOrNull()?.subject?.takeIf { it.isNotBlank() } ?: "Quote") },
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
            val q = state!!.getOrThrow()
            val statusColor: Color = MaterialTheme.statusColors.forFinancialStatus(q.status)
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(q.subject ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                q.guestUrl?.let {
                                    val url = "${ApiClient.serverUrl}$it"
                                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Icon(Icons.Outlined.OpenInBrowser, "Open in browser") }
                                }
                            }
                            Text(q.client ?: "", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) { Text("${q.date ?: ""}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) }
                                Surface(color = statusColor.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) { Text(q.status ?: "", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Line Items", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            (q.items).forEach { item ->
                                Row(Modifier.padding(bottom = 12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.description ?: "", fontWeight = FontWeight.Medium)
                                        Text("${item.quantity} × ${currency.format(item.unitPrice ?: 0.0)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text(currency.format(item.total ?: 0.0), fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            listOf("Subtotal" to q.subtotal, "Tax" to q.tax, "Total" to q.total).forEach { (l, v) ->
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
