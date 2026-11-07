package com.foleyit.itflow.ui.screens.kb

/** Theme colours as CSS hex strings ("#RRGGBB"), taken from the app's Material scheme so an article matches the app. */
data class KbColors(
    val background: String,
    val text: String,
    val muted: String,
    val link: String,
    val border: String,
    val codeBackground: String,
    val dark: Boolean,
)

data class KbFile(val name: String, val url: String)

internal fun escapeHtml(raw: String): String = buildString(raw.length) {
    for (c in raw) when (c) {
        '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;")
        '"' -> append("&quot;"); '\'' -> append("&#39;"); else -> append(c)
    }
}

private val credentialToken = Regex("""\[\[credential:(\d+)]]""")

/** The web app turns `[[credential:123]]` into a button; here it becomes a link the reader routes to that credential. */
internal fun linkCredentialTokens(html: String): String =
    credentialToken.replace(html) { "<a class=\"cred\" href=\"$CREDENTIAL_LINK_PREFIX${it.groupValues[1]}\">Open credential</a>" }

/** Scheme the reader intercepts; never leaves the app. */
const val CREDENTIAL_LINK_PREFIX = "rivetit://credentials/"

/**
 * The whole article as one scrolling page: title, category and date, the body, then attachments. Nothing sits outside
 * the WebView, so the reader gets the full screen. Colours follow the app theme; in dark mode the colours and
 * backgrounds the editor baked into the article (`style="color:#000"`) are overridden so text can never vanish.
 * JavaScript stays off in the WebView, and link taps are filtered to http(s) by the caller.
 */
fun buildKbDocument(
    title: String,
    category: String?,
    updated: String?,
    bodyHtml: String?,
    attachments: List<KbFile>,
    colors: KbColors,
): String {
    val meta = listOfNotNull(
        category?.takeIf { it.isNotBlank() }?.let { "<span class=\"pill\">${escapeHtml(it)}</span>" },
        updated?.take(10)?.takeIf { it.isNotBlank() }?.let { "<span class=\"when\">Updated ${escapeHtml(it)}</span>" },
    ).joinToString(" ")
    val files = if (attachments.isEmpty()) "" else buildString {
        append("<section class=\"files\"><h2>Attachments</h2>")
        attachments.forEach { append("<a class=\"file\" href=\"${escapeHtml(it.url)}\">${escapeHtml(it.name)}</a>") }
        append("</section>")
    }
    // Unconditional dark-mode neutraliser comes first so the code-block rules after it can win on order.
    val darkFix = if (colors.dark) """
        .body * { color: inherit !important; background-color: transparent !important; border-color: ${colors.border} !important; }
        .body a, .body a * { color: ${colors.link} !important; }
        .body pre, .body code { background-color: ${colors.codeBackground} !important; }
    """.trimIndent() else ""
    return """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta name="color-scheme" content="${if (colors.dark) "dark" else "light"}">
        <style>
          html { background: ${colors.background}; -webkit-text-size-adjust: 100%; }
          body { margin: 0; padding: 16px 18px 40px; background: ${colors.background}; color: ${colors.text};
                 font: 16px/1.6 -apple-system, Roboto, "Segoe UI", sans-serif; overflow-wrap: anywhere; }
          h1.title { font-size: 1.55em; line-height: 1.25; margin: 4px 0 10px; font-weight: 700; }
          .meta { margin-bottom: 18px; color: ${colors.muted}; font-size: 0.85em; }
          .pill { display: inline-block; padding: 2px 10px; border-radius: 999px; border: 1px solid ${colors.border}; margin-right: 8px; }
          .body h1, .body h2, .body h3, .body h4 { line-height: 1.3; margin: 1.4em 0 0.5em; }
          .body h1 { font-size: 1.4em; } .body h2 { font-size: 1.25em; } .body h3 { font-size: 1.1em; } .body h4 { font-size: 1em; }
          .body p, .body ul, .body ol, .body pre, .body blockquote { margin: 0 0 1em; }
          .body ul, .body ol { padding-left: 1.4em; } .body li { margin: 0.25em 0; }
          .body a { color: ${colors.link}; }
          .body img, .body video, .body iframe { max-width: 100%; height: auto; border-radius: 8px; }
          .body blockquote { margin-left: 0; padding: 2px 14px; border-left: 3px solid ${colors.border}; color: ${colors.muted}; }
          .body pre { padding: 12px; border-radius: 8px; overflow-x: auto; background: ${colors.codeBackground}; font-size: 0.88em; line-height: 1.45; }
          .body code { padding: 1px 5px; border-radius: 4px; background: ${colors.codeBackground}; font-size: 0.9em; }
          .body pre code { padding: 0; background: transparent; }
          .body table { display: block; overflow-x: auto; max-width: 100%; border-collapse: collapse; margin: 0 0 1em; }
          .body td, .body th { border: 1px solid ${colors.border}; padding: 6px 10px; vertical-align: top; }
          .body th { text-align: left; font-weight: 600; }
          .body hr { border: 0; border-top: 1px solid ${colors.border}; margin: 1.5em 0; }
          .body a.cred { display: inline-block; padding: 8px 14px; border: 1px solid ${colors.link}; border-radius: 999px; text-decoration: none; font-weight: 600; }
          .files { margin-top: 28px; padding-top: 14px; border-top: 1px solid ${colors.border}; }
          .files h2 { font-size: 0.8em; letter-spacing: 0.05em; text-transform: uppercase; color: ${colors.muted}; margin: 0 0 8px; }
          .file { display: block; padding: 10px 12px; margin-bottom: 8px; border: 1px solid ${colors.border}; border-radius: 10px;
                  color: ${colors.link}; text-decoration: none; }
          $darkFix
        </style></head><body>
        <h1 class="title">${escapeHtml(title)}</h1>
        <div class="meta">$meta</div>
        <div class="body">${linkCredentialTokens(bodyHtml.orEmpty())}</div>
        $files
        </body></html>
    """.trim()
}
