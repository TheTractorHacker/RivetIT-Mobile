package com.foleyit.itflow.ui.screens.notifications

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
import androidx.compose.ui.unit.dp
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.Notification
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen() {
    var state by remember { mutableStateOf<Result<com.foleyit.itflow.data.api.NotificationsResponse>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { state = runCatching { ApiClient.service().getNotifications() } } }
    LaunchedEffect(Unit) { load() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Notifications") },
            actions = {
                state?.getOrNull()?.data?.takeIf { it.isNotEmpty() }?.let {
                    TextButton(onClick = { scope.launch { runCatching { ApiClient.service().markAllRead() }; load() } }) { Text("Mark all read") }
                }
            }
        )
    }) { padding ->
        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
            else -> {
                val notifs = state!!.getOrThrow().data
                if (notifs.isEmpty()) EmptyScreen("No new notifications", Icons.Outlined.NotificationsNone)
                else LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifs, key = { it.id }) { n ->
                        val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                scope.launch { runCatching { ApiClient.service().markRead(n.id) }; load() }
                            }
                            true
                        })
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Box(Modifier.fillMaxSize().padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = "Mark read",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            content = {
                                NotifItem(n) {
                                    scope.launch { runCatching { ApiClient.service().markRead(n.id) }; load() }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifItem(n: Notification, onMarkRead: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = when {
                        n.type.contains("ticket") -> Icons.Outlined.ConfirmationNumber
                        n.type.contains("invoice") -> Icons.AutoMirrored.Outlined.ReceiptLong
                        else -> Icons.Outlined.Notifications
                    }
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.message, style = MaterialTheme.typography.bodyMedium)
                n.timestamp?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onMarkRead) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Mark read", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
