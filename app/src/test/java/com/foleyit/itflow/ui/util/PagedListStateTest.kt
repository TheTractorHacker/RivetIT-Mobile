package com.foleyit.itflow.ui.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class FakeItem(val id: Int)
private data class FakePage(override val data: List<FakeItem>, override val total: Int) : PagedResponse<FakeItem>

@OptIn(ExperimentalCoroutinesApi::class)
class PagedListStateTest {

    @Test
    fun `refresh loads page 1 and populates state`() = runTest {
        val controller = PagedListController<FakeItem>(scope = this) { page, _ ->
            assertEquals(1, page)
            FakePage(data = listOf(FakeItem(1), FakeItem(2)), total = 5)
        }

        controller.refresh()
        advanceUntilIdle()

        val state = controller.state
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        assertEquals(2, state.items.size)
        assertEquals(5, state.total)
        assertTrue(state.hasMore)
    }

    @Test
    fun `onSearchChanged debounces rapid keystrokes into a single fetch`() = runTest {
        var fetchCount = 0
        val controller = PagedListController<FakeItem>(scope = this, debounceMs = 300) { _, query ->
            fetchCount++
            FakePage(data = listOf(FakeItem(1)), total = 1).also { assertEquals("abc", query) }
        }

        controller.onSearchChanged("a")
        advanceTimeBy(50)
        controller.onSearchChanged("ab")
        advanceTimeBy(50)
        controller.onSearchChanged("abc")
        advanceUntilIdle()

        assertEquals("only the final debounced query should have triggered a fetch", 1, fetchCount)
    }

    @Test
    fun `loadMore appends items and advances the page`() = runTest {
        var requestedPage = 0
        val controller = PagedListController<FakeItem>(scope = this) { page, _ ->
            requestedPage = page
            when (page) {
                1 -> FakePage(data = listOf(FakeItem(1), FakeItem(2)), total = 4)
                2 -> FakePage(data = listOf(FakeItem(3), FakeItem(4)), total = 4)
                else -> error("unexpected page $page")
            }
        }

        controller.refresh()
        advanceUntilIdle()
        assertTrue(controller.state.hasMore)

        controller.loadMore()
        advanceUntilIdle()

        assertEquals(2, requestedPage)
        assertEquals(listOf(1, 2, 3, 4), controller.state.items.map { it.id })
        assertFalse(controller.state.hasMore)
    }

    @Test
    fun `loadMore is a no-op once there is nothing more to load`() = runTest {
        var fetchCount = 0
        val controller = PagedListController<FakeItem>(scope = this) { _, _ ->
            fetchCount++
            FakePage(data = listOf(FakeItem(1)), total = 1)
        }

        controller.refresh()
        advanceUntilIdle()
        assertFalse(controller.state.hasMore)

        controller.loadMore()
        advanceUntilIdle()

        assertEquals("loadMore should not have issued a second fetch", 1, fetchCount)
    }

    @Test
    fun `failed refresh surfaces the error without crashing`() = runTest {
        val boom = RuntimeException("server exploded")
        val controller = PagedListController<FakeItem>(scope = this) { _, _ -> throw boom }

        controller.refresh()
        advanceUntilIdle()

        val state = controller.state
        assertFalse(state.isRefreshing)
        assertEquals(boom, state.error)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `retry re-issues the fetch and can recover from a prior failure`() = runTest {
        var shouldFail = true
        val controller = PagedListController<FakeItem>(scope = this) { _, _ ->
            if (shouldFail) throw RuntimeException("down") else FakePage(listOf(FakeItem(1)), 1)
        }

        controller.refresh()
        advanceUntilIdle()
        assertTrue(controller.state.error != null)

        shouldFail = false
        controller.retry()
        advanceUntilIdle()

        assertNull(controller.state.error)
        assertEquals(1, controller.state.items.size)
    }
}
