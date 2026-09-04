package com.foleyit.itflow.ui.screens.clients

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.*
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.util.BiometricCrypto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(id: Int, navController: NavController) {
    var clientState by remember { mutableStateOf<Result<ClientDetail>?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun loadClient() { scope.launch { clientState = runCatching { ApiClient.service().getClient(id) } } }
    LaunchedEffect(Unit) { loadClient() }

    val tabs = listOf("Info", "Tickets", "Contacts", "Assets", "Locations", "Credentials", "Contracts", "Files")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { clientState?.getOrNull()?.let { Text(it.name) } ?: Text("Client") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            clientState == null -> LoadingScreen()
            clientState!!.isFailure -> ErrorScreen(clientState!!.exceptionOrNull()?.message ?: "", onRetry = ::loadClient)
            else -> {
                val client = clientState!!.getOrThrow()
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // Client header
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(client.name.first().uppercaseChar().toString(),
                                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(client.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                client.city?.let { Text("$it${if (!client.state.isNullOrBlank()) ", ${client.state}" else ""}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                                Text("${client.openTickets} open", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    // Quick actions
                    if (client.phone != null || client.website != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            client.phone?.let {
                                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) },
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Outlined.Phone, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Call")
                                }
                            }
                            client.website?.let {
                                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Outlined.Language, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Website")
                                }
                            }
                        }
                    }
                    // Tab row
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        indicator = { tabPositions -> GradientTabIndicator(tabPositions, selectedTab) }
                    ) {
                        tabs.forEachIndexed { i, title ->
                            Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                        }
                    }
                    // Tab content
                    when (selectedTab) {
                        0 -> ClientInfoTab(client)
                        1 -> ClientTicketsTab(id, navController)
                        2 -> ClientContactsTab(client.contacts, context)
                        3 -> ClientAssetsTab(id, navController)
                        4 -> ClientLocationsTab(id, context)
                        5 -> ClientCredentialsTab(id, navController)
                        6 -> ClientContractsTab(id)
                        7 -> ClientFilesTab(id, navController)
                    }
                }
            }
        }
    }
}

/** Short rounded gradient bar under the selected tab, replacing the default full-width indicator. */
@Composable
private fun GradientTabIndicator(tabPositions: List<TabPosition>, selectedTabIndex: Int) {
    if (selectedTabIndex !in tabPositions.indices) return
    val indicatorWidth = 24.dp
    val targetOffset = tabPositions[selectedTabIndex].left + (tabPositions[selectedTabIndex].width - indicatorWidth) / 2
    val indicatorOffset by animateDpAsState(targetValue = targetOffset, label = "tabIndicatorOffset")
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = indicatorOffset)
            .width(indicatorWidth)
            .height(3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary)
                )
            )
    )
}

@Composable
private fun ClientInfoTab(client: ClientDetail) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (client.address != null || client.phone != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Contact Info", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        client.address?.let { InfoRow(Icons.Outlined.LocationOn, it) }
                        if (!client.city.isNullOrBlank())
                            InfoRow(Icons.Outlined.Place, "${client.city}, ${client.state ?: ""} ${client.zip ?: ""}")
                        client.phone?.let { InfoRow(Icons.Outlined.Phone, it) }
                        client.website?.let { InfoRow(Icons.Outlined.Language, it) }
                    }
                }
            }
        }
        if (!client.notes.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Notes", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(client.notes)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientTicketsTab(clientId: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<List<TicketSummary>>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { state = runCatching { ApiClient.service().getClientTickets(clientId) } } }
    LaunchedEffect(Unit) { load() }
    when {
        state == null -> LoadingScreen()
        state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
        else -> {
            val tickets = state!!.getOrThrow()
            if (tickets.isEmpty()) { EmptyScreen("No tickets", Icons.Outlined.ConfirmationNumber); return }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tickets) { t ->
                    TicketRow(t) { navController.navigate(Screen.TicketDetail.go(t.id)) }
                }
            }
        }
    }
}

@Composable
private fun TicketRow(t: TicketSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick, shape = MaterialTheme.shapes.large) {
        ListItem(
            headlineContent = { Text(t.subject, maxLines = 1) },
            supportingContent = { Text("#${t.number} · ${t.status ?: ""}") },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    if (t.resolvedAt != null) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )
    }
}

@Composable
private fun ClientContactsTab(contacts: List<Contact>, context: android.content.Context) {
    if (contacts.isEmpty()) { EmptyScreen("No contacts", Icons.Outlined.People); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(contacts) { c ->
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                ListItem(
                    headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Column {
                            c.title?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            c.email?.takeIf { it.isNotBlank() }?.let { email ->
                                Text(email, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                                    })
                            }
                        }
                    },
                    trailingContent = c.phone?.takeIf { it.isNotBlank() }?.let { phone -> {
                        IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }) {
                            Icon(Icons.Outlined.Phone, "Call")
                        }
                    }}
                )
            }
        }
    }
}

@Composable
private fun ClientAssetsTab(clientId: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<List<AssetSummary>>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { state = runCatching { ApiClient.service().getClientAssets(clientId) } } }
    LaunchedEffect(Unit) { load() }
    when {
        state == null -> LoadingScreen()
        state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
        else -> {
            val assets = state!!.getOrThrow()
            if (assets.isEmpty()) { EmptyScreen("No assets", Icons.Outlined.Devices); return }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(assets) { a ->
                    Card(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.AssetDetail.go(a.id)) }, shape = MaterialTheme.shapes.large) {
                        ListItem(
                            headlineContent = { Text(a.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(listOfNotNull(a.make, a.model).joinToString(" ")) },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    a.type?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline) }
                                    Icon(Icons.Outlined.ChevronRight, null)
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
private fun ClientLocationsTab(clientId: Int, context: android.content.Context) {
    var state by remember { mutableStateOf<Result<List<ClientLocation>>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { state = runCatching { ApiClient.service().getClientLocations(clientId) } } }
    LaunchedEffect(Unit) { load() }
    when {
        state == null -> LoadingScreen()
        state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
        else -> {
            val locs = state!!.getOrThrow()
            if (locs.isEmpty()) { EmptyScreen("No locations", Icons.Outlined.LocationOn); return }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(locs) { loc ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(loc.name ?: "Location", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                if (loc.primary) Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall) {
                                    Text("Primary", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            loc.address?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (!loc.city.isNullOrBlank()) Text("${loc.city}, ${loc.state ?: ""} ${loc.zip ?: ""}",
                                style = MaterialTheme.typography.bodySmall)
                            loc.phone?.let {
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) },
                                    contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Outlined.Phone, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientCredentialsTab(clientId: Int, navController: NavController) {
    var biometricPassed by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<Result<List<CredentialSummary>>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val localActivity = androidx.activity.compose.LocalActivity.current
    fun load() { scope.launch { state = runCatching { ApiClient.service().getClientCredentials(clientId) } } }

    // Biometric gate — must pass before fetching or showing any credentials
    if (!biometricPassed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Fingerprint, null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Authentication required to view credentials",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    val activity = localActivity as? androidx.fragment.app.FragmentActivity ?: return@Button
                    val crypto = try { BiometricCrypto.cryptoObject() } catch (_: Exception) { return@Button }
                    val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                    val prompt = androidx.biometric.BiometricPrompt(activity, executor,
                        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                if (BiometricCrypto.confirm(result)) {
                                    biometricPassed = true
                                    load()
                                }
                            }
                        })
                    prompt.authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Verify identity")
                        .setSubtitle("Access client credentials")
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .setNegativeButtonText("Cancel")
                        .build(),
                        crypto)
                }) {
                    Icon(Icons.Outlined.Fingerprint, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Authenticate")
                }
            }
        }
        return
    }
    // Only reaches here after biometric success
    when {
        state == null -> LoadingScreen()
        state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
        else -> {
            val creds = state!!.getOrThrow()
            if (creds.isEmpty()) { EmptyScreen("No credentials", Icons.Outlined.Lock); return }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(creds) { c ->
                    Card(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.CredDetail.go(c.id)) }, shape = MaterialTheme.shapes.large) {
                        ListItem(
                            headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                            supportingContent = c.uri?.takeIf { it.isNotBlank() }?.let { uri -> { Text(uri, maxLines = 1) } },
                            leadingContent = { Icon(Icons.Outlined.Lock, null,
                                tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientContractsTab(clientId: Int) {
    var state by remember { mutableStateOf<Result<List<ClientContract>>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { state = runCatching { ApiClient.service().getClientContracts(clientId) } } }
    LaunchedEffect(Unit) { load() }
    when {
        state == null -> LoadingScreen()
        state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
        else -> {
            val contracts = state!!.getOrThrow()
            if (contracts.isEmpty()) { EmptyScreen("No contracts", Icons.Outlined.Description); return }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(contracts) { c ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        ListItem(
                            headlineContent = { Text(c.name ?: "", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("${c.type ?: ""} · ${c.status ?: ""}") },
                            leadingContent = { Icon(Icons.Outlined.Description, null,
                                tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientFilesTab(clientId: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<List<ClientFile>>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { state = runCatching { ApiClient.service().getClientFiles(clientId) } } }
    LaunchedEffect(Unit) { load() }
    when {
        state == null -> LoadingScreen()
        state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
        else -> {
            val files = state!!.getOrThrow()
            if (files.isEmpty()) { EmptyScreen("No signed forms yet", Icons.Outlined.Description); return }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files) { f ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { navController.navigate(Screen.OuttakeSign.go(f.id)) },
                        shape = MaterialTheme.shapes.large
                    ) {
                        ListItem(
                            headlineContent = { Text("Outtake Form · #${f.ticketNumber}", fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Column {
                                    f.ticketSubject?.let { Text(it, maxLines = 1) }
                                    Text("Signed by ${f.signedName ?: "?"}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline)
                                }
                            },
                            leadingContent = { Icon(Icons.Outlined.Draw, null,
                                tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
