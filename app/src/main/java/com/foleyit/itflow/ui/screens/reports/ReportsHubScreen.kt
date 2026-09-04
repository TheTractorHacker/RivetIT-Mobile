package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.ui.navigation.Screen

private data class ReportEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String
)

private val TICKET_REPORTS = listOf(
    ReportEntry("Time Summary", "Hours logged by client", Icons.Outlined.Timer, Screen.TimeReport.route),
    ReportEntry("Ticket Volume", "Tickets raised per month", Icons.AutoMirrored.Outlined.TrendingUp, Screen.TicketVolumeReport.route),
    ReportEntry("Tickets by Client", "Raised, resolved, priority breakdown", Icons.Outlined.Business, Screen.TicketsByClientReport.route),
    ReportEntry("Time by Technician", "Tickets assigned/touched, time worked", Icons.Outlined.Person, Screen.TimeByTechReport.route),
    ReportEntry("Technician Performance", "Open workload vs. resolved this year", Icons.Outlined.EmojiEvents, Screen.TechPerformanceReport.route),
    ReportEntry("Overview", "Open tickets by priority, status, category", Icons.Outlined.PieChart, Screen.OverviewReport.route),
)

private val FINANCIAL_REPORTS = listOf(
    ReportEntry("Income Summary", "Monthly income by category", Icons.Outlined.Paid, Screen.IncomeSummaryReport.route),
    ReportEntry("Expense Summary", "Monthly expenses by category", Icons.Outlined.Receipt, Screen.ExpenseSummaryReport.route),
    ReportEntry("Profit & Loss", "Income minus expenses by month", Icons.Outlined.Balance, Screen.ProfitLossReport.route),
    ReportEntry("Unbilled Tickets", "Billable tickets not yet invoiced", Icons.Outlined.RequestQuote, Screen.UnbilledTicketsReport.route),
    ReportEntry("Clients with a Balance", "Outstanding invoice balances", Icons.Outlined.AccountBalanceWallet, Screen.ClientsWithBalanceReport.route),
)

private val OPERATIONS_REPORTS = listOf(
    ReportEntry("Expiring Domains & Certs", "Renewals due in the next 30 days", Icons.Outlined.EventBusy, Screen.ExpiringReport.route),
)

private val SUPPORT_OPERATIONS_REPORTS = listOf(
    ReportEntry("Customer Satisfaction", "CSAT ratings, trend, feedback", Icons.Outlined.SentimentSatisfied, Screen.CsatReport.route),
    ReportEntry("RMM Health", "Alert volume, MTTA/MTTR, noisiest assets", Icons.Outlined.Warning, Screen.RmmHealthReport.route),
    ReportEntry("Service Desk", "Volume, backlog aging, SLA compliance", Icons.Outlined.SupportAgent, Screen.ServiceDeskReport.route),
    ReportEntry("Technician Utilization", "Billable vs. non-billable, capacity", Icons.Outlined.Groups, Screen.TechUtilizationReport.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHubScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { ReportSectionHeader("Tickets") }
            items(TICKET_REPORTS) { ReportCard(it) { navController.navigate(it.route) } }

            item { Spacer(Modifier.height(12.dp)); ReportSectionHeader("Support & Operations") }
            items(SUPPORT_OPERATIONS_REPORTS) { ReportCard(it) { navController.navigate(it.route) } }

            item { Spacer(Modifier.height(12.dp)); ReportSectionHeader("Financial") }
            items(FINANCIAL_REPORTS) { ReportCard(it) { navController.navigate(it.route) } }

            item { Spacer(Modifier.height(12.dp)); ReportSectionHeader("Operations") }
            items(OPERATIONS_REPORTS) { ReportCard(it) { navController.navigate(it.route) } }
        }
    }
}

@Composable
private fun ReportSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ReportCard(entry: ReportEntry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = MaterialTheme.shapes.large, onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(entry.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.Medium)
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
