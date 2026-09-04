package com.foleyit.itflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foleyit.itflow.ui.navigation.BottomNavItem
import com.foleyit.itflow.ui.navigation.Screen

/**
 * The new brand mark — a bold white "F" monogram built from 3 rounded rects, plus a small
 * secondary-color accent dot, replacing the old sync_alt Material icon everywhere it appeared
 * (top bar, auth screens, launcher icon). Drawn directly on a Canvas (not a static ImageVector)
 * so the accent dot can use the live theme's `secondary` role — it needs to track whichever
 * color seed is active, which a fixed-color vector asset couldn't do.
 */
@Composable
fun FoleyItLogoMark(
    modifier: Modifier = Modifier,
    markColor: Color = Color.White,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        fun px(v: Float) = v * s
        val strokeRadius = CornerRadius(px(1.5f), px(1.5f))
        // Vertical stroke of the F
        drawRoundRect(
            color = markColor,
            topLeft = Offset(px(6.5f), px(5f)),
            size = Size(px(3f), px(14f)),
            cornerRadius = strokeRadius,
        )
        // Top horizontal stroke
        drawRoundRect(
            color = markColor,
            topLeft = Offset(px(6.5f), px(5f)),
            size = Size(px(10.5f), px(3f)),
            cornerRadius = strokeRadius,
        )
        // Middle horizontal stroke
        drawRoundRect(
            color = markColor,
            topLeft = Offset(px(6.5f), px(10.5f)),
            size = Size(px(8f), px(3f)),
            cornerRadius = strokeRadius,
        )
        // Accent dot
        drawCircle(color = accentColor, radius = px(2.8f), center = Offset(px(18.5f), px(18.5f)))
    }
}

/**
 * The brand's asymmetric "squircle" corner treatment (`30% 30% 30% 10%`) — the signature shape
 * used on the logo tile everywhere it appears (top bar, auth screens, drawer header).
 */
val BrandTileShape: RoundedCornerShape = RoundedCornerShape(
    topStartPercent = 30, topEndPercent = 30, bottomEndPercent = 30, bottomStartPercent = 10,
)

/** Gradient brand tile (primary -> inversePrimary, 135deg) containing the [FoleyItLogoMark]. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(BrandTileShape)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // onPrimary, not a hardcoded white — this gradient is anchored in `primary`, and
        // onPrimary is the role already tuned to contrast against it in both light and dark mode
        // across all 5 seeds (dark-mode primaries in this app are deliberately light/bright, so a
        // fixed white mark would wash out there for several seeds).
        FoleyItLogoMark(
            modifier = Modifier.size(size * 0.62f),
            markColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Small red 8dp dot, top-right of an icon — the unread-notifications indicator. */
@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
    )
}

@Composable
private fun DrawerLeadingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, showDot: Boolean = false) {
    Box {
        Icon(icon, null)
        if (showDot) {
            UnreadDot(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp))
        }
    }
}

private val DrawerItemPadding = Modifier.padding(horizontal = 12.dp)

/**
 * Nav drawer content: header band (avatar + name + email — the app has no separate "role"
 * field on [com.foleyit.itflow.data.api.UserInfo], so email fills that line, same as the
 * account menu it replaces used to show), two grouped item lists, a divider, then Profile /
 * dark-mode row / Sign out pinned at the bottom. Opening a nav item is expected to close the
 * drawer (caller's [onNavigate] should do that); the dark-mode row deliberately does not.
 */
@Composable
fun AppDrawerContent(
    userName: String,
    userEmail: String,
    hasUnreadNotifications: Boolean,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.width(272.dp).fillMaxHeight()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    userName.ifBlank { "Account" }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1,
                )
                Text(
                    userEmail.ifBlank { "Signed in" }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1,
                )
            }
        }
        // Scrollable middle section — everything below (Profile/dark-mode/Sign out) must stay
        // pinned and visible even when this list alone is taller than the screen (header + 3 +
        // divider + 6 items comfortably exceeds a typical phone height once you add the pinned
        // section on top; without its own scroll, a plain Column would just clip the overflow
        // instead of scrolling it, silently hiding Sign out on shorter devices).
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                label = { Text("Home") }, icon = { DrawerLeadingIcon(Icons.Outlined.Dashboard) },
                selected = false, onClick = { onNavigate(Screen.Dashboard.route) }, modifier = DrawerItemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Search") }, icon = { DrawerLeadingIcon(Icons.Outlined.Search) },
                selected = false, onClick = { onNavigate(Screen.Search.route) }, modifier = DrawerItemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Notifications") },
                icon = { DrawerLeadingIcon(Icons.Outlined.Notifications, hasUnreadNotifications) },
                selected = false, onClick = { onNavigate(Screen.Notifications.route) }, modifier = DrawerItemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Alerts") }, icon = { DrawerLeadingIcon(Icons.Outlined.Warning) },
                selected = false, onClick = { onNavigate(Screen.Alerts.route) }, modifier = DrawerItemPadding,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

            NavigationDrawerItem(
                label = { Text("Reports") }, icon = { DrawerLeadingIcon(Icons.Outlined.Assessment) },
                selected = false, onClick = { onNavigate(Screen.ReportsHub.route) }, modifier = DrawerItemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Knowledge Base") }, icon = { DrawerLeadingIcon(Icons.AutoMirrored.Outlined.MenuBook) },
                selected = false, onClick = { onNavigate(Screen.KnowledgeBase.route) }, modifier = DrawerItemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Credentials") }, icon = { DrawerLeadingIcon(Icons.Outlined.Lock) },
                selected = false, onClick = { onNavigate(Screen.Credentials.route) }, modifier = DrawerItemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Quotes") }, icon = { DrawerLeadingIcon(Icons.Outlined.RequestQuote) },
                selected = false, onClick = { onNavigate(Screen.Quotes.route) }, modifier = DrawerItemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Invoices") }, icon = { DrawerLeadingIcon(Icons.AutoMirrored.Outlined.ReceiptLong) },
                selected = false, onClick = { onNavigate(Screen.Invoices.route) }, modifier = DrawerItemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Expenses") }, icon = { DrawerLeadingIcon(Icons.Outlined.Payments) },
                selected = false, onClick = { onNavigate(Screen.Expenses.route) }, modifier = DrawerItemPadding,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

        NavigationDrawerItem(
            label = { Text("Profile") }, icon = { DrawerLeadingIcon(Icons.Outlined.Person) },
            selected = false, onClick = { onNavigate(Screen.Profile.route) }, modifier = DrawerItemPadding,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onToggleDarkMode(!isDarkMode) }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (isDarkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, null)
            Spacer(Modifier.width(20.dp))
            Text("Dark mode", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = isDarkMode, onCheckedChange = onToggleDarkMode)
        }
        NavigationDrawerItem(
            label = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
            selected = false, onClick = onSignOut, modifier = DrawerItemPadding,
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Floating rounded bottom nav bar — replaces the M3 default tonal-pill `NavigationBar`
 * ("the pill is not it"). 64dp tall, inset 10dp from the sides/bottom, `surfaceContainerHigh`,
 * elevation level 2. Selected item gets a filled icon (unselected = outlined), a bold label,
 * and a small gradient underline bar instead of a background pill.
 */
@Composable
fun FloatingBottomNavBar(
    items: List<BottomNavItem>,
    isSelected: (BottomNavItem) -> Boolean,
    onSelect: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                FloatingNavItem(item, isSelected(item)) { onSelect(item.screen.route) }
            }
        }
    }
}

@Composable
private fun RowScope.FloatingNavItem(item: BottomNavItem, selected: Boolean, onClick: () -> Unit) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .pressScale()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) item.selectedIcon else item.icon, item.label,
            tint = contentColor, modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            item.label, style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(width = 16.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) {
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary)
                        )
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
        )
    }
}
