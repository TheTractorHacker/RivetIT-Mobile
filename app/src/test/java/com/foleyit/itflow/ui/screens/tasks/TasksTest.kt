package com.foleyit.itflow.ui.screens.tasks

import com.foleyit.itflow.*
import com.foleyit.itflow.data.model.*
import com.foleyit.itflow.data.repo.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

class TasksTest {
    @get:Rule val main = MainDispatcherRule()

    private val now = LocalDateTime.of(2026, 10, 6, 12, 0, 0)

    @Test fun `due chips`() {
        fun chip(due: String?, status: TaskStatus = TaskStatus.PENDING) = TasksLogic.dueChip(task(1, status = status, due = due), now)
        assertEquals(DueChip.OVERDUE, chip("2026-10-06 11:59:59"))
        assertEquals(DueChip.DUE_SOON, chip("2026-10-06 12:00:00"))
        assertEquals(DueChip.DUE_SOON, chip("2026-10-08 12:00:00"))
        assertEquals(DueChip.NONE, chip("2026-10-08 12:00:01"))
        assertEquals(DueChip.NONE, chip(null))
        assertEquals(DueChip.NONE, chip("garbage"))
        assertEquals(DueChip.NONE, chip("2026-10-01 00:00:00", TaskStatus.COMPLETED))
        assertEquals(DueChip.NONE, chip("2026-10-01 00:00:00", TaskStatus.SKIPPED))
        assertEquals(DueChip.OVERDUE, chip("2026-10-01 00:00:00", TaskStatus.BLOCKED))
        // A date-only value is due at the end of that day.
        assertEquals(DueChip.DUE_SOON, chip("2026-10-06"))
        assertEquals(DueChip.OVERDUE, chip("2026-10-05"))
    }

    @Test fun `grouping by run keeps order`() {
        val groups = TasksLogic.group(listOf(task(1, run = 2), task(2, run = 1), task(3, run = 2)))
        assertEquals(listOf(2, 1), groups.map { it.runId })
        assertEquals(listOf(1, 3), groups[0].tasks.map { it.id })
        assertTrue(TasksLogic.group(emptyList()).isEmpty())
    }

    @Test fun `only unblocked manual open tasks can be acted on`() {
        assertTrue(TasksLogic.canAct(task(1)))
        assertFalse(TasksLogic.canAct(task(1, status = TaskStatus.BLOCKED)))
        assertFalse(TasksLogic.canAct(task(1, type = TaskType.APPROVAL)))
        assertFalse(TasksLogic.canAct(task(1, type = TaskType.ACTION)))
        assertFalse(TasksLogic.canAct(task(1, status = TaskStatus.COMPLETED)))
        assertFalse(TasksLogic.canAct(task(1, status = TaskStatus.ACTION_FAILED, type = TaskType.ACTION)))
        assertFalse(TasksLogic.canAct(task(1, status = TaskStatus.RUNNING)))
        assertFalse(TasksLogic.canAct(task(1, status = TaskStatus.REJECTED)))
        assertFalse(TasksLogic.canAct(task(1, status = TaskStatus.UNKNOWN)))
        assertTrue(TasksLogic.skipReasonValid("x")); assertFalse(TasksLogic.skipReasonValid("  "))
    }

    private class FakeRepo : TasksRepository {
        var listResult: () -> TasksResult = { TasksResult(emptyList(), 0, 0) }
        var actResult: suspend () -> DecisionResult = { DecisionResult(true, "completed") }
        val calls = mutableListOf<String>()
        override suspend fun list() = listResult()
        override suspend fun complete(id: Int): DecisionResult { calls += "complete:$id"; return actResult() }
        override suspend fun skip(id: Int, reason: String): DecisionResult { calls += "skip:$id:$reason"; return actResult() }
    }

    private fun vm(repo: FakeRepo, cache: FeatureCache = FeatureCache(), badges: FeatureBadges = FeatureBadges()) = TasksViewModel(repo, cache, badges)

    @Test fun `loads, badge shows overdue count`() {
        val repo = FakeRepo().apply { listResult = { TasksResult(listOf(task(1), task(2)), 2, 1) } }
        val badges = FeatureBadges()
        val v = vm(repo, badges = badges)
        assertEquals(2, v.state.value.items.size); assertEquals(1, badges.overdueTasks.value)
    }

    @Test fun `empty, error, retry and cached states`() {
        val repo = FakeRepo()
        val v = vm(repo)
        assertTrue(v.state.value.items.isEmpty()); assertNotNull(v.state.value.load.data)

        repo.listResult = { throw IOException() }
        val failing = vm(repo)
        assertTrue(failing.state.value.load.isInitialError)

        val cached = FeatureCache().apply { tasks = TasksResult(listOf(task(9)), 1, 0) }
        val v2 = vm(repo, cached)
        assertEquals(1, v2.state.value.items.size)
        assertNotNull(v2.state.value.load.error); assertTrue(v2.state.value.load.fromCache)
    }

    @Test fun `complete calls the repo, emits Completed and refreshes`() = runTest {
        val repo = FakeRepo()
        repo.listResult = { TasksResult(listOf(task(1)), 1, 0) }
        val v = vm(repo)
        repo.listResult = { TasksResult(emptyList(), 0, 0) }
        v.complete(task(1))
        assertEquals(listOf("complete:1"), repo.calls)
        assertEquals(TaskEvent.Completed("completed"), v.events.first())
        assertTrue(v.state.value.items.isEmpty()); assertTrue(v.state.value.acting.isEmpty())
    }

    @Test fun `blocked approval and action tasks are never sent`() {
        val repo = FakeRepo()
        val v = vm(repo)
        v.complete(task(1, status = TaskStatus.BLOCKED)); v.complete(task(2, type = TaskType.APPROVAL)); v.complete(task(3, type = TaskType.ACTION))
        v.skip(task(1, status = TaskStatus.BLOCKED), "why")
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun `skip needs a reason`() = runTest {
        val repo = FakeRepo()
        val v = vm(repo)
        v.skip(task(1), "  ")
        assertTrue(repo.calls.isEmpty())
        assertEquals(TaskEvent.ReasonRequired, v.events.first())
    }

    @Test fun `skip sends the trimmed reason`() = runTest {
        val repo = FakeRepo().apply { actResult = { DecisionResult(true, "skipped") } }
        val v = vm(repo)
        v.skip(task(1), "  not needed ")
        assertEquals(listOf("skip:1:not needed"), repo.calls)
        assertEquals(TaskEvent.Skipped("skipped"), v.events.first())
    }

    @Test fun `double tap while in flight is ignored`() {
        val gate = CompletableDeferred<DecisionResult>()
        val repo = FakeRepo().apply { actResult = { gate.await() } }
        val v = vm(repo)
        v.complete(task(1)); v.complete(task(1))
        assertEquals(1, repo.calls.size); assertEquals(setOf(1), v.state.value.acting)
        gate.complete(DecisionResult(true, null))
        assertTrue(v.state.value.acting.isEmpty())
    }

    @Test fun `server refusal is reported with the task kept`() = runTest {
        val repo = FakeRepo().apply { actResult = { throw httpError(422) } }
        repo.listResult = { TasksResult(listOf(task(1)), 1, 0) }
        val v = vm(repo)
        v.complete(task(1))
        assertTrue(v.events.first() is TaskEvent.Failed)
        assertEquals(1, v.state.value.items.size)
    }

    @Test fun `409 means the task changed`() = runTest {
        val repo = FakeRepo().apply { actResult = { throw httpError(409) } }
        val v = vm(repo)
        v.complete(task(1))
        assertEquals(TaskEvent.Changed, v.events.first())
    }
}
