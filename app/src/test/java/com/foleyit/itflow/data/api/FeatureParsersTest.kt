package com.foleyit.itflow.data.api

import com.foleyit.itflow.data.model.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class FeatureParsersTest {
    private fun obj(s: String) = JsonParser.parseString(s).asJsonObject

    // ── approvals ──

    @Test fun `approvals full shape with unknown fields`() {
        val r = FeatureParsers.approvals(obj("""
          {"items":[{"kind":"catalog_request","id":7,"title":"New laptop","requester":"Jane","summary":"Needs one",
            "risk_score":55,"step":"Manager","ticket_id":321,"requested_at":"2026-10-05 09:30:00","due_at":"2026-10-08 17:00:00",
            "fields":[{"label":"Model","value":"X1","extra":1},{"label":"Why","value":""}],"future":{"a":1}},
           {"kind":"workflow_task","id":9,"title":"Approve offboarding","requester":"HR","summary":"",
            "risk_score":null,"step":null,"ticket_id":null,"requested_at":"2026-10-05 10:00:00","due_at":null,"fields":[]}],
           "counts":{"total":2,"other":3},"unknown":true}
        """))
        assertEquals(2, r.total)
        val a = r.items[0]
        assertEquals(ApprovalKind.CATALOG_REQUEST, a.kind); assertEquals(7, a.id); assertEquals("catalog_request:7", a.key)
        assertEquals(55, a.riskScore); assertEquals("Manager", a.step); assertEquals(321, a.ticketId)
        assertEquals(listOf(QaField("Model", "X1"), QaField("Why", "")), a.fields)
        val b = r.items[1]
        assertNull(b.riskScore); assertNull(b.step); assertNull(b.ticketId); assertNull(b.dueAt); assertTrue(b.fields.isEmpty())
        assertEquals("workflow_task:9", b.key)
    }

    @Test fun `approvals tolerate missing everything`() {
        val r = FeatureParsers.approvals(obj("{}"))
        assertTrue(r.items.isEmpty()); assertEquals(0, r.total)
        val n = FeatureParsers.approvals(obj("""{"items":null,"counts":null}"""))
        assertTrue(n.items.isEmpty()); assertEquals(0, n.total)
    }

    @Test fun `approvals drop malformed entries and fall back to item count`() {
        val r = FeatureParsers.approvals(obj("""
          {"items":[{"kind":"mystery","id":1},{"kind":"catalog_request"},{"kind":"workflow_task","id":"5","title":null,"fields":null},"junk",null]}
        """))
        assertEquals(1, r.items.size)
        assertEquals(5, r.items[0].id); assertEquals("", r.items[0].title)
        assertEquals(1, r.total)
    }

    @Test fun `decision`() {
        assertEquals(DecisionResult(true, "approved"), FeatureParsers.decision(obj("""{"ok":true,"status":"approved"}""")))
        assertEquals(DecisionResult(false, null), FeatureParsers.decision(obj("""{"ok":false}""")))
        assertEquals(DecisionResult(false, null), FeatureParsers.decision(obj("{}")))
    }

    // ── catalog ──

    @Test fun `catalog full shape`() {
        val r = FeatureParsers.catalog(obj("""
          {"items":[{"id":1,"name":"Laptop","description":"A laptop","icon":"fa-laptop","requires_approval":true,"risk_score":30,
             "fields":[
               {"key":"kind","label":"Kind","type":"select","options":["A","B"],"required":true,"placeholder":"","show_if":null},
               {"key":"why","label":"Why","type":"textarea","options":[],"required":false,"placeholder":"Tell us","show_if":{"field":"kind","op":"equals","value":"A"}},
               {"key":"opt","label":"Opt","type":"checkbox","options":[],"required":false,"placeholder":"","show_if":{"field":"kind","op":"in","value":["A","B"]}},
               {"key":"n","label":"N","type":"number","show_if":{"field":"kind","op":"not_empty"}}]}],
           "popular":[1,2],"recent":[1],"extra":5}
        """))
        val item = r.items.single()
        assertTrue(item.requiresApproval); assertEquals(30, item.riskScore); assertEquals("fa-laptop", item.icon)
        assertEquals(listOf(1, 2), r.popular); assertEquals(listOf(1), r.recent)
        assertEquals(listOf("kind", "why", "opt", "n"), item.fields.map { it.key })
        assertEquals(listOf("A", "B"), item.fields[0].options); assertTrue(item.fields[0].required)
        assertNull(item.fields[0].showIf)
        assertEquals(ShowIfRule.Equals("kind", "A"), item.fields[1].showIf)
        assertEquals(ShowIfRule.In("kind", listOf("A", "B")), item.fields[2].showIf)
        assertEquals(ShowIfRule.NotEmpty("kind"), item.fields[3].showIf)
        assertEquals(FieldType.NUMBER, item.fields[3].type)
    }

    @Test fun `catalog tolerates nulls and drops unusable entries`() {
        val r = FeatureParsers.catalog(obj("""
          {"items":[{"id":2,"name":"X","description":null,"icon":null,"requires_approval":null,"risk_score":null,
                      "fields":[{"key":"a","type":"hologram"},{"key":"","type":"text"},{"type":"text"},{"key":"b","label":null,"type":"text","options":null}]},
                    {"name":"no id"},{"id":3}],
           "popular":null,"recent":["x",4]}
        """))
        assertEquals(1, r.items.size)
        val item = r.items[0]
        assertEquals("", item.description); assertFalse(item.requiresApproval); assertEquals(0, item.riskScore)
        assertEquals(listOf("b"), item.fields.map { it.key }); assertEquals("b", item.fields[0].label)
        assertTrue(r.popular.isEmpty()); assertEquals(listOf(4), r.recent)
        assertTrue(FeatureParsers.catalog(obj("{}")).items.isEmpty())
    }

    @Test fun `show_if follows the server's parseShowIf`() {
        fun p(s: String) = FeatureParsers.showIf(JsonParser.parseString(s))
        assertNull(p("null"))
        assertNull(p("\"\""))
        assertNull(p("[]"))
        assertNull(p("""{"field":"a","op":"equals"}"""))            // equals needs a string value
        assertNull(p("""{"field":"a","op":"equals","value":5}"""))  // non-string value
        assertNull(p("""{"field":"a","op":"in","value":[]}"""))     // in needs a non-empty list
        assertNull(p("""{"field":"a","op":"in","value":"A"}"""))
        assertNull(p("""{"field":"a","op":"matches","value":"x"}""")) // unknown op
        assertNull(p("""{"op":"not_empty"}"""))
        assertNull(p("""{"field":5,"op":"not_empty"}"""))
        assertEquals(ShowIfRule.Equals("a", ""), p("""{"field":"a","op":"equals","value":""}"""))
        assertEquals(ShowIfRule.NotEmpty("a"), p("""{"field":"a","op":"not_empty","value":"ignored"}"""))
        assertEquals(ShowIfRule.In("a", listOf("1", "2")), p("""{"field":"a","op":"in","value":[1,"2"]}"""))
        // stored column value arriving as an encoded JSON string
        assertEquals(ShowIfRule.NotEmpty("a"), FeatureParsers.showIf(com.google.gson.JsonPrimitive("""{"field":"a","op":"not_empty"}""")))
        assertNull(FeatureParsers.showIf(null))
    }

    @Test fun `submit`() {
        assertEquals(SubmitResult(12, RequestStatus.CREATED), FeatureParsers.submit(obj("""{"ok":true,"ticket_id":12,"status":"created"}""")))
        assertEquals(SubmitResult(12, RequestStatus.PENDING_APPROVAL), FeatureParsers.submit(obj("""{"ok":true,"ticket_id":12,"status":"pending_approval"}""")))
        assertEquals(RequestStatus.CREATED, FeatureParsers.submit(obj("""{"ticket_id":"12","status":null}"""))!!.status)
        assertNull(FeatureParsers.submit(obj("""{"ok":true}""")))
    }

    // ── tasks ──

    @Test fun `tasks full shape`() {
        val r = FeatureParsers.tasks(obj("""
          {"items":[{"id":1,"run_id":10,"run_title":"Onboarding: Jane","task_title":"Create account","instructions":"Do it",
                     "due_at":"2026-10-01 00:00:00","status":"pending","type":"manual","blocked_by":[],"contact_name":"Jane","assignee":"Me"},
                    {"id":2,"run_id":10,"run_title":"Onboarding: Jane","task_title":"Laptop","instructions":"","due_at":null,
                     "status":"blocked","type":"action","blocked_by":["Create account","Badge"],"contact_name":"Jane","assignee":null},
                    {"id":3,"run_id":11,"run_title":"x","task_title":"y","status":"action_failed","type":"approval"}],
           "counts":{"open":3,"overdue":1}}
        """))
        assertEquals(3, r.items.size); assertEquals(3, r.open); assertEquals(1, r.overdue)
        assertEquals(TaskStatus.BLOCKED, r.items[1].status); assertEquals(TaskType.ACTION, r.items[1].type)
        assertEquals(listOf("Create account", "Badge"), r.items[1].blockedBy); assertNull(r.items[1].dueAt); assertNull(r.items[1].assignee)
        assertEquals(TaskStatus.ACTION_FAILED, r.items[2].status); assertEquals(TaskType.APPROVAL, r.items[2].type)
        assertEquals("", r.items[2].instructions); assertTrue(r.items[2].blockedBy.isEmpty())
    }

    @Test fun `tasks tolerate unknown enums and missing counts`() {
        val r = FeatureParsers.tasks(obj("""{"items":[{"id":1,"status":"weird","type":"???"},{"run_id":2}]}"""))
        assertEquals(1, r.items.size)
        assertEquals(TaskStatus.UNKNOWN, r.items[0].status); assertEquals(TaskType.MANUAL, r.items[0].type)
        assertEquals(1, r.open); assertEquals(0, r.overdue)
        assertTrue(FeatureParsers.tasks(obj("{}")).items.isEmpty())
    }

    @Test fun `running and rejected task statuses parse`() {
        val r = FeatureParsers.tasks(obj("""{"items":[{"id":1,"status":"running"},{"id":2,"status":"rejected"}]}"""))
        assertEquals(listOf(TaskStatus.RUNNING, TaskStatus.REJECTED), r.items.map { it.status })
    }

    // ── attachments ──

    @Test fun `attachments`() {
        val list = FeatureParsers.attachments(obj("""
          {"items":[{"id":4,"name":"a.pdf","size":1234,"mime":"application/pdf","created_at":"2026-10-05 10:00:00","uploaded_by":"Bob","x":1},
                    {"id":5,"name":null,"size":null,"mime":null,"created_at":null,"uploaded_by":null},{"name":"no id"}]}
        """))
        assertEquals(2, list.size)
        assertEquals(TicketAttachment(4, "a.pdf", 1234L, "application/pdf", "2026-10-05 10:00:00", "Bob"), list[0])
        assertEquals(TicketAttachment(5, "", 0L, "", null, null), list[1])
        assertTrue(FeatureParsers.attachments(obj("{}")).isEmpty())
    }
}
