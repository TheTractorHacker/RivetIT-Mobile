package com.foleyit.itflow.ui.util

/**
 * Load state shared by the approvals, catalog and task lists.
 *
 * - [data] null and [refreshing]: first load (spinner). [data] null and [error]: error screen with Retry.
 * - [data] present: list. [fromCache] means it is the last known result, shown while a refresh runs or
 *   after one failed; [error] then becomes a retry banner above the stale list.
 */
data class ListLoad<T>(
    val data: T? = null,
    val refreshing: Boolean = false,
    val error: Throwable? = null,
    val fromCache: Boolean = false,
) {
    val isInitialLoading: Boolean get() = data == null && error == null
    val isInitialError: Boolean get() = data == null && error != null

    fun started(): ListLoad<T> = copy(refreshing = true, error = null)
    fun succeeded(value: T): ListLoad<T> = ListLoad(value, refreshing = false, error = null, fromCache = false)
    fun failed(e: Throwable): ListLoad<T> = copy(refreshing = false, error = e)
    fun withData(value: T): ListLoad<T> = copy(data = value)

    companion object {
        fun <T> initial(cached: T?): ListLoad<T> = ListLoad(cached, refreshing = true, fromCache = cached != null)
    }
}
