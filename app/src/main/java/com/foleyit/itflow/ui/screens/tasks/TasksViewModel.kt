package com.foleyit.itflow.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foleyit.itflow.data.model.TasksResult
import com.foleyit.itflow.data.model.WorkflowTask
import com.foleyit.itflow.data.repo.*
import com.foleyit.itflow.ui.util.ListLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface TaskEvent {
    data class Completed(val status: String?) : TaskEvent
    data class Skipped(val status: String?) : TaskEvent
    data class Failed(val error: Throwable) : TaskEvent
    data object ReasonRequired : TaskEvent
    /** The server refused because the task changed meanwhile (blocked / already done); the list refreshes. */
    data object Changed : TaskEvent
}

data class TasksUiState(
    val load: ListLoad<TasksResult> = ListLoad(),
    val acting: Set<Int> = emptySet(),
) {
    val items: List<WorkflowTask> get() = load.data?.items.orEmpty()
}

class TasksViewModel(
    private val repo: TasksRepository = ApiTasksRepository(),
    private val cache: FeatureCache = FeatureCache.shared,
    private val badges: FeatureBadges = FeatureBadges.shared,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState(ListLoad.initial(cache.tasks)))
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private val _events = Channel<TaskEvent>(Channel.BUFFERED)
    val events: Flow<TaskEvent> = _events.receiveAsFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(load = it.load.started()) }
        viewModelScope.launch {
            try {
                val result = repo.list()
                cache.tasks = result
                badges.setOverdueTasks(result.overdue)
                _state.update { it.copy(load = it.load.succeeded(result)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(load = it.load.failed(e)) }
            }
        }
    }

    fun complete(task: WorkflowTask) {
        if (!TasksLogic.canAct(task)) return
        act(task) { repo.complete(task.id) }
    }

    fun skip(task: WorkflowTask, reason: String) {
        if (!TasksLogic.canAct(task)) return
        val text = reason.trim()
        if (!TasksLogic.skipReasonValid(text)) {
            _events.trySend(TaskEvent.ReasonRequired)
            return
        }
        act(task, skipping = true) { repo.skip(task.id, text) }
    }

    private fun act(task: WorkflowTask, skipping: Boolean = false, call: suspend () -> com.foleyit.itflow.data.model.DecisionResult) {
        if (task.id in _state.value.acting) return
        _state.update { it.copy(acting = it.acting + task.id) }
        viewModelScope.launch {
            try {
                val r = call()
                if (!r.ok) throw UnexpectedResponseException("ok=false")
                _events.trySend(if (skipping) TaskEvent.Skipped(r.status) else TaskEvent.Completed(r.status))
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val code = (e as? HttpException)?.code()
                if (code == 404 || code == 409) {
                    _events.trySend(TaskEvent.Changed)
                    refresh()
                } else {
                    _events.trySend(TaskEvent.Failed(e))
                }
            } finally {
                _state.update { it.copy(acting = it.acting - task.id) }
            }
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TasksViewModel() as T
        }
    }
}
