package com.windowweb.browser.webview

import com.windowweb.browser.core.ConsoleLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPageDiagnosticsTest {
    @Test
    fun blankSnapshotBecomesWarning() {
        val entry = BrowserPageDiagnostics.consoleEntry(
            tabId = "tab-1",
            rawResult = "\"BLANK|textLength=0|bodyChildren=0\"",
            sourceUrl = "https://dev.local"
        )

        assertEquals(ConsoleLevel.WARNING, entry.level)
        assertTrue(entry.message.contains("BLANK"))
    }

    @Test
    fun healthySnapshotBecomesInfo() {
        val entry = BrowserPageDiagnostics.consoleEntry(
            tabId = "tab-1",
            rawResult = "\"OK|textLength=20|bodyChildren=3\"",
            sourceUrl = "https://dev.local"
        )

        assertEquals(ConsoleLevel.INFO, entry.level)
    }
    @Test
    fun diagnosticsSerializeObjectsWithoutReplacingNetworkPrimitives() {
        assertTrue(BrowserPageDiagnostics.documentStartScript.contains("JSON.stringify"))
        assertFalse(BrowserPageDiagnostics.documentStartScript.contains("window.fetch ="))
        assertFalse(BrowserPageDiagnostics.documentStartScript.contains("window.EventSource ="))
        assertTrue(BrowserPageDiagnostics.documentStartScript.contains("browser reported offline"))
    }
}