package com.foleyit.itflow.data.model

/**
 * Domain models for the approvals, service-request, workflow-task and ticket-attachment endpoints.
 * They are built by [com.foleyit.itflow.data.api.FeatureParsers], which tolerates nulls, missing keys
 * and unknown fields, so nothing here is nullable "just in case".
 */

// ── Approvals ───────────────────────────────────────────────────────────────

enum class ApprovalKind(val wire: String) {
    CATALOG_REQUEST("catalog_request"),
    WORKFLOW_TASK("workflow_task");

    companion object {
        fun fromWire(raw: String?): ApprovalKind? = entries.firstOrNull { it.wire == raw }
    }
}

data class QaField(val label: String, val value: String)

data class ApprovalItem(
    val kind: ApprovalKind,
    val id: Int,
    val title: String,
    val requester: String,
    val summary: String,
    val riskScore: Int?,
    val step: String?,
    val ticketId: Int?,
    val requestedAt: String?,
    val dueAt: String?,
    val fields: List<QaField>,
) {
    /** Kind + id is the identity; ids alone collide between the two kinds. */
    val key: String get() = "${kind.wire}:$id"
}

data class ApprovalsResult(val items: List<ApprovalItem>, val total: Int)

data class DecisionResult(val ok: Boolean, val status: String?)

// ── Service catalog ─────────────────────────────────────────────────────────

enum class FieldType(val wire: String) {
    TEXT("text"), TEXTAREA("textarea"), SELECT("select"), CHECKBOX("checkbox"), DATE("date"), NUMBER("number");

    companion object {
        fun fromWire(raw: String?): FieldType? = entries.firstOrNull { it.wire == raw }
    }
}

/** A `show_if` rule, validated like the server's `ServiceCatalogService::parseShowIf`. */
sealed interface ShowIfRule {
    val field: String

    data class Equals(override val field: String, val value: String) : ShowIfRule
    data class In(override val field: String, val values: List<String>) : ShowIfRule
    data class NotEmpty(override val field: String) : ShowIfRule
}

data class CatalogField(
    val key: String,
    val label: String,
    val type: FieldType,
    val options: List<String>,
    val required: Boolean,
    val placeholder: String,
    val showIf: ShowIfRule?,
)

data class CatalogItem(
    val id: Int,
    val name: String,
    val description: String,
    val icon: String,
    val requiresApproval: Boolean,
    val riskScore: Int,
    val fields: List<CatalogField>,
)

data class CatalogResult(val items: List<CatalogItem>, val popular: List<Int>, val recent: List<Int>)

enum class RequestStatus { CREATED, PENDING_APPROVAL;
    companion object {
        fun fromWire(raw: String?) = if (raw == "pending_approval") PENDING_APPROVAL else CREATED
    }
}

data class SubmitResult(val ticketId: Int, val status: RequestStatus)

// ── Workflow tasks ──────────────────────────────────────────────────────────

enum class TaskStatus(val wire: String) {
    PENDING("pending"), BLOCKED("blocked"), COMPLETED("completed"), SKIPPED("skipped"), ACTION_FAILED("action_failed"),
    RUNNING("running"), REJECTED("rejected"),
    /** A status this app version does not know; shown read-only with no actions. */
    UNKNOWN("unknown");

    companion object {
        fun fromWire(raw: String?): TaskStatus = entries.firstOrNull { it.wire == raw } ?: UNKNOWN
    }
}

enum class TaskType(val wire: String) {
    MANUAL("manual"), APPROVAL("approval"), ACTION("action");

    companion object {
        fun fromWire(raw: String?): TaskType = entries.firstOrNull { it.wire == raw } ?: MANUAL
    }
}

data class WorkflowTask(
    val id: Int,
    val runId: Int,
    val runTitle: String,
    val taskTitle: String,
    val instructions: String,
    val dueAt: String?,
    val status: TaskStatus,
    val type: TaskType,
    val blockedBy: List<String>,
    val contactName: String,
    val assignee: String?,
)

data class TasksResult(val items: List<WorkflowTask>, val open: Int, val overdue: Int)

// ── Ticket attachments ──────────────────────────────────────────────────────

data class TicketAttachment(
    val id: Int,
    val name: String,
    val size: Long,
    val mime: String,
    val createdAt: String?,
    val uploadedBy: String?,
)
