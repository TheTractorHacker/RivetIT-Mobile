package com.foleyit.itflow.ui.screens.kb

import com.foleyit.itflow.ui.util.userMessage
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.KbArticleDetail
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.navigation.Screen
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
                title = { Text("Knowledge Base") },
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
                    ArticleContent(
                        article, Modifier.fillMaxSize().padding(padding),
                        onOpenCredential = { navController.navigate(Screen.CredDetail.go(it)) }
                    )
                },
                onFailure = {
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        ErrorScreen(userMessage(it)) {
                            scope.launch { state = runCatching { ApiClient.service().getKbArticle(id) } }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ArticleContent(article: KbArticleDetail, modifier: Modifier = Modifier, onOpenCredential: (Int) -> Unit) {
    val context = LocalContext.current
    val baseUrl = ApiClient.serverUrl
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme() || scheme.background.luminance() < 0.5f
    val colors = remember(scheme, dark) {
        KbColors(
            background = scheme.background.toCss(), text = scheme.onBackground.toCss(),
            muted = scheme.onSurfaceVariant.toCss(), link = scheme.primary.toCss(),
            border = scheme.outlineVariant.toCss(), codeBackground = scheme.surfaceContainerHigh.toCss(), dark = dark,
        )
    }
    // Built once per article/theme. Rebuilding on every recomposition made the page reload and jump back to the top.
    val document = remember(article, colors) {
        buildKbDocument(
            title = article.title, category = article.categoryName, updated = article.updatedAt, bodyHtml = article.content,
            attachments = article.attachments.orEmpty().map { KbFile(it.name, if (it.url.startsWith("http")) it.url else "$baseUrl${it.url}") },
            colors = colors,
        )
    }
    val pageBackground = scheme.background.toArgb()

    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (url.startsWith(CREDENTIAL_LINK_PREFIX)) {
                            url.removePrefix(CREDENTIAL_LINK_PREFIX).toIntOrNull()?.let(onOpenCredential)
                        } else openExternalUrl(context, request.url)
                        return true
                    }
                }
            }
        },
        update = { webView ->
            webView.setBackgroundColor(pageBackground)
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(baseUrl, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun Color.toCss(): String = "#%06X".format(toArgb() and 0xFFFFFF)

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
