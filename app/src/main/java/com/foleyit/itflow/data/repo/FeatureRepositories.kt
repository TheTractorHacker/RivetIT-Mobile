package com.foleyit.itflow.data.repo

import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.FeatureParsers
import com.foleyit.itflow.data.model.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** Thrown when a 2xx response is not in the documented shape. */
class UnexpectedResponseException(message: String) : Exception(message)

interface ApprovalsRepository {
    suspend fun list(): ApprovalsResult
    suspend fun decide(kind: ApprovalKind, id: Int, approve: Boolean, comment: String): DecisionResult
}

interface CatalogRepository {
    suspend fun load(): CatalogResult
    suspend fun submit(itemId: Int, answers: Map<String, String>, clientId: Int?): SubmitResult
}

interface TasksRepository {
    suspend fun list(): TasksResult
    suspend fun complete(id: Int): DecisionResult
    suspend fun skip(id: Int, reason: String): DecisionResult
}

class ApiApprovalsRepository : ApprovalsRepository {
    override suspend fun list() = FeatureParsers.approvals(ApiClient.service().getApprovals())

    override suspend fun decide(kind: ApprovalKind, id: Int, approve: Boolean, comment: String): DecisionResult {
        val body = JsonObject().apply {
            addProperty("kind", kind.wire)
            addProperty("id", id)
            addProperty("decision", if (approve) "approve" else "reject")
            addProperty("comment", comment)
        }
        return FeatureParsers.decision(ApiClient.service().decideApproval(body))
    }
}

class ApiCatalogRepository : CatalogRepository {
    override suspend fun load() = FeatureParsers.catalog(ApiClient.service().getServiceCatalog())

    override suspend fun submit(itemId: Int, answers: Map<String, String>, clientId: Int?): SubmitResult {
        val body = JsonObject().apply {
            addProperty("catalog_item_id", itemId)
            add("answers", JsonObject().also { o -> answers.forEach { (k, v) -> o.addProperty(k, v) } })
            if (clientId != null) addProperty("client_id", clientId) else add("client_id", com.google.gson.JsonNull.INSTANCE)
        }
        return FeatureParsers.submit(ApiClient.service().submitCatalogRequest(body))
            ?: throw UnexpectedResponseException("Missing ticket_id")
    }
}

class ApiTasksRepository : TasksRepository {
    override suspend fun list() = FeatureParsers.tasks(ApiClient.service().getWorkflowTasks("mine"))

    override suspend fun complete(id: Int) = act(id, "complete", "")
    override suspend fun skip(id: Int, reason: String) = act(id, "skip", reason)

    private suspend fun act(id: Int, action: String, reason: String): DecisionResult {
        val body = JsonObject().apply {
            addProperty("id", id)
            addProperty("action", action)
            addProperty("reason", reason)
        }
        return FeatureParsers.decision(ApiClient.service().actOnWorkflowTask(body))
    }
}

/**
 * Last successful result of each list, kept in memory only. Responses are fetched `no-store` so approvals and
 * tasks (names, requests, answers) never reach the disk cache; keeping the last result in process memory still
 * lets a screen open instantly and survive a flaky connection. Cleared on sign-out and on account/server change.
 */
class FeatureCache {
    @Volatile var approvals: ApprovalsResult? = null
    @Volatile var catalog: CatalogResult? = null
    @Volatile var tasks: TasksResult? = null

    fun clear() { approvals = null; catalog = null; tasks = null }

    companion object { val shared = FeatureCache() }
}
