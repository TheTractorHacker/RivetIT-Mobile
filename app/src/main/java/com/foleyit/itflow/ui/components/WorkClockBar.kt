package com.foleyit.itflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foleyit.itflow.data.local.WorkClock
import com.foleyit.itflow.data.local.WorkSession
import com.foleyit.itflow.data.local.formatClock
import kotlinx.coroutines.delay

/**
 * The technician's running clock, docked above the bottom navigation on every screen so time keeps
 * counting while they move between tickets, clients and assets. Hidden when nothing is clocked, and
 * on the clocked ticket's own screen (which shows the same clock in its header).
 */
@Composable
fun WorkClockBar(
    hideOnTicketId: Int?,
    onOpenTicket: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by WorkClock.state.collectAsState()
    val session = state.active?.takeUnless { it.ticketId == hideOnTicketId }

    AnimatedVisibility(
        visible = session != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        // Keep the last session while the exit animation plays instead of going blank.
        var shown by remember { mutableStateOf(session) }
        if (session != null) shown = session
        shown?.let { s ->
            WorkClockRow(
                session = s,
                otherPaused = state.sessions.count { it.ticketId != s.ticketId && !it.running },
                onOpen = { onOpenTicket(s.ticketId) },
            )
        }
    }
}

@Composable
private fun WorkClockRow(session: WorkSession, otherPaused: Int, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.running, session.ticketId) {
        while (session.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (session.running) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(8.dp),
                ) {}
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "#${session.number} ${session.subject}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(if (session.running) "On the clock" else "Paused")
                            if (otherPaused > 0) append(" · $otherPaused more paused")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatClock(session.elapsedMs(now)),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(
                onClick = {
                    if (session.running) WorkClock.pause(session.ticketId)
                    else WorkClock.start(session.ticketId, session.number, session.subject, session.client)
                },
            ) {
                Icon(
                    if (session.running) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                    contentDescription = if (session.running) "Pause timer" else "Resume timer",
                )
            }
            IconButton(
                onClick = {
                    WorkClock.requestLog(session.ticketId)
                    onOpen()
                },
                modifier = Modifier.semantics { contentDescription = "Stop and log time" },
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
            }
        }
    }
}
