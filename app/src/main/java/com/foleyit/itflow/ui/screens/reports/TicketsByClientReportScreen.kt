package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.TicketsByClientResponse
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsByClientReportScreen(navController: NavController) {
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var state by remember { mutableStateOf<Result<TicketsByClientResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() { scope.launch { state = runCatching { ApiClient.service().getTicketsByClientReport(year) } } }
    LaunchedEffect(year) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tickets by Client") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                YearPicker(year) { year = it }
            }
            when {
                state == null -> LoadingScreen()
                state!!.isFailure -> ErrorScreen(userMessage(state!!.exceptionOrNull()!!), onRetry = ::load)
                else -> {
                    val report = state!!.getOrThrow()
                    if (report.clients.isEmpty()) {
                        EmptyScreen("No tickets in $year")
                    } else {
                        val maxRaised = (report.clients.maxOfOrNull { it.raised } ?: 1).coerceAtLeast(1)
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(report.clients, key = { it.clientId }) { c ->
                                ReportBarRow(
                                    label = c.client,
                                    value = "${c.raised} raised",
                                    fraction = c.raised.toFloat() / maxRaised,
                                    subtitle = "${c.resolved} resolved · ${formatSeconds(c.secondsWorked)} worked · " +
                                        "L${c.priorityLow}/M${c.priorityMedium}/H${c.priorityHigh}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
