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
    object Projects      : Screen("projects")
    object ProjectDetail : Screen("projects/{id}")   { fun go(id: Int) = "projects/$id" }
    object Contracts     : Screen("contracts")
    object ContractDetail: Screen("contracts/{id}")  { fun go(id: Int) = "contracts/$id" }
    object Credentials   : Screen("credentials")
    object CredDetail    : Screen("credentials/{id}"){ fun go(id: Int) = "credentials/$id" }
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
    object Approvals     : Screen("approvals")
    /** Opens the approvals list with this item's detail already showing (push / deep link). */
    object ApprovalDetail: Screen("approvals/{kind}/{id}") { fun go(kind: String, id: Int) = "approvals/$kind/$id" }
    object Requests      : Screen("requests")
    object MyTasks       : Screen("tasks")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val accessibilityLabel: String = label
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard,    "Home",    Icons.Outlined.Dashboard,           Icons.Filled.Dashboard),
    BottomNavItem(Screen.Tickets,      "Tickets", Icons.Outlined.ConfirmationNumber,  Icons.Filled.ConfirmationNumber),
    BottomNavItem(Screen.Clients,      "Depts", Icons.Outlined.Business,            Icons.Filled.Business, "Departments"),
    BottomNavItem(Screen.Assets,       "Assets",  Icons.Outlined.Devices,             Icons.Filled.Devices),
    BottomNavItem(Screen.Appointments, "Appts",   Icons.Outlined.CalendarMonth,       Icons.Filled.CalendarMonth),
)
