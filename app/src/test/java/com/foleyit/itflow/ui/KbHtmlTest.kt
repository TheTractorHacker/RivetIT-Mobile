package com.foleyit.itflow.ui

import com.foleyit.itflow.ui.screens.kb.KbColors
import com.foleyit.itflow.ui.screens.kb.KbFile
import com.foleyit.itflow.ui.screens.kb.buildKbDocument
import com.foleyit.itflow.ui.screens.kb.escapeHtml
import com.foleyit.itflow.ui.screens.kb.linkCredentialTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KbHtmlTest {
    private fun colors(dark: Boolean) = KbColors("#101010", "#EEEEEE", "#AAAAAA", "#66AAFF", "#333333", "#222222", dark)

    @Test fun escapesHtmlSpecials() {
        assertEquals("&lt;b&gt; &amp; &quot;x&quot; &#39;y&#39;", escapeHtml("<b> & \"x\" 'y'"))
    }

    @Test fun titleAndAttachmentNamesCannotInjectMarkup() {
        val doc = buildKbDocument("<script>alert(1)</script>", null, null, "<p>ok</p>",
            listOf(KbFile("a\"><img src=x>", "https://s/u/\"x")), colors(false))
        assertFalse(doc.contains("<script>alert(1)</script>"))
        assertTrue(doc.contains("&lt;script&gt;"))
        assertFalse(doc.contains("a\"><img"))
        assertTrue(doc.contains("<p>ok</p>"))          // the article body itself is kept as authored
    }

    @Test fun showsCategoryDateAndAttachmentsInsideThePage() {
        val doc = buildKbDocument("T", "Networking", "2026-11-03 12:00:00", "<p>b</p>",
            listOf(KbFile("guide.pdf", "https://s/uploads/kb/guide.pdf")), colors(false))
        assertTrue(doc.contains("Networking")); assertTrue(doc.contains("Updated 2026-11-03"))
        assertFalse(doc.contains("12:00:00"))
        assertTrue(doc.contains("href=\"https://s/uploads/kb/guide.pdf\">guide.pdf"))
        assertTrue(doc.trimStart().startsWith("<!DOCTYPE html>"))
    }

    @Test fun noMetaOrAttachmentsWhenAbsent() {
        val doc = buildKbDocument("T", null, null, null, emptyList(), colors(false))
        assertFalse(doc.contains("class=\"pill\"")); assertFalse(doc.contains("Attachments"))
    }

    @Test fun darkModeOverridesBakedInColoursLightDoesNot() {
        assertTrue(buildKbDocument("T", null, null, "<p style=\"color:#000\">x</p>", emptyList(), colors(true)).contains("color: inherit !important"))
        assertFalse(buildKbDocument("T", null, null, "<p>x</p>", emptyList(), colors(false)).contains("color: inherit !important"))
    }

    @Test fun credentialTokensBecomeInAppLinksOnlyForNumericIds() {
        assertEquals("Use: <a class=\"cred\" href=\"rivetit://credentials/125\">Open credential</a>.", linkCredentialTokens("Use: [[credential:125]]."))
        assertEquals("[[credential:abc]]", linkCredentialTokens("[[credential:abc]]"))
    }
}
