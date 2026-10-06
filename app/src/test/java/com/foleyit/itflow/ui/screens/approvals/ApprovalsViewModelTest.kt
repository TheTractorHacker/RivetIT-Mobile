package com.foleyit.itflow.ui.screens.approvals

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

class ApprovalsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeRepo : ApprovalsRepository {
        var listResult: () -> ApprovalsResult = { ApprovalsResult(emptyList(), 0) }
        var decideResult: suspend () -> DecisionResult = { DecisionResult(true, "approved") }
        val decisions = mutableListOf<List<Any>>()
        var listCalls = 0
        override suspend fun list(): ApprovalsResult { listCalls++; return listResult() }
        override suspend fun decide(kind: ApprovalKind, id: Int, approve: Boolean, comment: String): DecisionResult {
            decisions += listOf(kind, id, approve, comment)
            return decideResult()
        }
    }

    private fun vm(repo: FakeRepo, cache: FeatureCache = FeatureCache(), badges: FeatureBadges = FeatureBadges()) =
        ApprovalsViewModel(repo, cache, badges)

    @Test fun `loads items, sets badge and cache`() {
        val repo = FakeRepo().apply { listResult = { ApprovalsResult(listOf(approval(id = 1), approval(id = 2)), 2) } }
        val cache = FeatureCache(); val badges = FeatureBadges()
        val v = vm(repo, cache, badges)
        val s = v.state.value
        assertEquals(2, s.items.size)
        assertFalse(s.load.refreshing); assertNull(s.load.error); assertFalse(s.load.fromCache)
        assertEquals(2, badges.approvals.value)
        assertNotNull(cache.approvals)
    }

    @Test fun `empty result is data with no items, not an error`() {
        val v = vm(FakeRepo())
        assertNotNull(v.state.value.load.data)
        assertTrue(v.state.value.items.isEmpty())
        assertFalse(v.state.value.load.isInitialError)
    }

    @Test fun `first load failure is an initial error and retry recovers`() {
        val repo = FakeRepo().apply { listResult = { throw IOException("down") } }
        val v = vm(repo)
        assertTrue(v.state.value.load.isInitialError)
        repo.listResult = { ApprovalsResult(listOf(approval(id = 1)), 1) }
        v.refresh()
        assertFalse(v.state.value.load.isInitialError)
        assertEquals(1, v.state.value.items.size)
    }

    @Test fun `cached result shows first and is kept when refresh fails`() {
        val cache = FeatureCache().apply { approvals = ApprovalsResult(listOf(approval(id = 5)), 1) }
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRepo()
        var fail = true
        repo.listResult = { if (fail) throw IOException("offline") else ApprovalsResult(emptyList(), 0) }
        val v = vm(repo, cache)
        val s = v.state.value
        // The refresh already failed (eager dispatcher): cached list stays, error surfaces as a banner.
        assertEquals(1, s.items.size)
        assertNotNull(s.load.error); assertTrue(s.load.fromCache); assertFalse(s.load.isInitialError)
        fail = false
        v.refresh()
        assertTrue(v.state.value.items.isEmpty()); assertFalse(v.state.value.load.fromCache); assertNull(v.state.value.load.error)
        gate.complete(Unit)
    }

    @Test fun `approve removes the item, updates badge, emits Decided and refreshes`() = runTest {
        val repo = FakeRepo()
        repo.listResult = { ApprovalsResult(listOf(approval(id = 1), approval(id = 2)), 2) }
        val badges = FeatureBadges()
        val v = vm(repo, badges = badges)
        repo.listResult = { ApprovalsResult(listOf(approval(id = 2)), 1) }
        v.decide(approval(id = 1), approve = true, comment = "ok")
        assertEquals(listOf<Any>(ApprovalKind.CATALOG_REQUEST, 1, true, "ok"), repo.decisions.single())
        assertEquals(listOf(2), v.state.value.items.map { it.id })
        assertEquals(1, badges.approvals.value)
        assertEquals(ApprovalEvent.Decided(true, "approved"), v.events.first())
        assertTrue(v.state.value.deciding.isEmpty())
        assertEquals(2, repo.listCalls) // initial load + refresh after decision
    }

    @Test fun `reject without a comment sends nothing`() = runTest {
        val repo = FakeRepo()
        val v = vm(repo)
        v.decide(approval(id = 1), approve = false, comment = "   ")
        assertTrue(repo.decisions.isEmpty())
        assertEquals(ApprovalEvent.CommentRequired, v.events.first())
        assertTrue(v.state.value.deciding.isEmpty())
    }

    @Test fun `reject with a comment sends it trimmed`() = runTest {
        val repo = FakeRepo().apply { decideResult = { DecisionResult(true, "rejected") } }
        val v = vm(repo)
        v.decide(approval(ApprovalKind.WORKFLOW_TASK, 4), approve = false, comment = "  too risky ")
        assertEquals(listOf<Any>(ApprovalKind.WORKFLOW_TASK, 4, false, "too risky"), repo.decisions.single())
        assertEquals(ApprovalEvent.Decided(false, "rejected"), v.events.first())
    }

    @Test fun `buttons stay disabled while a decision is in flight and a second tap is ignored`() {
        val gate = CompletableDeferred<DecisionResult>()
        val repo = FakeRepo().apply { decideResult = { gate.await() } }
        val v = vm(repo)
        val item = approval(id = 1)
        v.decide(item, true, "")
        assertEquals(setOf(item.key), v.state.value.deciding)
        v.decide(item, true, "")
        assertEquals(1, repo.decisions.size)
        gate.complete(DecisionResult(true, "approved"))
        assertTrue(v.state.value.deciding.isEmpty())
    }

    @Test fun `same id of the other kind is a different item`() {
        val gate = CompletableDeferred<DecisionResult>()
        val repo = FakeRepo().apply { decideResult = { gate.await() } }
        val v = vm(repo)
        v.decide(approval(ApprovalKind.CATALOG_REQUEST, 1), true, "")
        v.decide(approval(ApprovalKind.WORKFLOW_TASK, 1), true, "")
        assertEquals(2, repo.decisions.size)
        gate.complete(DecisionResult(true, null))
    }

    @Test fun `failure keeps the item and reports it`() = runTest {
        val repo = FakeRepo()
        repo.listResult = { ApprovalsResult(listOf(approval(id = 1)), 1) }
        repo.decideResult = { throw httpError(403) }
        val v = vm(repo)
        v.decide(approval(id = 1), true, "")
        assertEquals(1, v.state.value.items.size)
        assertTrue(v.events.first() is ApprovalEvent.Failed)
        assertTrue(v.state.value.deciding.isEmpty())
    }

    @Test fun `404 means no longer waiting - removed and refreshed`() = runTest {
        val repo = FakeRepo()
        repo.listResult = { ApprovalsResult(listOf(approval(id = 1)), 1) }
        repo.decideResult = { throw httpError(404) }
        val v = vm(repo)
        repo.listResult = { ApprovalsResult(emptyList(), 0) }
        v.decide(approval(id = 1), true, "")
        assertEquals(ApprovalEvent.NoLongerWaiting, v.events.first())
        assertTrue(v.state.value.items.isEmpty())
    }

    @Test fun `ok false from the server is a failure`() = runTest {
        val repo = FakeRepo().apply { decideResult = { DecisionResult(false, null) } }
        val v = vm(repo)
        v.decide(approval(id = 1), true, "")
        assertTrue(v.events.first() is ApprovalEvent.Failed)
    }

    @Test fun `risk bands`() {
        assertEquals(RiskBand.LOW, ApprovalsLogic.riskBand(0)); assertEquals(RiskBand.LOW, ApprovalsLogic.riskBand(39))
        assertEquals(RiskBand.MEDIUM, ApprovalsLogic.riskBand(40)); assertEquals(RiskBand.MEDIUM, ApprovalsLogic.riskBand(69))
        assertEquals(RiskBand.HIGH, ApprovalsLogic.riskBand(70)); assertEquals(RiskBand.HIGH, ApprovalsLogic.riskBand(100))
        assertEquals("catalog_request:3", ApprovalsLogic.key("catalog_request", 3))
        assertNull(ApprovalsLogic.key(null, 3)); assertNull(ApprovalsLogic.key("x", 0))
    }
}
