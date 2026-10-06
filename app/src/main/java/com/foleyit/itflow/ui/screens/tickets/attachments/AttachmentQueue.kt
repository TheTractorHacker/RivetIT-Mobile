package com.foleyit.itflow.ui.screens.tickets.attachments

sealed interface UploadState {
    data object Queued : UploadState
    data class Uploading(val percent: Int) : UploadState
    data object Done : UploadState
    data class Failed(val message: String) : UploadState
}

data class PendingAttachment(
    val id: Long,
    /** Content/file URI as a string, so the queue stays free of Android types. */
    val uri: String,
    val name: String,
    val size: Long,
    val mime: String,
    val state: UploadState = UploadState.Queued,
)

/** Files chosen for a reply, with the state of each upload. Immutable; every change returns a new queue. */
data class AttachmentQueue(
    val items: List<PendingAttachment> = emptyList(),
    private val nextId: Long = 1,
) {
    data class AddResult(val queue: AttachmentQueue, val rejection: Rejection?)

    fun add(uri: String, rawName: String?, size: Long, pickerMime: String?, maxBytes: Long): AddResult {
        val name = AttachmentRules.safeFileName(rawName)
        val rejection = AttachmentRules.check(name, size, maxBytes, items.size, items.map { it.name })
        if (rejection != null) return AddResult(this, rejection)
        val item = PendingAttachment(nextId, uri, name, size, AttachmentRules.uploadMime(name, pickerMime))
        return AddResult(copy(items = items + item, nextId = nextId + 1), null)
    }

    fun remove(id: Long): AttachmentQueue = copy(items = items.filterNot { it.id == id })

    private fun update(id: Long, state: UploadState) = copy(items = items.map { if (it.id == id) it.copy(state = state) else it })

    fun uploading(id: Long, percent: Int) = update(id, UploadState.Uploading(percent.coerceIn(0, 100)))
    fun done(id: Long) = update(id, UploadState.Done)
    fun failed(id: Long, message: String) = update(id, UploadState.Failed(message))
    fun retry(id: Long) = update(id, UploadState.Queued)

    /** Files still to send: queued, or failed and being retried. Done files are never re-sent. */
    val toUpload: List<PendingAttachment> get() = items.filter { it.state is UploadState.Queued }
    val hasFailures: Boolean get() = items.any { it.state is UploadState.Failed }
    val isBusy: Boolean get() = items.any { it.state is UploadState.Uploading }
    val allDone: Boolean get() = items.isNotEmpty() && items.all { it.state is UploadState.Done }
}
