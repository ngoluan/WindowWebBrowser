package com.windowweb.browser.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevServerProxyPolicyTest {
    @Test
    fun proxiesOpenCodeHealthAndStreamsOnSameOrigin() {
        val origin = "http://2.25.204.83:4096"

        assertTrue(
            DevServerProxyPolicy.shouldProxy(
                "GET",
                "http://2.25.204.83:4096/global/health",
                origin
            )
        )
        assertTrue(
            DevServerProxyPolicy.shouldProxy(
                "GET",
                "http://2.25.204.83:4096/global/event",
                origin
            )
        )
        assertTrue(
            DevServerProxyPolicy.shouldProxy(
                "GET",
                "http://2.25.204.83:4096/event?directory=%2Ftmp",
                origin
            )
        )
    }

    @Test
    fun refusesCrossOriginAndRequestsWithBodies() {
        val origin = "http://2.25.204.83:4096"

        assertFalse(
            DevServerProxyPolicy.shouldProxy(
                "GET",
                "http://other.example:4096/global/health",
                origin
            )
        )
        assertFalse(
            DevServerProxyPolicy.shouldProxy(
                "POST",
                "http://2.25.204.83:4096/global/health",
                origin
            )
        )
    }

    @Test
    fun leavesHttpsOnWebViewsCertificatePath() {
        assertFalse(
            DevServerProxyPolicy.shouldProxy(
                "GET",
                "https://dev.example/global/event",
                "https://dev.example"
            )
        )
    }

    @Test
    fun refusesUnrelatedPageResources() {
        assertFalse(
            DevServerProxyPolicy.shouldProxy(
                "GET",
                "http://2.25.204.83:4096/assets/index.js",
                "http://2.25.204.83:4096"
            )
        )
    }
}
