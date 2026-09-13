package com.foleyit.itflow.ui.screens.contracts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ContractAllowancePeriod
import com.foleyit.itflow.data.api.ContractDetail
import com.foleyit.itflow.data.api.ContractDocument
import com.foleyit.itflow.data.api.ContractSlaTier
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.SectionLabel
import com.foleyit.itflow.ui.util.fmtDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractDetailScreen(id: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<ContractDetail>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun load() { scope.launch { state = runCatching { ApiClient.service().getContract(id) } } }
    LaunchedEffect(Unit) { load() }

    val contract = state?.getOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contract?.name ?: "Contract") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "Error", onRetry = ::load)
            else -> {
                val c = state!!.getOrThrow()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    listOfNotNull(c.type, c.client).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!c.status.isNullOrBlank()) {
                                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                            Text(c.status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    if (c.isExpired) {
                                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                                            Text("Expired", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    } else if (c.isDueSoon) {
                                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                                            Text("Due Soon", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Details
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                SectionLabel("Details")
                                ContractDetailRow("Value", c.value?.let { "$${"%.2f".format(it)}" })
                                ContractDetailRow("Renewal", c.renewalFrequency)
                                ContractDetailRow("Start", fmtDate(c.startDate))
                                ContractDetailRow("End", fmtDate(c.endDate))
                                ContractDetailRow("Renewal Date", fmtDate(c.renewalDate))
                            }
                        }
                    }

                    // Notes
                    if (!c.details.isNullOrBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    SectionLabel("Notes")
                                    Text(c.details, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // SLA
                    val slaTiers = listOfNotNull(
                        c.sla.high.takeIf { it.responseTime != null || it.resolutionTime != null }?.let { "High" to it },
                        c.sla.medium.takeIf { it.responseTime != null || it.resolutionTime != null }?.let { "Medium" to it },
                        c.sla.low.takeIf { it.responseTime != null || it.resolutionTime != null }?.let { "Low" to it }
                    )
                    if (slaTiers.isNotEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    SectionLabel("SLA")
                                    slaTiers.forEach { (label, tier) -> SlaTierRow(label, tier) }
                                }
                            }
                        }
                    }

                    // Included hours allowance
                    val hasAllowance = c.allowance.remote.included != null || c.allowance.onsite.included != null
                    if (hasAllowance) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    SectionLabel("Included Hours — This Month")
                                    if (c.allowance.remote.included != null) {
                                        AllowanceRow("Remote", c.allowance.remote)
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    if (c.allowance.onsite.included != null) {
                                        AllowanceRow("Onsite", c.allowance.onsite)
                                    }
                                }
                            }
                        }
                    }

                    // Documents
                    if (c.documents.isNotEmpty()) {
                        item { SectionLabel("Documents") }
                        items(c.documents, key = { "d${it.id}" }) { doc -> DocumentRow(doc, context) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContractDetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SlaTierRow(label: String, tier: ContractSlaTier) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            listOfNotNull(
                tier.responseTime?.let { "${it}h response" },
                tier.resolutionTime?.let { "${it}h resolution" }
            ).joinToString(" / "),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AllowanceRow(label: String, period: ContractAllowancePeriod) {
    val included = period.included ?: 0.0
    val progress = if (included > 0) (period.used / included).toFloat().coerceIn(0f, 1f) else 0f
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "${"%.1f".format(period.used)} / ${"%.1f".format(included)} hrs",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraLarge),
            color = if ((period.remaining ?: 0.0) < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DocumentRow(doc: ContractDocument, context: Context) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        ListItem(
            leadingContent = { Icon(Icons.Outlined.AttachFile, null, tint = MaterialTheme.colorScheme.outline) },
            headlineContent = { Text(doc.name, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(fmtDate(doc.uploadedAt), style = MaterialTheme.typography.labelSmall) },
            trailingContent = {
                IconButton(onClick = { openContractDocument(context, doc.url) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open")
                }
            }
        )
    }
}

/** Restricted to http(s) only, matching the same defense-in-depth convention as KB attachment links. */
private fun openContractDocument(context: Context, relativeOrAbsoluteUrl: String) {
    val url = if (relativeOrAbsoluteUrl.startsWith("http")) relativeOrAbsoluteUrl else "${ApiClient.serverUrl}$relativeOrAbsoluteUrl"
    val uri = Uri.parse(url)
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app found to open this document", android.widget.Toast.LENGTH_SHORT).show()
    }
}
