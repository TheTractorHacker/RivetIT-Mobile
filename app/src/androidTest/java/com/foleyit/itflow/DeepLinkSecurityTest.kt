package com.foleyit.itflow

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the deep-link validation fix: a crafted `deep_link_route` Intent extra
 * (as any installed app could send to this exported launcher Activity) must never crash the app
 * or navigate anywhere — [com.foleyit.itflow.ui.navigation.DeepLinks.ALLOWED_ROUTE] should reject it.
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkSecurityTest {

    private fun launchWithDeepLink(route: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("deep_link_route", route)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertNotEquals(
                    "activity should not have finished/crashed from a malicious deep link",
                    true,
                    activity.isFinishing
                )
            }
        }
    }

    @Test
    fun maliciousPathTraversalRouteDoesNotCrash() {
        launchWithDeepLink("../../credentials/1")
    }

    @Test
    fun unknownResourceRouteDoesNotCrash() {
        launchWithDeepLink("evil_admin_panel")
    }

    @Test
    fun injectedQueryStringRouteDoesNotCrash() {
        launchWithDeepLink("tickets/1?foo=bar&redirect=http://evil.example")
    }
}
