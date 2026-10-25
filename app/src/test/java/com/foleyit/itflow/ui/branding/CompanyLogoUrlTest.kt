package com.foleyit.itflow.ui.branding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanyLogoUrlTest {
    private val server = "https://rivet.example.com"

    @Test fun resolvesUploadedLogoOnTheSameServer() {
        assertEquals(
            "https://rivet.example.com/uploads/settings/logo.png",
            companyLogoUrl(server, "/uploads/settings/logo.png"),
        )
    }

    @Test fun noLogoMeansNoUrl() {
        assertNull(companyLogoUrl(server, null))
        assertNull(companyLogoUrl(server, ""))
    }

    @Test fun refusesAnotherHostSoCredentialsAndTrafficStayOnTheServer() {
        assertNull(companyLogoUrl(server, "https://evil.example.net/uploads/settings/logo.png"))
        assertNull(companyLogoUrl(server, "//evil.example.net/uploads/settings/logo.png"))
    }

    @Test fun refusesPathsOutsideTheSettingsUploadFolder() {
        assertNull(companyLogoUrl(server, "/api/v1/me"))
        assertNull(companyLogoUrl(server, "/uploads/clients/secret.pdf"))
    }
}
