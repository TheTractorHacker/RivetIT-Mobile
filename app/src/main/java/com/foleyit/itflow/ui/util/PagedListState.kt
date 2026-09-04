package com.foleyit.itflow.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Marker interface for the existing `data class XResponse(val data: List<T>, val total: Int)` API models. */
interface PagedResponse<T> {
    val data: List<T>
    val total: Int
}

data class PagedListUiState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val isRefreshing: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null
) {
    val hasMore: Boolean get() = items.size < total
}

/**
 * Shared searchable/paged list state for screens whose API already supports `page`/`total`
 * (Tickets, Clients, Assets, Credentials, Quotes, Invoices, Expenses, KB articles) — replaces
 * per-screen, inconsistently-implemented search debounce and pagination with one correct copy.
 */
class PagedListController<T>(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300,
    private val fetch: suspend (page: Int, search: String) -> PagedResponse<T>
) {
    var state by mutableStateOf(PagedListUiState<T>())
        private set

    private var query: String = ""
    private var searchJob: Job? = null

    /** Call from a search field's onValueChange; debounces and refreshes automatically. */
    fun onSearchChanged(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(debounceMs)
            refresh()
        }
    }

    /** Reloads page 1 immediately (no debounce) — used for initial load, retry, and filter changes. */
    fun refresh() {
        searchJob?.cancel()
        state = state.copy(isRefreshing = true, error = null)
        scope.launch {
            runCatching { fetch(1, query) }
                .onSuccess { resp ->
                    state = PagedListUiState(items = resp.data, page = 1, total = resp.total, isRefreshing = false)
                }
                .onFailure { e ->
                    state = state.copy(isRefreshing = false, error = e)
                }
        }
    }

    fun loadMore() {
        if (state.isLoadingMore || state.isRefreshing || !state.hasMore) return
        val nextPage = state.page + 1
        state = state.copy(isLoadingMore = true)
        scope.launch {
            runCatching { fetch(nextPage, query) }
                .onSuccess { resp ->
                    state = state.copy(
                        items = state.items + resp.data,
                        page = nextPage,
                        total = resp.total,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    // Keep existing items; user can trigger loadMore() again (e.g. scroll/tap retry).
                    state = state.copy(isLoadingMore = false)
                }
        }
    }

    fun retry() = refresh()
}

/**
 * Creates (and recreates on [resetKeys] change, e.g. a tab or filter) a [PagedListController]
 * bound to this composable's lifecycle, and triggers its initial load.
 */
@Composable
fun <T> rememberPagedList(
    vararg resetKeys: Any?,
    debounceMs: Long = 300,
    fetch: suspend (page: Int, search: String) -> PagedResponse<T>
): PagedListController<T> {
    val scope = rememberCoroutineScope()
    val controller = remember(*resetKeys) { PagedListController(scope, debounceMs, fetch) }
    LaunchedEffect(*resetKeys) { controller.refresh() }
    return controller
}
