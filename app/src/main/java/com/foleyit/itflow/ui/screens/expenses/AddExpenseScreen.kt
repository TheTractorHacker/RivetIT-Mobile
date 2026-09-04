package com.foleyit.itflow.ui.screens.expenses

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.ui.components.SectionLabel
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(onDone: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var paymentMethod by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> receiptUri = uri }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            val file = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            receiptUri = Uri.fromFile(file)
        }
    }

    fun submit() {
        if (description.isBlank() || amount.isBlank()) { error = "Description and amount required"; return }
        val amt = amount.toDoubleOrNull() ?: run { error = "Invalid amount"; return }
        loading = true; error = null
        scope.launch {
            try {
                val descBody = description.toRequestBody("text/plain".toMediaType())
                val amtBody = amt.toString().toRequestBody("text/plain".toMediaType())
                val dateBody = date.toRequestBody("text/plain".toMediaType())
                val currBody = "USD".toRequestBody("text/plain".toMediaType())
                val refBody = reference.toRequestBody("text/plain".toMediaType())
                val pmBody = paymentMethod.toRequestBody("text/plain".toMediaType())
                val receiptPart = receiptUri?.let { uri ->
                    val stream = context.contentResolver.openInputStream(uri) ?: return@let null
                    val file = File(context.cacheDir, "receipt_upload")
                    file.outputStream().use { out -> stream.copyTo(out) }
                    val mt = context.contentResolver.getType(uri) ?: "image/jpeg"
                    MultipartBody.Part.createFormData("receipt", "receipt.jpg", file.asRequestBody(mt.toMediaType()))
                }
                ApiClient.service().createExpense(descBody, amtBody, dateBody, currBody, refBody, pmBody, receiptPart)
                onDone()
            } catch (e: Exception) { error = userMessage(e) } finally { loading = false }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Add Expense") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, "Close") } }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Details")
                        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, leadingIcon = { Icon(Icons.Outlined.Description, null) }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, leadingIcon = { Icon(Icons.Outlined.AttachMoney, null) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = paymentMethod, onValueChange = { paymentMethod = it }, label = { Text("Payment Method") }, leadingIcon = { Icon(Icons.Outlined.CreditCard, null) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = reference, onValueChange = { reference = it }, label = { Text("Reference") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Receipt")
                        if (receiptUri != null) {
                            val bmp = remember(receiptUri) {
                                receiptUri?.let { uri ->
                                    context.contentResolver.openInputStream(uri)
                                        ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                                }
                            }
                            bmp?.let {
                                Image(bitmap = it, contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop)
                            }
                            OutlinedButton(onClick = { receiptUri = null }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remove") }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Camera") }
                                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Photo, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Gallery") }
                            }
                        }
                    }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            item { Button(onClick = ::submit, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !loading, shape = MaterialTheme.shapes.extraLarge) { if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save Expense") } }
        }
    }
}
