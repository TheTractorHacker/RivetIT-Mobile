package com.foleyit.itflow.ui.screens.requests

import com.foleyit.itflow.*
import com.foleyit.itflow.data.model.*
import com.foleyit.itflow.data.repo.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ServiceCatalogViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun field(key: String, type: FieldType = FieldType.TEXT, required: Boolean = false, rule: ShowIfRule? = null, options: List<String> = emptyList()) =
        CatalogField(key, key, type, options, required, "", rule)

    private fun item(id: Int, name: String, fields: List<CatalogField> = emptyList(), desc: String = "") =
        CatalogItem(id, name, desc, "", false, 0, fields)

    private class FakeRepo : CatalogRepository {
        var loadResult: () -> CatalogResult = { CatalogResult(emptyList(), emptyList(), emptyList()) }
        var submitResult: suspend () -> SubmitResult = { SubmitResult(1, RequestStatus.CREATED) }
        val submitted = mutableListOf<Triple<Int, Map<String, String>, Int?>>()
        override suspend fun load() = loadResult()
        override suspend fun submit(itemId: Int, answers: Map<String, String>, clientId: Int?): SubmitResult {
            submitted += Triple(itemId, answers, clientId); return submitResult()
        }
    }

    private val device = item(
        1, "Laptop", fields = listOf(
            field("kind", FieldType.SELECT, required = true, options = listOf("Laptop", "Phone")),
            field("serial", required = true, rule = ShowIfRule.Equals("kind", "Laptop")),
            field("note"),
        )
    )

    private fun vm(repo: FakeRepo, cache: FeatureCache = FeatureCache()) = ServiceCatalogViewModel(repo, cache)

    @Test fun `loading, empty, error and cached`() {
        val repo = FakeRepo()
        assertNotNull(vm(repo).state.value.load.data)
        repo.loadResult = { throw IOException() }
        assertTrue(vm(repo).state.value.load.isInitialError)
        val cache = FeatureCache().apply { catalog = CatalogResult(listOf(device), emptyList(), emptyList()) }
        val v = vm(repo, cache)
        assertEquals(1, v.state.value.load.data!!.items.size); assertTrue(v.state.value.load.fromCache); assertNotNull(v.state.value.load.error)
    }

    @Test fun `shelves and search`() {
        val items = listOf(item(1, "VPN access", desc = "Remote"), item(2, "Laptop", desc = "New hardware"), item(3, "Badge"))
        val r = CatalogResult(items, popular = listOf(2, 99, 2), recent = listOf(3, 1))
        val s = CatalogLogic.shelves(r, "")
        assertEquals(listOf(2), s.popular.map { it.id })
        assertEquals(listOf(3, 1), s.recent.map { it.id })
        assertEquals(listOf("Badge", "Laptop", "VPN access"), s.all.map { it.name })
        assertFalse(s.searching)
        val q = CatalogLogic.shelves(r, " hardware ")
        assertTrue(q.searching); assertTrue(q.popular.isEmpty()); assertTrue(q.recent.isEmpty())
        assertEquals(listOf("Laptop"), q.all.map { it.name })
        assertEquals(listOf("VPN access"), CatalogLogic.shelves(r, "VPN").all.map { it.name })
        assertTrue(CatalogLogic.shelves(r, "zzz").all.isEmpty())
    }

    @Test fun `icons map`() {
        assertEquals(CatalogIcon.LAPTOP, catalogIconFor("fa-laptop"))
        assertEquals(CatalogIcon.LOCK, catalogIconFor("bi-key-fill"))
        assertEquals(CatalogIcon.PERSON, catalogIconFor("USER-plus"))
        assertEquals(CatalogIcon.DEFAULT, catalogIconFor(""))
        assertEquals(CatalogIcon.DEFAULT, catalogIconFor("zzz"))
    }

    @Test fun `hidden field answers are cleared when the controlling answer changes`() {
        val v = vm(FakeRepo())
        v.open(device)
        v.setAnswer("kind", "Laptop"); v.setAnswer("serial", "SN1"); v.setAnswer("note", "n")
        assertEquals(mapOf("kind" to "Laptop", "serial" to "SN1", "note" to "n"), v.state.value.answers)
        v.setAnswer("kind", "Phone")
        assertEquals(mapOf("kind" to "Phone", "note" to "n"), v.state.value.answers) // serial cleared
        v.setAnswer("kind", "Laptop")
        assertNull(v.state.value.answers["serial"]) // and it does not come back by itself
    }

    @Test fun `submit validates first and sends nothing when invalid`() {
        val repo = FakeRepo()
        val v = vm(repo)
        v.open(device)
        v.submit()
        assertTrue(repo.submitted.isEmpty())
        assertEquals(FieldError.Required, v.state.value.errors["kind"])
        assertFalse(v.state.value.errors.containsKey("serial")) // hidden, so not required
        v.setAnswer("kind", "Laptop")
        assertFalse(v.state.value.errors.containsKey("kind")) // typing clears that field's error
        v.submit()
        assertEquals(FieldError.Required, v.state.value.errors["serial"])
        assertTrue(repo.submitted.isEmpty())
    }

    @Test fun `submit sends visible answers only and shows the created result`() {
        val repo = FakeRepo().apply { submitResult = { SubmitResult(55, RequestStatus.PENDING_APPROVAL) } }
        val v = vm(repo)
        v.open(device)
        v.setAnswer("kind", "Laptop"); v.setAnswer("serial", "SN1")
        v.setAnswer("kind", "Phone")
        v.submit()
        assertEquals(1, repo.submitted.size)
        assertEquals(1, repo.submitted[0].first)
        assertEquals(mapOf("kind" to "Phone"), repo.submitted[0].second)
        assertNull(repo.submitted[0].third)
        val stage = v.state.value.stage as RequestStage.Done
        assertEquals(55, stage.result.ticketId); assertEquals(RequestStatus.PENDING_APPROVAL, stage.result.status)
        assertFalse(v.state.value.submitting)
    }

    @Test fun `failure stays on the form with the answers kept, and can be retried`() {
        val repo = FakeRepo().apply { submitResult = { throw httpError(422, """{"error":"Bad"}""") } }
        val v = vm(repo)
        v.open(device)
        v.setAnswer("kind", "Phone")
        v.submit()
        assertTrue(v.state.value.stage is RequestStage.Form)
        assertNotNull(v.state.value.submitError); assertEquals("Phone", v.state.value.answers["kind"]); assertFalse(v.state.value.submitting)
        repo.submitResult = { SubmitResult(2, RequestStatus.CREATED) }
        v.submit()
        assertTrue(v.state.value.stage is RequestStage.Done)
    }

    @Test fun `a second tap while submitting is ignored`() {
        val gate = CompletableDeferred<SubmitResult>()
        val repo = FakeRepo().apply { submitResult = { gate.await() } }
        val v = vm(repo)
        v.open(device); v.setAnswer("kind", "Phone")
        v.submit(); v.submit()
        assertEquals(1, repo.submitted.size); assertTrue(v.state.value.submitting)
        gate.complete(SubmitResult(1, RequestStatus.CREATED))
        assertFalse(v.state.value.submitting)
    }

    @Test fun `back returns to the catalog and drops the draft`() {
        val v = vm(FakeRepo())
        v.open(device); v.setAnswer("kind", "Phone")
        v.backToCatalog()
        assertEquals(RequestStage.Catalog, v.state.value.stage); assertTrue(v.state.value.answers.isEmpty())
    }
}
