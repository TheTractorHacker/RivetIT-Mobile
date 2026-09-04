package com.foleyit.itflow.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*

sealed class Screen(val route: String) {
    object Setup         : Screen("setup")
    object Login         : Screen("login")
    object Dashboard     : Screen("dashboard")
    object Tickets       : Screen("tickets")
    object TicketDetail  : Screen("tickets/{id}")    { fun go(id: Int) = "tickets/$id" }
    object TicketChat    : Screen("tickets/{id}/chat") { fun go(id: Int) = "tickets/$id/chat" }
    object Clients       : Screen("clients")
    object ClientDetail  : Screen("clients/{id}")    { fun go(id: Int) = "clients/$id" }
    object Assets        : Screen("assets")
    object AssetDetail   : Screen("assets/{id}")     { fun go(id: Int) = "assets/$id" }
    object Credentials   : Screen("credentials")
    object CredDetail    : Screen("credentials/{id}"){ fun go(id: Int) = "credentials/$id" }
    object Quotes        : Screen("quotes")
    object QuoteDetail   : Screen("quotes/{id}")     { fun go(id: Int) = "quotes/$id" }
    object Invoices      : Screen("invoices")
    object InvoiceDetail : Screen("invoices/{id}")   { fun go(id: Int) = "invoices/$id" }
    object Expenses      : Screen("expenses")
    object AddExpense    : Screen("expenses/add")
    object Notifications : Screen("notifications")
    object Appointments  : Screen("appointments")
    object FillWorksheet : Screen("worksheets/{id}/fill") { fun go(id: Int) = "worksheets/$id/fill" }
    object OuttakeSign   : Screen("outtakes/{id}/sign")   { fun go(id: Int) = "outtakes/$id/sign" }
    object CreateTicket  : Screen("tickets/create")
    object Search        : Screen("search")
    object TimeReport    : Screen("reports/time")
    object ReportsHub    : Screen("reports")
    object TicketVolumeReport      : Screen("reports/tickets")
    object TicketsByClientReport   : Screen("reports/tickets-by-client")
    object TimeByTechReport        : Screen("reports/time-by-tech")
    object TechPerformanceReport   : Screen("reports/tech-performance")
    object OverviewReport          : Screen("reports/overview")
    object UnbilledTicketsReport   : Screen("reports/unbilled-tickets")
    object ClientsWithBalanceReport: Screen("reports/clients-with-balance")
    object IncomeSummaryReport     : Screen("reports/income-summary")
    object ExpenseSummaryReport    : Screen("reports/expense-summary")
    object ProfitLossReport        : Screen("reports/profit-loss")
    object ExpiringReport          : Screen("reports/expiring")
    object CsatReport              : Screen("reports/csat")
    object RmmHealthReport         : Screen("reports/rmm-health")
    object ServiceDeskReport       : Screen("reports/service-desk")
    object TechUtilizationReport   : Screen("reports/technician-utilization")
    object ScanBarcode   : Screen("scan/barcode")
    object Profile       : Screen("profile")
    object KnowledgeBase : Screen("kb")
    object KbArticleDetail : Screen("kb/{id}") { fun go(id: Int) = "kb/$id" }
    object Alerts        : Screen("alerts")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard,    "Home",    Icons.Outlined.Dashboard,           Icons.Filled.Dashboard),
    BottomNavItem(Screen.Tickets,      "Tickets", Icons.Outlined.ConfirmationNumber,  Icons.Filled.ConfirmationNumber),
    BottomNavItem(Screen.Clients,      "Clients", Icons.Outlined.Business,            Icons.Filled.Business),
    BottomNavItem(Screen.Assets,       "Assets",  Icons.Outlined.Devices,             Icons.Filled.Devices),
    BottomNavItem(Screen.Appointments, "Appts",   Icons.Outlined.CalendarMonth,       Icons.Filled.CalendarMonth),
)
