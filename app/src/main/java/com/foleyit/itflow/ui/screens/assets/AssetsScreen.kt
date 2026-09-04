package com.foleyit.itflow.ui.screens.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadMoreRow
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.pressScale
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.util.rememberPagedList
import com.foleyit.itflow.ui.util.userMessage

/** Fixed, user-facing type categories — the raw API type strings vary too much to show directly as chips. */
private val ASSET_CATEGORY_ORDER = listOf("Workstations", "Servers", "Network", "Printers", "Mobile", "Other")

/** Maps a raw asset type string (as returned by the API) to one of the fixed filter categories. */
private fun assetCategoryFor(rawType: String): String = when (rawType.trim().lowercase()) {
    "desktop", "laptop", "workstation" -> "Workstations"
    "server" -> "Servers"
    "switch", "router", "network", "firewall", "access point" -> "Network"
    "printer" -> "Printers"
    "phone", "mobile", "tablet" -> "Mobile"
    else -> "Other"
}

/** Small icon per raw asset type, used on the leading icon tile of each row. */
private fun assetTypeIcon(rawType: String?) = when (rawType?.trim()?.lowercase()) {
    "laptop" -> Icons.Outlined.LaptopMac
    "desktop" -> Icons.Outlined.DesktopWindows
    "server" -> Icons.Outlined.Dns
    "phone", "mobile" -> Icons.Outlined.PhoneAndroid
    "printer" -> Icons.Outlined.Print
    "network", "switch", "router" -> Icons.Outlined.Router
    else -> Icons.Outlined.Devices
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(navController: NavController) {
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var types by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        types = runCatching { ApiClient.service().getAssetTypes() }.getOrDefault(emptyList())
    }

    // Group the raw fetched types by category, then only surface a category chip when at least
    // one real type maps into it — never one chip per raw type.
    val typesByCategory = remember(types) { types.groupBy { assetCategoryFor(it) } }
    val availableCategories = remember(typesByCategory) {
        ASSET_CATEGORY_ORDER.filter { typesByCategory.containsKey(it) }
    }

    // The server's `type` filter only accepts a single raw type string, but a category can span
    // several of them — so fetch unfiltered (by search only) and filter client-side by category
    // for display. This never drops matching assets; loading more pages surfaces more of them.
    val list = rememberPagedList<com.foleyit.itflow.data.api.AssetSummary>(Unit) { page, q ->
        ApiClient.service().getAssets(search = q, page = page, type = "")
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.ScanBarcode.route) },
                modifier = Modifier.pressScale(0.90f),
            ) {
                Icon(Icons.Outlined.DocumentScanner, "Scan Barcode")
            }
        }
    ) { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(bottom = scaffoldPadding.calculateBottomPadding())) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; list.onSearchChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(48.dp),
                placeholder = { Text("Search assets…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = ""; list.onSearchChanged("") }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Clear, "Clear search", Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            if (availableCategories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All") }
                        )
                    }
                    items(availableCategories) { c ->
                        FilterChip(
                            selected = selectedCategory == c,
                            onClick = { selectedCategory = if (selectedCategory == c) null else c },
                            label = { Text(c) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            val ls = list.state
            val displayedItems = remember(ls.items, selectedCategory) {
                selectedCategory?.let { cat -> ls.items.filter { assetCategoryFor(it.type ?: "") == cat } }
                    ?: ls.items
            }
            when {
                ls.isRefreshing -> LoadingScreen()
                ls.error != null -> ErrorScreen(userMessage(ls.error), onRetry = list::retry)
                // Only treat this as a true dead end once every page has been fetched — a
                // category filter can legitimately have zero matches on the pages loaded so far
                // while more still exist further in the (unfiltered) paginated list; falling
                // through to `else` keeps the LoadMoreRow reachable so the user can page further
                // instead of getting stuck on a false "No assets found".
                displayedItems.isEmpty() && !ls.hasMore -> EmptyScreen("No assets found", Icons.Outlined.Devices)
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedItems, key = { it.id }) { a ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                onClick = { navController.navigate(Screen.AssetDetail.go(a.id)) }
                            ) {
                                ListItem(
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.secondaryContainer,
                                                    MaterialTheme.shapes.medium
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                assetTypeIcon(a.type),
                                                null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    },
                                    headlineContent = { Text(a.name, fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text(listOfNotNull(a.make, a.model).joinToString(" ")) },
                                    trailingContent = {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(a.client ?: "", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline)
                                            Icon(Icons.Outlined.ChevronRight, null)
                                        }
                                    }
                                )
                            }
                        }
                        if (ls.hasMore) {
                            item(key = "load_more") { LoadMoreRow(ls.isLoadingMore, list::loadMore) }
                        }
                    }
                }
            }
        }
    }
}
