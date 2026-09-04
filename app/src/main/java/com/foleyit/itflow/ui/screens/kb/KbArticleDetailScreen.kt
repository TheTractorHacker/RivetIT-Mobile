package com.foleyit.itflow.ui.screens.kb

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.KbArticleDetail
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KbArticleDetailScreen(id: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<KbArticleDetail>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        state = runCatching { ApiClient.service().getKbArticle(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.getOrNull()?.title ?: "Article") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val result = state) {
            null -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingScreen() }
            else -> result.fold(
                onSuccess = { article ->
                    ArticleContent(article, Modifier.fillMaxSize().padding(padding))
                },
                onFailure = {
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        ErrorScreen(it.message ?: "Failed to load article") {
                            scope.launch { state = runCatching { ApiClient.service().getKbArticle(id) } }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ArticleContent(article: KbArticleDetail, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val baseUrl = ApiClient.serverUrl

    Column(modifier) {
        article.categoryName?.let { category ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        category,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            openExternalUrl(context, request.url)
                            return true
                        }
                    }
                }
            },
            update = { webView ->
                val html = """
                    <html><head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { font-family: sans-serif; padding: 16px; line-height: 1.5; font-size: 16px; }
                        img { max-width: 100%; height: auto; }
                        table { width: 100%; border-collapse: collapse; }
                        td, th { border: 1px solid #ccc; padding: 4px; }
                    </style>
                    </head><body>${article.content ?: ""}</body></html>
                """.trimIndent()
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
            }
        )

        if (!article.attachments.isNullOrEmpty()) {
            HorizontalDivider()
            Column(Modifier.padding(12.dp)) {
                Text("Attachments", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                article.attachments.forEach { att ->
                    val url = if (att.url.startsWith("http")) att.url else "$baseUrl${att.url}"
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(att.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { openExternalUrl(context, Uri.parse(url)) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Opens a URL sourced from KB article HTML/attachments in an external app. Restricted to http(s)
 * only — article content originates from the trusted backend today, but this is defense-in-depth
 * against a stored-HTML/URL injection issue rejecting schemes like `intent://`, `file://`, `javascript:`.
 */
private fun openExternalUrl(context: Context, uri: Uri?) {
    if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app found to open this link", android.widget.Toast.LENGTH_SHORT).show()
    }
}
