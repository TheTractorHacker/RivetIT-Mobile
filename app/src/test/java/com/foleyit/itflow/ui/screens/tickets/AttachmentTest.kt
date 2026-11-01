package com.foleyit.itflow.ui.screens.tickets

import com.foleyit.itflow.ui.screens.tickets.attachments.*
import org.junit.Assert.*
import org.junit.Test

class AttachmentTest {
    private val max = AttachmentRules.DEFAULT_MAX_BYTES

    @Test fun `extension allowlist matches the web agent list and is case insensitive`() {
        listOf("a.PDF", "b.jpg", "c.JPEG", "d.docx", "e.zip", "f.ovpn", "g.msg").forEach {
            assertNull(it, AttachmentRules.check(it, 10, max, 0))
        }
        listOf("run.exe", "x.apk", "s.svg", "p.php", "h.html", "noext", ".hidden", "trailingdot.", "a.sh", "b.js").forEach {
            assertTrue(it, AttachmentRules.check(it, 10, max, 0) is Rejection.Extension)
        }
    }

    @Test fun `double extensions use the last one`() {
        assertEquals("exe", AttachmentRules.extensionOf("invoice.pdf.exe"))
        assertTrue(AttachmentRules.check("invoice.pdf.exe", 10, max, 0) is Rejection.Extension)
        assertTrue(AttachmentRules.check("backup.exe.zip", 10, max, 0) is Rejection.Extension)
        assertNull(AttachmentRules.check("backup.tar.gz", 10, max, 0))
    }

    @Test fun `script or executable middle segments are refused like the server does`() {
        listOf("shell.php.png", "a.html.pdf", "x.EXE.zip", "p.svg.jpg", "r.phtml.txt").forEach {
            assertTrue(it, AttachmentRules.check(it, 10, max, 0) is Rejection.Extension)
        }
        assertNull(AttachmentRules.check("report.v2.final.pdf", 10, max, 0))
    }

    @Test fun `size limit boundaries and empty files`() {
        assertNull(AttachmentRules.check("a.pdf", max, max, 0))
        assertEquals(Rejection.TooLarge(max), AttachmentRules.check("a.pdf", max + 1, max, 0))
        assertEquals(Rejection.Empty, AttachmentRules.check("a.pdf", 0, max, 0))
    }

    @Test fun `count limit and duplicates`() {
        assertEquals(Rejection.TooMany, AttachmentRules.check("a.pdf", 1, max, AttachmentRules.MAX_FILES))
        assertEquals(Rejection.Duplicate, AttachmentRules.check("A.pdf", 1, max, 1, listOf("a.PDF")))
    }

    @Test fun `safe file names`() {
        assertEquals("passwd", AttachmentRules.safeFileName("../../etc/passwd"))
        assertEquals("evil.pdf", AttachmentRules.safeFileName("C:\\Users\\x\\evil.pdf"))
        assertEquals("hidden.txt", AttachmentRules.safeFileName(".hidden.txt"))
        assertEquals("a_b_.txt", AttachmentRules.safeFileName("a<b>.txt".replace(">", "_")))
        assertEquals("attachment", AttachmentRules.safeFileName(null))
        assertEquals("attachment", AttachmentRules.safeFileName("   "))
        assertEquals("attachment", AttachmentRules.safeFileName("..."))
        assertEquals("ab.txt", AttachmentRules.safeFileName("a\u0000b.txt"))
        assertEquals("a b.txt", AttachmentRules.safeFileName("a \n\t  b.txt"))
        val long = AttachmentRules.safeFileName("x".repeat(300) + ".pdf")
        assertEquals(100, long.length); assertTrue(long.endsWith(".pdf"))
        assertEquals("shot.jpg", AttachmentRules.safeFileName("content://media/shot.jpg"))
    }

    @Test fun `open mime comes from the allowlisted extension only`() {
        assertEquals("application/pdf", AttachmentRules.openMime("x.PDF"))
        assertEquals("image/png", AttachmentRules.openMime("x.png"))
        assertNull(AttachmentRules.openMime("x.apk"))
        assertNull(AttachmentRules.openMime("x.html"))
        assertNull(AttachmentRules.openMime("noext"))
        assertEquals("application/octet-stream", AttachmentRules.openMime("x.ovpn"))
    }

    @Test fun `upload mime prefers a sane picker type`() {
        assertEquals("image/jpeg", AttachmentRules.uploadMime("a.jpg", "image/jpeg"))
        assertEquals("image/jpeg", AttachmentRules.uploadMime("a.jpg", "not a mime"))
        assertEquals("image/jpeg", AttachmentRules.uploadMime("a.jpg", null))
        assertEquals("application/octet-stream", AttachmentRules.uploadMime("a.ovpn", null))
    }

    @Test fun `kinds and sizes`() {
        assertEquals(FileKind.IMAGE, AttachmentRules.kindOf("a.PNG")); assertEquals(FileKind.PDF, AttachmentRules.kindOf("a.pdf"))
        assertEquals(FileKind.SHEET, AttachmentRules.kindOf("a.csv")); assertEquals(FileKind.ARCHIVE, AttachmentRules.kindOf("a.gz"))
        assertEquals(FileKind.IMAGE, AttachmentRules.kindOf("noext", "image/heic")); assertEquals(FileKind.OTHER, AttachmentRules.kindOf("noext"))
        assertEquals("999 B", AttachmentRules.formatSize(999)); assertEquals("1.0 KB", AttachmentRules.formatSize(1024))
        assertEquals("10.0 MB", AttachmentRules.formatSize(10L * 1024 * 1024))
    }

    // ── queue ──

    @Test fun `queue add, reject and remove`() {
        var q = AttachmentQueue()
        val a = q.add("content://1", "../a.pdf", 100, "application/pdf", max)
        assertNull(a.rejection); q = a.queue
        assertEquals("a.pdf", q.items.single().name)
        val bad = q.add("content://2", "b.exe", 100, null, max)
        assertTrue(bad.rejection is Rejection.Extension); assertEquals(1, bad.queue.items.size)
        val big = q.add("content://3", "c.zip", max + 1, null, max)
        assertTrue(big.rejection is Rejection.TooLarge)
        val dup = q.add("content://4", "A.PDF", 5, null, max)
        assertEquals(Rejection.Duplicate, dup.rejection)
        q = q.remove(q.items.single().id)
        assertTrue(q.items.isEmpty())
    }

    @Test fun `queue ids stay unique after removal`() {
        var q = AttachmentQueue().add("u1", "a.pdf", 1, null, max).queue
        val firstId = q.items[0].id
        q = q.remove(firstId).add("u2", "b.pdf", 1, null, max).queue
        assertNotEquals(firstId, q.items[0].id)
    }

    @Test fun `queue stops at the file limit`() {
        var q = AttachmentQueue()
        repeat(AttachmentRules.MAX_FILES) { q = q.add("u$it", "f$it.pdf", 1, null, max).queue }
        assertEquals(Rejection.TooMany, q.add("x", "extra.pdf", 1, null, max).rejection)
    }

    @Test fun `upload state machine - failed files retry, done files are not re-sent`() {
        var q = AttachmentQueue()
        q = q.add("u1", "a.pdf", 10, null, max).queue.add("u2", "b.pdf", 10, null, max).queue
        val (a, b) = q.items.map { it.id }
        assertEquals(listOf(a, b), q.toUpload.map { it.id })
        q = q.uploading(a, 50)
        assertTrue(q.isBusy); assertEquals(UploadState.Uploading(50), q.items[0].state)
        q = q.uploading(a, 500)
        assertEquals(UploadState.Uploading(100), q.items[0].state) // clamped
        q = q.done(a).failed(b, "boom")
        assertTrue(q.hasFailures); assertFalse(q.allDone); assertTrue(q.toUpload.isEmpty())
        q = q.retry(b)
        assertEquals(listOf(b), q.toUpload.map { it.id }); assertFalse(q.hasFailures)
        q = q.done(b)
        assertTrue(q.allDone); assertTrue(q.toUpload.isEmpty())
        assertFalse(AttachmentQueue().allDone)
    }
}
