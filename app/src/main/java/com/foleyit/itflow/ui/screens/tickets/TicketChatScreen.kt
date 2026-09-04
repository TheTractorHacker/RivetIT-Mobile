package com.foleyit.itflow.ui.screens.tickets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ChatMessage
import com.foleyit.itflow.data.api.SendChatMessageRequest
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.fmtDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketChatScreen(ticketId: Int, navController: NavController) {
    var loading by remember { mutableStateOf(true) }
    var chatDisabled by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun poll() {
        try {
            val sinceId = messages.lastOrNull()?.id ?: 0
            val resp = ApiClient.service().getChatMessages(ticketId, sinceId)
            val newOnes = resp.data.filterNot { incoming -> messages.any { it.id == incoming.id } }
            if (newOnes.isNotEmpty()) {
                messages.addAll(newOnes)
                scope.launch { listState.animateScrollToItem(messages.size - 1) }
            }
            chatDisabled = false
            errorMsg = null
        } catch (e: HttpException) {
            if (e.code() == 403) chatDisabled = true
            else errorMsg = "Failed to load chat"
        } catch (e: Exception) {
            errorMsg = "Failed to load chat"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(ticketId) {
        // Stop polling while the app/screen is backgrounded to save battery and data.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                poll()
                delay(1500)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Chat") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (!chatDisabled) {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Message") },
                            shape = MaterialTheme.shapes.extraLarge,
                            maxLines = 4
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            enabled = draft.isNotBlank() && !sending,
                            onClick = {
                                val text = draft.trim()
                                draft = ""
                                sending = true
                                scope.launch {
                                    try {
                                        ApiClient.service().sendChatMessage(ticketId, SendChatMessageRequest(text))
                                        poll()
                                    } catch (e: Exception) {
                                        errorMsg = "Failed to send message"
                                    } finally {
                                        sending = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, "Send")
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingScreen() }
            chatDisabled -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Forum, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Text("Live chat is not enabled for this ticket",
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                errorMsg?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(it, modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No messages yet", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatBubble(msg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isAgent = msg.senderType == "agent"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAgent) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isAgent) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!msg.senderName.isNullOrBlank()) {
                    Text(
                        msg.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAgent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Text(
                    msg.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAgent) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    fmtDate(msg.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAgent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
