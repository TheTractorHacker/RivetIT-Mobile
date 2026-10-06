package com.foleyit.itflow.ui.screens.requests

import com.foleyit.itflow.data.model.CatalogItem
import com.foleyit.itflow.data.model.CatalogResult

/** What the catalog screen lists for the current search text. */
data class CatalogShelves(
    val popular: List<CatalogItem>,
    val recent: List<CatalogItem>,
    val all: List<CatalogItem>,
    val searching: Boolean,
)

object CatalogLogic {
    /**
     * With no search text: the Popular and Recently-used shelves (ids the server sent, skipping any that are not in the
     * catalog) plus every item by name. With search text: just the matching items (name or description), no shelves.
     */
    fun shelves(result: CatalogResult, query: String): CatalogShelves {
        val byId = result.items.associateBy { it.id }
        val q = query.trim()
        if (q.isNotEmpty()) {
            val hits = result.items
                .filter { it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
            return CatalogShelves(emptyList(), emptyList(), hits, searching = true)
        }
        return CatalogShelves(
            popular = result.popular.distinct().mapNotNull { byId[it] },
            recent = result.recent.distinct().mapNotNull { byId[it] },
            all = result.items.sortedBy { it.name.lowercase() },
            searching = false,
        )
    }
}

enum class CatalogIcon { LAPTOP, PERSON, LOCK, NETWORK, PRINTER, PHONE, MAIL, APPS, SECURITY, CLOUD, STORAGE, DEFAULT }

/** Maps the server's free-form icon name (e.g. `fa-laptop`, `laptop`, `bx-user`) to a small set of app icons. */
fun catalogIconFor(name: String): CatalogIcon {
    val n = name.lowercase()
    return when {
        listOf("laptop", "computer", "desktop", "pc", "monitor").any { n.contains(it) } -> CatalogIcon.LAPTOP
        listOf("user", "person", "account", "people", "id-card", "badge").any { n.contains(it) } -> CatalogIcon.PERSON
        listOf("key", "lock", "password", "unlock").any { n.contains(it) } -> CatalogIcon.LOCK
        listOf("wifi", "network", "ethernet", "globe", "router", "vpn").any { n.contains(it) } -> CatalogIcon.NETWORK
        n.contains("print") -> CatalogIcon.PRINTER
        listOf("phone", "mobile", "tablet", "cell").any { n.contains(it) } -> CatalogIcon.PHONE
        listOf("mail", "envelope", "email", "inbox").any { n.contains(it) } -> CatalogIcon.MAIL
        listOf("software", "app", "download", "install", "window", "puzzle").any { n.contains(it) } -> CatalogIcon.APPS
        listOf("shield", "security", "virus", "bug").any { n.contains(it) } -> CatalogIcon.SECURITY
        n.contains("cloud") -> CatalogIcon.CLOUD
        listOf("database", "server", "folder", "drive", "storage", "hdd").any { n.contains(it) } -> CatalogIcon.STORAGE
        else -> CatalogIcon.DEFAULT
    }
}
