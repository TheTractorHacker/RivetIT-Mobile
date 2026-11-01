package com.foleyit.itflow

import com.foleyit.itflow.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import retrofit2.HttpException
import retrofit2.Response

/** Runs `viewModelScope` work eagerly on the test thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun httpError(code: Int, body: String = """{"error":"nope"}""") =
    HttpException(Response.error<Any>(code, body.toResponseBody()))

fun approval(kind: ApprovalKind = ApprovalKind.CATALOG_REQUEST, id: Int, title: String = "T$id") =
    ApprovalItem(kind, id, title, "Req", "", null, null, null, "2026-10-05 09:00:00", null, emptyList())

fun task(
    id: Int, run: Int = 1, status: TaskStatus = TaskStatus.PENDING, type: TaskType = TaskType.MANUAL,
    due: String? = null, blockedBy: List<String> = emptyList(),
) = WorkflowTask(id, run, "Run $run", "Task $id", "", due, status, type, blockedBy, "Jane", null)
