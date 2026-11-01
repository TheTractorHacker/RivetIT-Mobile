package com.foleyit.itflow.ui.screens.tasks

import com.foleyit.itflow.data.model.TaskStatus
import com.foleyit.itflow.data.model.TaskType
import com.foleyit.itflow.data.model.WorkflowTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class DueChip { NONE, OVERDUE, DUE_SOON }

/** A run (e.g. "Onboarding: Jane Doe") and the tasks of mine in it. */
data class TaskGroup(val runId: Int, val runTitle: String, val contactName: String, val tasks: List<WorkflowTask>)

object TasksLogic {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Tasks due within this many hours (and not yet overdue) get the "due soon" chip. */
    const val DUE_SOON_HOURS = 48L

    fun parse(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim()
        return try { LocalDateTime.parse(v, FORMAT) } catch (_: DateTimeParseException) {
            try { java.time.LocalDate.parse(v.take(10)).atTime(23, 59, 59) } catch (_: DateTimeParseException) { null }
        }
    }

    /** Finished tasks never carry a due chip. */
    fun dueChip(task: WorkflowTask, now: LocalDateTime): DueChip {
        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.SKIPPED) return DueChip.NONE
        val due = parse(task.dueAt) ?: return DueChip.NONE
        return when {
            due.isBefore(now) -> DueChip.OVERDUE
            !due.isAfter(now.plusHours(DUE_SOON_HOURS)) -> DueChip.DUE_SOON
            else -> DueChip.NONE
        }
    }

    /** Group by run, keeping the server's order for both runs and tasks. */
    fun group(tasks: List<WorkflowTask>): List<TaskGroup> {
        val groups = LinkedHashMap<Int, MutableList<WorkflowTask>>()
        tasks.forEach { groups.getOrPut(it.runId) { mutableListOf() } += it }
        return groups.map { (runId, list) ->
            TaskGroup(runId, list.first().runTitle, list.first().contactName, list)
        }
    }

    /** Manual, unblocked, open tasks can be completed or skipped by hand. */
    fun canAct(task: WorkflowTask): Boolean =
        task.type == TaskType.MANUAL && task.status == TaskStatus.PENDING

    fun isBlocked(task: WorkflowTask): Boolean = task.status == TaskStatus.BLOCKED

    /** Skipping needs a non-blank reason. */
    fun skipReasonValid(reason: String): Boolean = reason.isNotBlank()
}
