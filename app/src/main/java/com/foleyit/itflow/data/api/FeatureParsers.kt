package com.foleyit.itflow.data.api

import com.foleyit.itflow.data.model.*
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Tolerant parsers for the approvals / catalog / workflow-task / attachment responses. Unknown keys are
 * ignored, explicit nulls and missing keys fall back to empty values, and a malformed entry is dropped
 * instead of failing the whole list.
 */
object FeatureParsers {

    private fun JsonElement?.obj(): JsonObject? = if (this != null && this.isJsonObject) this.asJsonObject else null
    private fun JsonElement?.arr(): JsonArray? = if (this != null && this.isJsonArray) this.asJsonArray else null

    private fun JsonObject.str(key: String): String? {
        val e = get(key)
        return if (e is JsonPrimitive) e.asString else null
    }

    private fun JsonObject.int(key: String): Int? {
        val e = get(key)
        if (e !is JsonPrimitive) return null
        return runCatching { if (e.isNumber) e.asNumber.toInt() else e.asString.trim().toInt() }.getOrNull()
    }

    private fun JsonObject.long(key: String): Long? {
        val e = get(key)
        if (e !is JsonPrimitive) return null
        return runCatching { if (e.isNumber) e.asNumber.toLong() else e.asString.trim().toLong() }.getOrNull()
    }

    private fun JsonObject.bool(key: String): Boolean {
        val e = get(key)
        if (e !is JsonPrimitive) return false
        return when {
            e.isBoolean -> e.asBoolean
            e.isNumber -> e.asInt != 0
            else -> e.asString.let { it == "1" || it.equals("true", ignoreCase = true) }
        }
    }

    private fun JsonObject.strings(key: String): List<String> =
        get(key).arr()?.mapNotNull { if (it is JsonPrimitive) it.asString else null } ?: emptyList()

    private fun JsonObject.ints(key: String): List<Int> =
        get(key).arr()?.mapNotNull { e ->
            if (e is JsonPrimitive) runCatching { if (e.isNumber) e.asInt else e.asString.trim().toInt() }.getOrNull() else null
        } ?: emptyList()

    // ── Approvals ──

    fun approvals(root: JsonObject): ApprovalsResult {
        val items = root.get("items").arr()?.mapNotNull { approval(it.obj()) } ?: emptyList()
        val total = root.get("counts").obj()?.int("total") ?: items.size
        return ApprovalsResult(items, total)
    }

    private fun approval(o: JsonObject?): ApprovalItem? {
        o ?: return null
        val kind = ApprovalKind.fromWire(o.str("kind")) ?: return null
        val id = o.int("id") ?: return null
        val fields = o.get("fields").arr()?.mapNotNull { f ->
            val fo = f.obj() ?: return@mapNotNull null
            QaField(fo.str("label").orEmpty(), fo.str("value").orEmpty())
        } ?: emptyList()
        return ApprovalItem(
            kind = kind, id = id,
            title = o.str("title").orEmpty(),
            requester = o.str("requester").orEmpty(),
            summary = o.str("summary").orEmpty(),
            riskScore = o.int("risk_score"),
            step = o.str("step")?.takeIf { it.isNotBlank() },
            ticketId = o.int("ticket_id")?.takeIf { it > 0 },
            requestedAt = o.str("requested_at"),
            dueAt = o.str("due_at")?.takeIf { it.isNotBlank() },
            fields = fields,
        )
    }

    fun decision(root: JsonObject) = DecisionResult(root.bool("ok"), root.str("status"))

    // ── Catalog ──

    fun catalog(root: JsonObject): CatalogResult {
        val items = root.get("items").arr()?.mapNotNull { catalogItem(it.obj()) } ?: emptyList()
        return CatalogResult(items, root.ints("popular"), root.ints("recent"))
    }

    private fun catalogItem(o: JsonObject?): CatalogItem? {
        o ?: return null
        val id = o.int("id") ?: return null
        val name = o.str("name") ?: return null
        val fields = o.get("fields").arr()?.mapNotNull { catalogField(it.obj()) } ?: emptyList()
        return CatalogItem(
            id = id, name = name,
            description = o.str("description").orEmpty(),
            icon = o.str("icon").orEmpty(),
            requiresApproval = o.bool("requires_approval"),
            riskScore = o.int("risk_score") ?: 0,
            fields = fields,
        )
    }

    private fun catalogField(o: JsonObject?): CatalogField? {
        o ?: return null
        val key = o.str("key")?.takeIf { it.isNotBlank() } ?: return null
        // An unknown field type cannot be rendered or validated; the server would also reject it.
        val type = FieldType.fromWire(o.str("type")) ?: return null
        return CatalogField(
            key = key,
            label = o.str("label")?.takeIf { it.isNotBlank() } ?: key,
            type = type,
            options = o.strings("options").map { it.trim() }.filter { it.isNotEmpty() },
            required = o.bool("required"),
            placeholder = o.str("placeholder").orEmpty(),
            showIf = showIf(o.get("show_if")),
        )
    }

    /**
     * Mirrors `ServiceCatalogService::parseShowIf`: a missing, non-object or malformed rule is null, which means the
     * field is always shown (a malformed rule never hides a field). `equals` needs a string value, `in` a non-empty list.
     */
    fun showIf(raw: JsonElement?): ShowIfRule? {
        var el = raw
        // The column is stored JSON; tolerate it arriving as an encoded string too.
        if (el is JsonPrimitive && el.isString) {
            el = runCatching { com.google.gson.JsonParser.parseString(el.asString) }.getOrNull()
        }
        val o = el.obj() ?: return null
        val field = (o.get("field") as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: return null
        val op = (o.get("op") as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: return null
        val value = o.get("value")
        return when (op) {
            "equals" -> {
                val v = (value as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: return null
                ShowIfRule.Equals(field, v)
            }
            "in" -> {
                val list = value.arr()?.mapNotNull { if (it is JsonPrimitive) it.asString else null }
                if (list.isNullOrEmpty()) return null
                ShowIfRule.In(field, list)
            }
            "not_empty" -> ShowIfRule.NotEmpty(field)
            else -> null
        }
    }

    fun submit(root: JsonObject): SubmitResult? {
        val ticket = root.int("ticket_id") ?: return null
        return SubmitResult(ticket, RequestStatus.fromWire(root.str("status")))
    }

    // ── Workflow tasks ──

    fun tasks(root: JsonObject): TasksResult {
        val items = root.get("items").arr()?.mapNotNull { task(it.obj()) } ?: emptyList()
        val counts = root.get("counts").obj()
        return TasksResult(
            items,
            open = counts?.int("open") ?: items.count { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.SKIPPED },
            overdue = counts?.int("overdue") ?: 0,
        )
    }

    private fun task(o: JsonObject?): WorkflowTask? {
        o ?: return null
        val id = o.int("id") ?: return null
        return WorkflowTask(
            id = id,
            runId = o.int("run_id") ?: 0,
            runTitle = o.str("run_title").orEmpty(),
            taskTitle = o.str("task_title").orEmpty(),
            instructions = o.str("instructions").orEmpty(),
            dueAt = o.str("due_at")?.takeIf { it.isNotBlank() },
            status = TaskStatus.fromWire(o.str("status")),
            type = TaskType.fromWire(o.str("type")),
            blockedBy = o.strings("blocked_by"),
            contactName = o.str("contact_name").orEmpty(),
            assignee = o.str("assignee")?.takeIf { it.isNotBlank() },
        )
    }

    // ── Attachments ──

    fun attachments(root: JsonObject): List<TicketAttachment> =
        root.get("items").arr()?.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.int("id") ?: return@mapNotNull null
            TicketAttachment(
                id = id,
                name = o.str("name").orEmpty(),
                size = o.long("size") ?: 0L,
                mime = o.str("mime").orEmpty(),
                createdAt = o.str("created_at"),
                uploadedBy = o.str("uploaded_by")?.takeIf { it.isNotBlank() },
            )
        } ?: emptyList()
}
