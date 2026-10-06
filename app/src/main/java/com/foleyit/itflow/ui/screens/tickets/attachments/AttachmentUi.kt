package com.foleyit.itflow.ui.screens.tickets.attachments

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.foleyit.itflow.R
import com.foleyit.itflow.data.model.TicketAttachment
import com.foleyit.itflow.ui.util.fmtDate

fun fileKindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.IMAGE -> Icons.Outlined.Image
    FileKind.PDF -> Icons.Outlined.PictureAsPdf
    FileKind.DOCUMENT -> Icons.Outlined.Description
    FileKind.SHEET -> Icons.Outlined.TableChart
    FileKind.ARCHIVE -> Icons.Outlined.FolderZip
    FileKind.TEXT -> Icons.Outlined.TextSnippet
    FileKind.MEDIA -> Icons.Outlined.PermMedia
    FileKind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

class AttachmentPickers(val camera: () -> Unit, val photos: () -> Unit, val files: () -> Unit)

/**
 * System pickers only: the photo picker, the document picker, and the camera app writing to an app-private
 * FileProvider URI. No storage permission is requested. The camera needs the CAMERA runtime permission (the manifest
 * declares it for the barcode scanner, which makes the camera intent require it); a denial is reported, never thrown.
 */
@Composable
fun rememberAttachmentPickers(onPicked: (List<Uri>) -> Unit, onMessage: (Int) -> Unit): AttachmentPickers {
    val ctx = LocalContext.current
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraTarget
        if (ok && target != null) onPicked(listOf(target))
    }
    fun launchCamera() {
        val target = AttachmentTransfer.newCameraTarget(ctx)
        cameraTarget = target
        try { takePicture.launch(target) } catch (_: ActivityNotFoundException) { onMessage(R.string.attach_no_camera) }
        catch (_: SecurityException) { onMessage(R.string.attach_camera_denied) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else onMessage(R.string.attach_camera_denied)
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris -> if (uris.isNotEmpty()) onPicked(uris) }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) onPicked(uris) }

    return remember(ctx) {
        AttachmentPickers(
            camera = {
                if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) onMessage(R.string.attach_no_camera)
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            photos = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            files = { docs.launch(arrayOf("*/*")) },
        )
    }
}

/** Reads each picked URI's name/size and adds the accepted ones to the queue; returns the queue and the first rejection. */
fun addPicked(ctx: Context, queue: AttachmentQueue, uris: List<Uri>, maxBytes: Long): Pair<AttachmentQueue, Pair<String, Rejection>?> {
    var q = queue
    var firstRejection: Pair<String, Rejection>? = null
    for (uri in uris) {
        val info = AttachmentTransfer.describe(ctx, uri)
        val name = AttachmentRules.safeFileName(info.name)
        val r = q.add(uri.toString(), info.name, info.size, info.mime, maxBytes)
        q = r.queue
        if (r.rejection != null && firstRejection == null) firstRejection = name to r.rejection
    }
    return q to firstRejection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSourceSheet(pickers: AttachmentPickers, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                stringResource(R.string.attach_sheet_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SourceRow(Icons.Outlined.PhotoCamera, R.string.attach_camera) { onDismiss(); pickers.camera() }
            SourceRow(Icons.Outlined.PhotoLibrary, R.string.attach_photo) { onDismiss(); pickers.photos() }
            SourceRow(Icons.Outlined.FolderOpen, R.string.attach_files) { onDismiss(); pickers.files() }
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, label: Int, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(stringResource(label)) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** The queued files for a reply: icon, name, size, upload status, and Remove / Retry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentChips(queue: AttachmentQueue, enabled: Boolean, onRemove: (Long) -> Unit, onRetry: (Long) -> Unit) {
    if (queue.items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        queue.items.forEach { a ->
            val size = AttachmentRules.formatSize(a.size)
            val desc = stringResource(R.string.attach_chip_desc, a.name, size)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 12.dp, top = 6.dp, end = 4.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(fileKindIcon(AttachmentRules.kindOf(a.name, a.mime)), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = desc }) {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (val s = a.state) {
                                    UploadState.Queued -> if (enabled) size else stringResource(R.string.attach_waiting)
                                    is UploadState.Uploading -> stringResource(R.string.attach_uploading, s.percent)
                                    UploadState.Done -> stringResource(R.string.attach_uploaded)
                                    is UploadState.Failed -> stringResource(R.string.attach_failed)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (a.state is UploadState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (a.state is UploadState.Failed) {
                            IconButton(onClick = { onRetry(a.id) }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.attach_retry, a.name)) }
                        }
                        if (a.state !is UploadState.Uploading && a.state !is UploadState.Done) {
                            IconButton(onClick = { onRemove(a.id) }, enabled = enabled || a.state is UploadState.Failed) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.attach_remove, a.name))
                            }
                        }
                    }
                    (a.state as? UploadState.Uploading)?.let { LinearProgressIndicator(progress = { it.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) }
                }
            }
        }
    }
}

/** The "Attachments" card on the ticket: icon by type, name, size; tap downloads and opens. */
@Composable
fun AttachmentsCard(
    items: List<TicketAttachment>,
    openingId: Int?,
    onOpen: (TicketAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.attach_section_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            items.forEach { a ->
                val size = AttachmentRules.formatSize(a.size)
                val by = listOfNotNull(size, a.uploadedBy, a.createdAt?.let { fmtDate(it) }?.takeIf { it.isNotBlank() }).joinToString(" • ")
                ListItem(
                    leadingContent = {
                        if (openingId == a.id) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(fileKindIcon(AttachmentRules.kindOf(a.name, a.mime)), null)
                    },
                    headlineContent = { Text(a.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(by) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier
                        .clickable(enabled = openingId == null, role = Role.Button) { onOpen(a) }
                        .semantics { contentDescription = a.name + ", " + size },
                )
            }
        }
    }
}
