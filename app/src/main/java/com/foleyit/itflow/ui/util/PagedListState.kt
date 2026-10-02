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
    initialQuery: String = "",
    private val fetch: suspend (page: Int, search: String) -> PagedResponse<T>
) {
    var state by mutableStateOf(PagedListUiState<T>())
        private set

    private var query: String = initialQuery
    private var searchJob: Job? = null
    private var requestJob: Job? = null
    private var loadMoreJob: Job? = null
    private var requestVersion = 0

    /** Call from a search field's onValueChange; debounces and refreshes automatically. */
    fun onSearchChanged(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        requestJob?.cancel()
        loadMoreJob?.cancel()
        requestVersion++
        state = state.copy(isRefreshing = true, error = null)
        searchJob = scope.launch {
            delay(debounceMs)
            startRefresh()
        }
    }

    /** Reloads page 1 immediately (no debounce) — used for initial load, retry, and filter changes. */
    fun refresh() {
        searchJob?.cancel()
        startRefresh()
    }

    private fun startRefresh() {
        requestJob?.cancel()
        loadMoreJob?.cancel()
        val version = ++requestVersion
        state = state.copy(isRefreshing = true, error = null)
        requestJob = scope.launch {
            runCatching { fetch(1, query) }
                .onSuccess { resp ->
                    if (version == requestVersion) {
                        state = PagedListUiState(items = resp.data, page = 1, total = resp.total, isRefreshing = false)
                    }
                }
                .onFailure { e ->
                    if (version == requestVersion) state = state.copy(isRefreshing = false, error = e)
                }
        }
    }

    fun loadMore() {
        if (state.isLoadingMore || state.isRefreshing || !state.hasMore) return
        val nextPage = state.page + 1
        val version = requestVersion
        state = state.copy(isLoadingMore = true)
        loadMoreJob = scope.launch {
            runCatching { fetch(nextPage, query) }
                .onSuccess { resp ->
                    if (version == requestVersion) {
                        state = state.copy(
                            items = state.items + resp.data,
                            page = nextPage,
                            total = resp.total,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure {
                    // Keep existing items; user can trigger loadMore() again (e.g. scroll/tap retry).
                    if (version == requestVersion) state = state.copy(isLoadingMore = false)
                }
        }
    }

    fun retry() = refresh()

    fun cancel() {
        requestVersion++
        searchJob?.cancel()
        requestJob?.cancel()
        loadMoreJob?.cancel()
    }
}

/**
 * Creates (and recreates on [resetKeys] change, e.g. a tab or filter) a [PagedListController]
 * bound to this composable's lifecycle, and triggers its initial load.
 */
@Composable
fun <T> rememberPagedList(
    vararg resetKeys: Any?,
    debounceMs: Long = 300,
    initialQuery: String = "",
    fetch: suspend (page: Int, search: String) -> PagedResponse<T>
): PagedListController<T> {
    val scope = rememberCoroutineScope()
    val controller = remember(*resetKeys) { PagedListController(scope, debounceMs, initialQuery, fetch) }
    LaunchedEffect(*resetKeys) { controller.refresh() }
    androidx.compose.runtime.DisposableEffect(controller) { onDispose { controller.cancel() } }
    return controller
}
