package com.windowweb.browser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlInputParserTest {
    @Test
    fun repairsMissingHttpsColon() {
        assertEquals("https://example.com", UrlInputParser.parse("https//example.com"))
    }

    @Test
    fun repairsSingleSlashHttps() {
        assertEquals("https://example.com/path", UrlInputParser.parse("https:/example.com/path"))
    }

    @Test
    fun doesNotTreatAWordBeginningWithHttpsAsAUrl() {
        assertEquals(
            "https://www.google.com/search?q=httpsomething",
            UrlInputParser.parse("httpsomething")
        )
    }

    @Test
    fun supportsLocalhostWithPort() {
        assertEquals("https://localhost:8443/login", UrlInputParser.parse("localhost:8443/login"))
    }

    @Test
    fun createsExplicitHttpFallback() {
        assertEquals(
            "http://example.com:443/login?q=1",
            UrlInputParser.httpFallback("https://example.com:443/login?q=1")
        )
    }

    @Test
    fun doesNotDowngradeNonHttpsUrl() {
        assertNull(UrlInputParser.httpFallback("http://example.com"))
    }
}
