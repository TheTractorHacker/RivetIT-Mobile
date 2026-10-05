package com.foleyit.itflow.ui.screens.main

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.local.AppPreferences
import com.foleyit.itflow.ui.components.AppDrawerContent
import com.foleyit.itflow.ui.components.BrandMark
import com.foleyit.itflow.ui.components.FloatingBottomNavBar
import com.foleyit.itflow.ui.components.OfflineBanner
import com.foleyit.itflow.ui.components.UnreadDot
import com.foleyit.itflow.ui.navigation.*
import com.foleyit.itflow.ui.theme.Motion
import com.foleyit.itflow.ui.theme.ThemeMode
import com.foleyit.itflow.ui.util.rememberIsOnline
import com.foleyit.itflow.ui.screens.appointments.AppointmentsScreen
import com.foleyit.itflow.ui.screens.worksheets.FillWorksheetScreen
import com.foleyit.itflow.ui.screens.worksheets.OuttakeSignScreen
import com.foleyit.itflow.ui.screens.profile.ProfileScreen
import com.foleyit.itflow.ui.screens.assets.*
import com.foleyit.itflow.ui.screens.projects.*
import com.foleyit.itflow.ui.screens.contracts.*
import com.foleyit.itflow.ui.screens.clients.*
import com.foleyit.itflow.ui.screens.credentials.*
import com.foleyit.itflow.ui.screens.dashboard.DashboardScreen
import com.foleyit.itflow.ui.screens.kb.KbArticleDetailScreen
import com.foleyit.itflow.ui.screens.kb.KnowledgeBaseScreen
import com.foleyit.itflow.ui.screens.notifications.NotificationsScreen
import com.foleyit.itflow.ui.screens.alerts.AlertsScreen
import com.foleyit.itflow.ui.screens.tickets.*
import com.foleyit.itflow.ui.screens.search.SearchScreen
import com.foleyit.itflow.ui.screens.reports.*
import com.foleyit.itflow.ui.screens.scan.ScanBarcodeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Widest the screen content is allowed to grow before it is centred with margins. */
private val ContentMaxWidth = 840.dp

// Routes that show the main app bar. Credentials is deliberately absent: it draws its own
// back-arrow header (it is behind a biometric gate), so listing it here stacked two app bars.
private val ROOT_ROUTES = setOf(
    Screen.Dashboard.route, Screen.Tickets.route, Screen.Clients.route,
    Screen.Assets.route, Screen.Projects.route, Screen.Contracts.route, Screen.Appointments.route,
    Screen.Notifications.route, Screen.Alerts.route
)

// The 5 true root screens — only these show the floating bottom nav (the rest of ROOT_ROUTES
// gets the brand top bar but is reached via the drawer, not a persistent tab).
private val BOTTOM_NAV_ROUTES = bottomNavItems.map { it.screen.route }.toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: AppPreferences,
    onLoggedOut: () -> Unit,
    deepLinkRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onChangeServer: () -> Unit = {}
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var hasUnreadNotifications by remember { mutableStateOf(false) }
    var capabilities by remember { mutableStateOf(com.foleyit.itflow.data.Capabilities.Unrestricted) }

    val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val isDarkMode = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }

    LaunchedEffect(Unit) {
        userName = prefs.userName.first() ?: ""
        userEmail = prefs.userEmail.first() ?: ""
        try {
            ApiClient.profile().let { capabilities = com.foleyit.itflow.data.Capabilities(it.isAdmin, it.permissions) }
        } catch (_: Exception) {
            // Keep the permissive default; the server still enforces permissions.
        }
        try {
            hasUnreadNotifications = ApiClient.service().getDashboard().unread > 0
        } catch (_: Exception) {
            // Chrome badge only — a failed fetch here shouldn't block rendering the screen.
        }
    }

    fun closeDrawerAndNavigate(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            launchSingleTop = true
            if (route in BOTTOM_NAV_ROUTES) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                restoreState = true
            }
        }
    }

    LaunchedEffect(deepLinkRoute) {
        deepLinkRoute?.let {
            DeepLinks.resolve(it)?.let { route ->
                try {
                    navController.navigate(route) { launchSingleTop = true }
                } catch (_: IllegalArgumentException) {
                    // Unknown route — silently ignore rather than crash
                }
            }
            onDeepLinkConsumed()
        }
    }

    val currentDest by navController.currentBackStackEntryAsState()
    val currentRoute = currentDest?.destination?.route
    val isRootScreen = currentRoute in ROOT_ROUTES

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun signOut() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try { ApiClient.service().registerFcmToken(com.foleyit.itflow.data.api.FcmTokenRequest("")) } catch (_: Exception) {}
                try { ApiClient.service().logout() } catch (_: Exception) {}
            }
            prefs.clearAuth()
            ApiClient.clearToken()
            onLoggedOut()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                userName = userName,
                userEmail = userEmail,
                hasUnreadNotifications = hasUnreadNotifications,
                isDarkMode = isDarkMode,
                onToggleDarkMode = { dark -> scope.launch { prefs.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) } },
                onNavigate = ::closeDrawerAndNavigate,
                onSignOut = { scope.launch { drawerState.close() }; signOut() },
                capabilities = capabilities,
            )
        }
    ) {
    Scaffold(
        // Detail screens own their system-bar insets through their inner Scaffold.
        // Applying the outer safe-drawing inset as well left a blank band above
        // their top app bars and reduced the usable height on a phone.
        contentWindowInsets = if (isRootScreen) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isRootScreen) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, "Menu")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("RivetIT", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                            Icon(Icons.Outlined.Search, "Search")
                        }
                        IconButton(onClick = { navController.navigate(Screen.Alerts.route) }) {
                            Icon(Icons.Outlined.Warning, "Alerts")
                        }
                        IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                            Box {
                                Icon(Icons.Outlined.Notifications, "Notifications")
                                if (hasUnreadNotifications) {
                                    UnreadDot(Modifier.align(Alignment.TopEnd))
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentRoute in BOTTOM_NAV_ROUTES) {
                val hierarchy = currentDest?.destination?.hierarchy
                FloatingBottomNavBar(
                    items = bottomNavItems.filter {
                        when (it.screen.route) {
                            Screen.Tickets.route, Screen.Appointments.route ->
                                capabilities.canView(com.foleyit.itflow.data.Capabilities.SUPPORT)
                            Screen.Clients.route -> capabilities.canView(com.foleyit.itflow.data.Capabilities.CLIENT)
                            Screen.Assets.route -> capabilities.canView(com.foleyit.itflow.data.Capabilities.ASSETS)
                            else -> true
                        }
                    },
                    isSelected = { item ->
                        hierarchy?.any { it.route == item.screen.route } == true || currentRoute == item.screen.route
                    },
                    onSelect = ::navigateToTab,
                )
            }
        }
    ) { padding ->
        val isOnline by rememberIsOnline()
        // On wide screens (tablets, landscape) cap the content width and centre it; phone-style single-column
        // lists and forms otherwise stretch edge to edge. Phones narrower than the cap are unaffected.
        Column(Modifier.padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!isOnline) {
            OfflineBanner()
        }
        androidx.compose.runtime.CompositionLocalProvider(
            com.foleyit.itflow.data.LocalCapabilities provides capabilities
        ) {
        NavHost(
            navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.weight(1f).widthIn(max = ContentMaxWidth).fillMaxWidth(),
            // Fade + subtle rise (design reference's springy screen-transition motion) applied
            // once here, at the NavHost level, so every one of the ~35 destinations below gets
            // it uniformly without each composable() call needing its own animation params.
            enterTransition = {
                fadeIn(animationSpec = Motion.medium()) +
                    slideInVertically(animationSpec = Motion.medium()) { it / 20 }
            },
            exitTransition = { fadeOut(animationSpec = Motion.fast()) },
            popEnterTransition = {
                fadeIn(animationSpec = Motion.medium()) +
                    slideInVertically(animationSpec = Motion.medium()) { -it / 20 }
            },
            popExitTransition = { fadeOut(animationSpec = Motion.fast()) },
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.Tickets.route) { TicketsScreen(navController) }
            composable(Screen.TicketDetail.route) {
                TicketDetailScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.TicketChat.route) {
                TicketChatScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.Clients.route) { ClientsScreen(navController) }
            composable(Screen.ClientDetail.route) {
                ClientDetailScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.Assets.route) { AssetsScreen(navController) }
            composable(Screen.AssetDetail.route) {
                AssetDetailScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.Projects.route) { ProjectsScreen(navController) }
            composable(Screen.ProjectDetail.route) {
                ProjectDetailScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.Contracts.route) { ContractsScreen(navController) }
            composable(Screen.ContractDetail.route) {
                ContractDetailScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.Appointments.route) { AppointmentsScreen(navController) }
            composable(Screen.Credentials.route) { RequireView(com.foleyit.itflow.data.Capabilities.CREDENTIAL, capabilities) { CredentialsScreen(navController) } }
            composable(Screen.CredDetail.route) {
                CredentialDetailScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen(onUnreadChanged = { hasUnreadNotifications = it })
            }
            composable(Screen.Alerts.route) { RequireView(com.foleyit.itflow.data.Capabilities.RMM_ALERTS, capabilities) { AlertsScreen(navController) } }
            composable(Screen.Profile.route) { ProfileScreen(navController, prefs, onChangeServer, onLoggedOut) }
            composable(Screen.KnowledgeBase.route) { RequireView(com.foleyit.itflow.data.Capabilities.KB, capabilities) { KnowledgeBaseScreen(navController) } }
            composable(Screen.KbArticleDetail.route) {
                KbArticleDetailScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.FillWorksheet.route) {
                FillWorksheetScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.OuttakeSign.route) {
                OuttakeSignScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0, navController)
            }
            composable(Screen.CreateTicket.route) { CreateTicketScreen(navController) }
            composable(Screen.Search.route) { SearchScreen(navController) }
            composable(Screen.TimeReport.route) { TimeSummaryScreen(navController) }
            composable(Screen.ReportsHub.route) { ReportsHubScreen(navController) }
            composable(Screen.TicketVolumeReport.route) { TicketVolumeReportScreen(navController) }
            composable(Screen.TicketsByClientReport.route) { TicketsByClientReportScreen(navController) }
            composable(Screen.TimeByTechReport.route) { TimeByTechReportScreen(navController) }
            composable(Screen.TechPerformanceReport.route) { TechPerformanceReportScreen(navController) }
            composable(Screen.OverviewReport.route) { OverviewReportScreen(navController) }
            composable(Screen.ExpiringReport.route) { ExpiringReportScreen(navController) }
            composable(Screen.CsatReport.route) { CsatReportScreen(navController) }
            composable(Screen.RmmHealthReport.route) { RmmHealthReportScreen(navController) }
            composable(Screen.ServiceDeskReport.route) { ServiceDeskReportScreen(navController) }
            composable(Screen.TechUtilizationReport.route) { TechnicianUtilizationReportScreen(navController) }
            composable(Screen.ScanBarcode.route) { ScanBarcodeScreen(navController) }
        }
        }
        }
    }
    }
}

/** Shows [content] only when the user may view [module]; otherwise a plain explanation (deep links can still reach the route). */
@Composable
private fun RequireView(
    module: String,
    capabilities: com.foleyit.itflow.data.Capabilities,
    content: @Composable () -> Unit,
) {
    if (capabilities.canView(module)) content()
    else com.foleyit.itflow.ui.components.EmptyScreen("You don't have access to this section", Icons.Outlined.Lock)
}
