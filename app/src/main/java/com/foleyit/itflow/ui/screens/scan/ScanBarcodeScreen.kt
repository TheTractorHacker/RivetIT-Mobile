package com.foleyit.itflow.ui.screens.scan

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.ui.navigation.Screen
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val ScanLineColor = Color(0xFF00E5A0)
private val MatchColor = Color(0xFF2ECC71)
private val NoMatchColor = Color(0xFFE74C3C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBarcodeScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanning by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Point camera at barcode or serial number") }
    var notFoundCode by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var borderTarget by remember { mutableStateOf(Color.White) }
    val borderColor by animateColorAsState(borderTarget, tween(200), label = "scanBorderColor")

    // Short confirmation/error tones — no asset files needed, ToneGenerator is the
    // standard lightweight approach for scanner-style beeps.
    val toneGenerator = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { toneGenerator?.release() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun flashBorder(color: Color) {
        borderTarget = color
        scope.launch {
            delay(600)
            borderTarget = Color.White
        }
    }

    fun onBarcodeFound(raw: String) {
        if (!scanning) return
        scanning = false

        // Decode confirmation — fires the instant a code is read, before the async
        // lookup, matching how a physical barcode scanner beeps on decode rather
        // than waiting on a server round-trip.
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }

        status = "Found: $raw — searching…"
        scope.launch {
            val result = runCatching { ApiClient.service().getAssets(search = raw) }.getOrNull()
            val first = result?.data?.firstOrNull()
            if (first != null) {
                flashBorder(MatchColor)
                delay(250) // let the success flash register before navigating away
                navController.navigate(Screen.AssetDetail.go(first.id)) {
                    popUpTo(Screen.ScanBarcode.route) { inclusive = true }
                }
            } else {
                flashBorder(NoMatchColor)
                runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200) }
                // Leave scanning=false and surface a dialog instead of re-arming immediately -
                // the camera keeps decoding the same code every frame while it's still in view,
                // so resuming here would re-fire this lookup (and the NACK beep/red flash)
                // dozens of times a second until the user physically moved the camera.
                status = "Point camera at barcode or serial number"
                notFoundCode = raw
            }
        }
    }

    notFoundCode?.let { code ->
        AlertDialog(
            onDismissRequest = { notFoundCode = null; scanning = true },
            title = { Text("No Asset Found") },
            text = { Text("No asset matches the scanned code \"$code\".") },
            confirmButton = {
                TextButton(onClick = { notFoundCode = null; scanning = true }) { Text("Scan Again") }
            },
            dismissButton = {
                TextButton(onClick = { navController.popBackStack() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Barcode") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (hasCameraPermission) {
                        IconButton(onClick = {
                            val cam = camera ?: return@IconButton
                            if (cam.cameraInfo.hasFlashUnit()) {
                                torchOn = !torchOn
                                cam.cameraControl.enableTorch(torchOn)
                            }
                        }) {
                            Icon(
                                if (torchOn) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn,
                                "Toggle flashlight"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasCameraPermission -> {
                    // Permission denied — show explanation
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📷", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(16.dp))
                        Text("Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Camera access is needed to scan asset barcodes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                }
                else -> {
                    // Camera preview
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val provider = future.get()
                                val preview = Preview.Builder().build()
                                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(
                                    Executors.newSingleThreadExecutor(),
                                    ZxingAnalyzer { raw -> if (scanning) onBarcodeFound(raw) }
                                )
                                runCatching {
                                    provider.unbindAll()
                                    camera = provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview, analysis
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Viewfinder box — border flashes green/red on match/no-match, and a
                    // sweeping line animates while actively scanning for a more alive,
                    // "yes it's working" feel than a static frame.
                    Box(
                        Modifier.align(Alignment.Center).size(240.dp)
                            .border(3.dp, borderColor, MaterialTheme.shapes.medium)
                    ) {
                        if (scanning) {
                            val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
                            val lineProgress by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "scanLineProgress"
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .offset(y = (236 * lineProgress).dp)
                                    .background(ScanLineColor)
                            )
                        }
                    }

                    // Status label
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            status,
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private class ZxingAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {
    // TRY_HARDER trades a little decode latency for a real tolerance boost on skewed/lower
    // quality captures — worthwhile here since we no longer bail after the first miss per frame.
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.TRY_HARDER to true))
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val plane = imageProxy.planes.firstOrNull()
        if (plane != null) {
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            // CameraX delivers the Y-plane in sensor orientation; rotationDegrees is how much it
            // must be rotated clockwise to appear upright. QR decodes fine without this because
            // its finder patterns are orientation-invariant, but 1D linear barcodes (UPC/EAN/
            // Code128/Codabar/ITF — what asset serial labels actually use) are direction-sensitive
            // and fail outright when scanned sideways. This was the actual "only QR scans" bug.
            val (rotated, w, h) = rotateYuvPlane(
                data, imageProxy.width, imageProxy.height, imageProxy.imageInfo.rotationDegrees
            )
            val source = PlanarYUVLuminanceSource(rotated, w, h, 0, 0, w, h, false)
            runCatching { onResult(reader.decode(BinaryBitmap(HybridBinarizer(source))).text) }
        }
        imageProxy.close()
    }
}

/**
 * Rotates a single-byte-per-pixel plane (here, a YUV luma/Y plane) clockwise by
 * [rotationDegrees], which must be one of 0/90/180/270 (the only values CameraX's
 * `ImageInfo.rotationDegrees` ever reports). Returns the rotated buffer plus its new
 * (width, height) — for 90/270 these are swapped versus the input.
 *
 * Assumes the plane is tightly packed (row stride == width), matching the assumption the
 * pre-existing code already made by feeding the raw buffer straight into
 * [PlanarYUVLuminanceSource] with `width` doubling as that source's row-stride parameter.
 */
private fun rotateYuvPlane(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): Triple<ByteArray, Int, Int> {
    if (rotationDegrees == 0) return Triple(data, width, height)
    val out = ByteArray(data.size)
    return when (rotationDegrees) {
        90 -> {
            for (y in 0 until height) for (x in 0 until width) {
                out[x * height + (height - 1 - y)] = data[y * width + x]
            }
            Triple(out, height, width)
        }
        180 -> {
            val last = data.size - 1
            for (i in data.indices) out[last - i] = data[i]
            Triple(out, width, height)
        }
        270 -> {
            for (y in 0 until height) for (x in 0 until width) {
                out[(width - 1 - x) * height + y] = data[y * width + x]
            }
            Triple(out, height, width)
        }
        else -> Triple(data, width, height) // defensive fallback; CameraX never reports other values
    }
}
