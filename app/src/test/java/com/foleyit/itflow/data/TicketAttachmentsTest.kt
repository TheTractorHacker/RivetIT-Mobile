package com.foleyit.itflow.data

import com.foleyit.itflow.data.attachments.AttachmentRules
import com.foleyit.itflow.data.attachments.ticketAttachmentUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TicketAttachmentsTest {
    @Test fun allowedTypePasses() = assertNull(AttachmentRules.problem("Report.PDF", 1024))
    @Test fun disallowedTypeIsRejected() = assertNotNull(AttachmentRules.problem("run.exe", 1024))
    @Test fun noExtensionIsRejected() = assertNotNull(AttachmentRules.problem("README", 10))
    @Test fun emptyFileIsRejected() = assertNotNull(AttachmentRules.problem("a.txt", 0))
    @Test fun oversizeFileIsRejected() = assertNotNull(AttachmentRules.problem("a.zip", AttachmentRules.MAX_BYTES + 1))
    @Test fun exactlyMaxPasses() = assertNull(AttachmentRules.problem("a.zip", AttachmentRules.MAX_BYTES))
    @Test fun sizeFormatting() {
        assertEquals("512 B", AttachmentRules.humanSize(512))
        assertEquals("2.0 MB", AttachmentRules.humanSize(2L shl 20))
    }

    @Test fun sameOriginUploadPathIsAccepted() =
        assertEquals("https://x.example/uploads/tickets/12/a.pdf",
            ticketAttachmentUrl("https://x.example", "/uploads/tickets/12/a.pdf"))
    @Test fun otherHostIsRejected() =
        assertNull(ticketAttachmentUrl("https://x.example", "https://evil.example/uploads/tickets/12/a.pdf"))
    @Test fun otherSchemeIsRejected() =
        assertNull(ticketAttachmentUrl("https://x.example", "javascript:alert(1)"))
    @Test fun pathOutsideUploadsIsRejected() =
        assertNull(ticketAttachmentUrl("https://x.example", "/admin/login.php"))
    @Test fun traversalIsRejected() =
        assertNull(ticketAttachmentUrl("https://x.example", "/uploads/tickets/../../etc/passwd"))
    @Test fun userInfoIsRejected() =
        assertNull(ticketAttachmentUrl("https://x.example", "https://u:p@x.example/uploads/tickets/1/a.pdf"))
}
