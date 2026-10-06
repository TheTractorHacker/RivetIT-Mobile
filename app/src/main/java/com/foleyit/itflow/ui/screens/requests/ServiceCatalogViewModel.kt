package com.foleyit.itflow.ui.screens.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foleyit.itflow.data.model.CatalogItem
import com.foleyit.itflow.data.model.CatalogResult
import com.foleyit.itflow.data.model.SubmitResult
import com.foleyit.itflow.data.repo.*
import com.foleyit.itflow.ui.util.ListLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface RequestStage {
    data object Catalog : RequestStage
    data class Form(val item: CatalogItem) : RequestStage
    data class Done(val item: CatalogItem, val result: SubmitResult) : RequestStage
}

data class RequestUiState(
    val load: ListLoad<CatalogResult> = ListLoad(),
    val query: String = "",
    val stage: RequestStage = RequestStage.Catalog,
    val answers: Map<String, String> = emptyMap(),
    val errors: Map<String, FieldError> = emptyMap(),
    val submitting: Boolean = false,
    val submitError: Throwable? = null,
)

class ServiceCatalogViewModel(
    private val repo: CatalogRepository = ApiCatalogRepository(),
    private val cache: FeatureCache = FeatureCache.shared,
) : ViewModel() {

    private val _state = MutableStateFlow(RequestUiState(load = ListLoad.initial(cache.catalog)))
    val state: StateFlow<RequestUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(load = it.load.started()) }
        viewModelScope.launch {
            try {
                val result = repo.load()
                cache.catalog = result
                _state.update { it.copy(load = it.load.succeeded(result)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(load = it.load.failed(e)) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun open(item: CatalogItem) = _state.update {
        it.copy(stage = RequestStage.Form(item), answers = emptyMap(), errors = emptyMap(), submitError = null, submitting = false)
    }

    /** Leaves the form (answers are dropped) or the result screen. */
    fun backToCatalog() = _state.update {
        it.copy(stage = RequestStage.Catalog, answers = emptyMap(), errors = emptyMap(), submitError = null, submitting = false)
    }

    /** Stores an answer and clears the answers (and errors) of any field this change hides. */
    fun setAnswer(key: String, value: String) {
        _state.update { s ->
            val form = s.stage as? RequestStage.Form ?: return@update s
            val next = ShowIfEvaluator(form.item.fields).clearHidden(s.answers + (key to value))
            val visible = next.keys
            s.copy(answers = next, errors = s.errors.filterKeys { it != key && it in visible }, submitError = null)
        }
    }

    fun submit() {
        val s = _state.value
        val form = s.stage as? RequestStage.Form ?: return
        if (s.submitting) return
        val errors = RequestFormLogic.validate(form.item.fields, s.answers)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val payload = RequestFormLogic.buildAnswers(form.item.fields, s.answers)
        _state.update { it.copy(submitting = true, submitError = null, errors = emptyMap()) }
        viewModelScope.launch {
            try {
                val result = repo.submit(form.item.id, payload, null)
                _state.update { it.copy(stage = RequestStage.Done(form.item, result), submitting = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, submitError = e) }
            }
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ServiceCatalogViewModel() as T
        }
    }
}
