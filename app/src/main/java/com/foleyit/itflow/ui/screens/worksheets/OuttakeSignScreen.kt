package com.foleyit.itflow.ui.screens.worksheets

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.*
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.components.SectionLabel
import com.foleyit.itflow.ui.util.fmtDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OuttakeSignScreen(outtakeId: Int, navController: NavController) {
    var outtake by remember { mutableStateOf<Result<OuttakeDetail>?>(null) }
    var signedName by remember { mutableStateOf("") }
    var paths by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var termsAgreed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(android.util.Size(0, 0)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val result = runCatching { ApiClient.service().getOuttake(outtakeId) }
        outtake = result
        // Pre-fill with the ticket contact name, falling back to client name
        result.getOrNull()?.let { ot ->
            if (signedName.isBlank()) {
                signedName = ot.contactName?.takeIf { it.isNotBlank() }
                    ?: ot.client?.takeIf { it.isNotBlank() }
                    ?: ""
            }
        }
    }

    fun clearSignature() { paths = emptyList(); currentPath = emptyList() }

    fun captureSignature(): String? {
        if (paths.isEmpty() && currentPath.isEmpty()) return null
        val w = canvasSize.width.takeIf { it > 0 } ?: 600
        val h = canvasSize.height.takeIf { it > 0 } ?: 200
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 4f
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }
        (paths + if (currentPath.size > 1) listOf(currentPath) else emptyList()).forEach { pts ->
            if (pts.size < 2) return@forEach
            val path = android.graphics.Path()
            path.moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { path.lineTo(it.x, it.y) }
            canvas.drawPath(path, paint)
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        return "data:image/png;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun sign() {
        if (signedName.isBlank()) { error = "Name is required"; return }
        val allPaths = paths + if (currentPath.size > 1) listOf(currentPath) else emptyList()
        if (allPaths.isEmpty()) { error = "Please draw your signature"; return }
        if (!termsAgreed) { error = "Please agree to the Terms & Conditions"; return }
        loading = true; error = null
        scope.launch {
            try {
                val sig = captureSignature() ?: run { error = "Failed to capture signature"; loading = false; return@launch }
                withContext(Dispatchers.IO) {
                    ApiClient.service().signOuttake(outtakeId, SignRequest(signedName.trim(), sig))
                }
                navController.popBackStack()
            } catch (e: Exception) {
                error = "Failed to sign: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outtake Form") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            val ot = outtake?.getOrNull()
            if (ot != null && !ot.signed) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                        if (error != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.width(6.dp))
                                    Text(error!!, color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Button(
                            onClick = ::sign,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            enabled = !loading
                        ) {
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Outlined.Draw, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sign & Complete", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            outtake == null -> LoadingScreen()
            outtake!!.isFailure -> ErrorScreen(outtake!!.exceptionOrNull()?.message ?: "")
            else -> {
                val ot = outtake!!.getOrThrow()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header card
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Outlined.Assignment, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Outtake / Pickup Form",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold)
                                        ot.ticketSubject?.let {
                                            Text(it, style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                if (ot.client != null || !ot.notes.isNullOrBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(12.dp))
                                }
                                ot.client?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Business, null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.outline)
                                        Spacer(Modifier.width(6.dp))
                                        Text(it, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                if (!ot.notes.isNullOrBlank()) {
                                    if (ot.client != null) Spacer(Modifier.height(6.dp))
                                    Row {
                                        Icon(Icons.AutoMirrored.Outlined.Notes, null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.outline)
                                        Spacer(Modifier.width(6.dp))
                                        Text(ot.notes, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                if (ot.signed) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Signed by ${ot.signedName} on ${fmtDate(ot.signedAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!ot.signed) {
                        // Sign-off card: customer name, signature pad, terms
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    SectionLabel("Sign-off")

                                    OutlinedTextField(
                                        value = signedName, onValueChange = { signedName = it },
                                        label = { Text("Customer Name *") },
                                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Spacer(Modifier.height(16.dp))
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("Customer Signature",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(onClick = ::clearSignature) {
                                            Icon(Icons.Outlined.Clear, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Clear")
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(Color.White)
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                MaterialTheme.shapes.medium)
                                    ) {
                                        if (paths.isEmpty() && currentPath.isEmpty()) {
                                            Text("Customer signs here",
                                                modifier = Modifier.align(Alignment.Center),
                                                color = Color(0xFFB0B0B0),
                                                style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            currentPath = listOf(offset)
                                                            canvasSize = android.util.Size(size.width, size.height)
                                                        },
                                                        onDrag = { change, _ ->
                                                            currentPath = currentPath + change.position
                                                        },
                                                        onDragEnd = {
                                                            if (currentPath.size > 1) paths = paths + listOf(currentPath)
                                                            currentPath = emptyList()
                                                        }
                                                    )
                                                }
                                        ) {
                                            (paths + if (currentPath.size > 1) listOf(currentPath) else emptyList())
                                                .forEach { pts ->
                                                    if (pts.size < 2) return@forEach
                                                    drawPath(
                                                        path = androidx.compose.ui.graphics.Path().apply {
                                                            moveTo(pts[0].x, pts[0].y)
                                                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                                                        },
                                                        color = Color.Black,
                                                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                    )
                                                }
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))
                                    val uriHandler = LocalUriHandler.current
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Checkbox(checked = termsAgreed, onCheckedChange = { termsAgreed = it })
                                            Text("I agree to the", style = MaterialTheme.typography.bodySmall)
                                            TextButton(
                                                onClick = { uriHandler.openUri("https://foleyit.com/ticket-terms") },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                Text("Terms & Conditions", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
