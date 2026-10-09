package com.foleyit.itflow.ui.screens.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foleyit.itflow.data.model.ApprovalItem
import com.foleyit.itflow.data.model.ApprovalsResult
import com.foleyit.itflow.data.repo.*
import com.foleyit.itflow.ui.util.ListLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** One-shot results the screen shows as a snackbar / haptic. */
sealed interface ApprovalEvent {
    data class Decided(val approved: Boolean, val status: String?) : ApprovalEvent
    data class Failed(val error: Throwable) : ApprovalEvent
    /** Rejecting needs a reason; nothing was sent. */
    data object CommentRequired : ApprovalEvent
    /** The item was already decided elsewhere (404 / 409); the list is being refreshed. */
    data object NoLongerWaiting : ApprovalEvent
}

data class ApprovalsUiState(
    val load: ListLoad<ApprovalsResult> = ListLoad(),
    /** Keys ("kind:id") with a decision in flight; their buttons stay disabled. */
    val deciding: Set<String> = emptySet(),
) {
    val items: List<ApprovalItem> get() = load.data?.items.orEmpty()
}

class ApprovalsViewModel(
    private val repo: ApprovalsRepository = ApiApprovalsRepository(),
    private val cache: FeatureCache = FeatureCache.shared,
    private val badges: FeatureBadges = FeatureBadges.shared,
) : ViewModel() {

    private val _state = MutableStateFlow(ApprovalsUiState(ListLoad.initial(cache.approvals)))
    val state: StateFlow<ApprovalsUiState> = _state.asStateFlow()

    private val _events = Channel<ApprovalEvent>(Channel.BUFFERED)
    val events: Flow<ApprovalEvent> = _events.receiveAsFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(load = it.load.started()) }
        viewModelScope.launch {
            try {
                val result = repo.list()
                cache.approvals = result
                badges.setApprovals(result.total)
                _state.update { it.copy(load = it.load.succeeded(result)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(load = it.load.failed(e)) }
            }
        }
    }

    /** Approve, or reject with a required [comment]. Ignored while a decision for the same item is in flight. */
    fun decide(item: ApprovalItem, approve: Boolean, comment: String) {
        val text = comment.trim()
        if (!approve && text.isEmpty()) {
            _events.trySend(ApprovalEvent.CommentRequired)
            return
        }
        val key = item.key
        if (key in _state.value.deciding) return
        _state.update { it.copy(deciding = it.deciding + key) }
        viewModelScope.launch {
            try {
                val result = repo.decide(item.kind, item.id, approve, text)
                if (!result.ok) throw UnexpectedResponseException("ok=false")
                removeLocally(key)
                _events.trySend(ApprovalEvent.Decided(approve, result.status))
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val code = (e as? HttpException)?.code()
                if (code == 404 || code == 409) {
                    removeLocally(key)
                    _events.trySend(ApprovalEvent.NoLongerWaiting)
                    refresh()
                } else {
                    _events.trySend(ApprovalEvent.Failed(e))
                }
            } finally {
                _state.update { it.copy(deciding = it.deciding - key) }
            }
        }
    }

    private fun removeLocally(key: String) {
        _state.update { s ->
            val current = s.load.data ?: return@update s
            val items = current.items.filterNot { it.key == key }
            val updated = current.copy(items = items, total = (current.total - 1).coerceAtLeast(items.size))
            cache.approvals = updated
            badges.setApprovals(updated.total)
            s.copy(load = s.load.withData(updated))
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ApprovalsViewModel() as T
        }
    }
}
