package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ClientsWithBalanceResponse
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsWithBalanceReportScreen(navController: NavController) {
    var state by remember { mutableStateOf<Result<ClientsWithBalanceResponse>?>(null) }
    val scope = rememberCoroutineScope()
    val currency = NumberFormat.getCurrencyInstance(Locale.US)

    fun load() { scope.launch { state = runCatching { ApiClient.service().getClientsWithBalanceReport() } } }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clients with a Balance") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        when {
            state == null -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingScreen() }
            state!!.isFailure -> Box(Modifier.fillMaxSize().padding(padding)) { ErrorScreen(userMessage(state!!.exceptionOrNull()!!), onRetry = ::load) }
            else -> {
                val report = state!!.getOrThrow()
                if (report.clients.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding)) { EmptyScreen("No outstanding balances") }
                } else {
                    val maxBalance = (report.clients.maxOfOrNull { it.balance } ?: 1.0).coerceAtLeast(1.0)
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                        items(report.clients, key = { it.clientId }) { c ->
                            ReportBarRow(
                                label = c.clientName,
                                value = currency.format(c.balance),
                                fraction = (c.balance / maxBalance).toFloat(),
                                barColor = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
