package com.foleyit.itflow.ui.screens.notifications

import androidx.compose.foundation.layout.*
import com.foleyit.itflow.ui.util.userMessage
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
fun NotificationsScreen(onUnreadChanged: (Boolean) -> Unit = {}, navController: androidx.navigation.NavController? = null) {
    var state by remember { mutableStateOf<Result<com.foleyit.itflow.data.api.NotificationsResponse>?>(null) }
    var pendingId by remember { mutableStateOf<Int?>(null) }
    var markingAll by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch {
            state = runCatching { ApiClient.service().getNotifications() }
            state?.getOrNull()?.let { onUnreadChanged(it.total > 0) }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun markRead(id: Int) {
        if (pendingId != null || markingAll) return
        pendingId = id
        scope.launch {
            val result = runCatching { ApiClient.service().markRead(id) }
            if (result.isSuccess) {
                state = state?.map { response ->
                    response.copy(data = response.data.filterNot { it.id == id }, total = (response.total - 1).coerceAtLeast(0))
                }
                state?.getOrNull()?.let { onUnreadChanged(it.total > 0) }
            }
            pendingId = null
            if (result.isFailure) snackbar.showSnackbar("Could not mark notification as read. Try again.")
        }
    }

    fun markAllRead() {
        if (pendingId != null || markingAll) return
        markingAll = true
        scope.launch {
            val result = runCatching { ApiClient.service().markAllRead() }
            if (result.isSuccess) {
                state = state?.map { it.copy(data = emptyList(), total = 0) }
                onUnreadChanged(false)
            }
            markingAll = false
            if (result.isFailure) snackbar.showSnackbar("Could not mark all notifications as read. Try again.")
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(
            title = { Text("Notifications") },
            actions = {
                state?.getOrNull()?.data?.takeIf { it.isNotEmpty() }?.let {
                    TextButton(onClick = ::markAllRead, enabled = pendingId == null && !markingAll) {
                        if (markingAll) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Mark all read")
                    }
                }
            }
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state == null -> LoadingScreen()
                state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.let { userMessage(it) } ?: "", onRetry = ::load)
                else -> {
                    val notifs = state!!.getOrThrow().data
                    if (notifs.isEmpty()) EmptyScreen("No new notifications", Icons.Outlined.NotificationsNone)
                    else LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifs, key = { it.id }) { n ->
                            val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) markRead(n.id)
                                // Keep the card until the server confirms the write.
                                false
                            })
                            SwipeToDismissBox(
                                modifier = Modifier.animateItem(),
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = pendingId == null && !markingAll,
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
                                    // Notifications that name a destination (a ticket, an approval) open it when tapped.
                                    val route = com.foleyit.itflow.ui.navigation.PushRouting.routeForNotification(n.type, n.action, n.kind, n.refId)
                                        ?.let { com.foleyit.itflow.ui.navigation.DeepLinks.resolve(it) }
                                    NotifItem(
                                        n, pending = pendingId == n.id, enabled = pendingId == null && !markingAll,
                                        onOpen = if (route != null && navController != null) ({ navController.navigate(route) }) else null,
                                    ) { markRead(n.id) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifItem(n: Notification, pending: Boolean, enabled: Boolean, onOpen: (() -> Unit)? = null, onMarkRead: () -> Unit) {
    val cardModifier = Modifier.fillMaxWidth()
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = when {
                        n.type.contains("approval") -> Icons.Outlined.HowToReg
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
            IconButton(onClick = onMarkRead, enabled = enabled) {
                if (pending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.CheckCircle, contentDescription = "Mark read", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (onOpen != null) Card(onClick = onOpen, modifier = cardModifier, shape = MaterialTheme.shapes.large, content = content)
    else Card(modifier = cardModifier, shape = MaterialTheme.shapes.large, content = content)
}
