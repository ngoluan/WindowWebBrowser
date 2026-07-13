package com.windowweb.browser.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevServerEndpointPolicyTest {
    @Test
    fun permitsPrivateAndLocalEndpointsInRelease() {
        assertTrue(DevServerEndpointPolicy.isEligible("192.168.1.162", debugBuild = false))
        assertTrue(DevServerEndpointPolicy.isEligible("kvm2", debugBuild = false))
        assertTrue(DevServerEndpointPolicy.isEligible("service.local", debugBuild = false))
        assertTrue(DevServerEndpointPolicy.isEligible("::1", debugBuild = false))
    }

    @Test
    fun blocksPublicEndpointsInRelease() {
        assertFalse(DevServerEndpointPolicy.isEligible("example.com", debugBuild = false))
        assertFalse(DevServerEndpointPolicy.isEligible("8.8.8.8", debugBuild = false))
    }

    @Test
    fun debugBuildCanExplicitlyApproveAnyEndpoint() {
        assertTrue(DevServerEndpointPolicy.isEligible("dev.example.com", debugBuild = true))
    }
}
