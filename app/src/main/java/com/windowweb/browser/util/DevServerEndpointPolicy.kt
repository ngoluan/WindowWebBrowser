package com.windowweb.browser.util

import java.util.Locale

/**
 * Limits certificate exceptions to development-like endpoints in release builds.
 * Debug builds may explicitly approve any endpoint.
 */
object DevServerEndpointPolicy {
    fun isEligible(host: String, debugBuild: Boolean): Boolean {
        if (debugBuild) return true

        val normalized = host
            .trim()
            .trim('[', ']')
            .trimEnd('.')
            .lowercase(Locale.US)
        if (normalized.isBlank()) return false

        if (
            normalized == "localhost" ||
            normalized.endsWith(".localhost") ||
            normalized.endsWith(".local") ||
            normalized.endsWith(".lan") ||
            normalized.endsWith(".internal") ||
            normalized.endsWith(".test")
        ) {
            return true
        }

        if (!normalized.contains('.') && !normalized.contains(':')) {
            // Single-label names such as "kvm2" are normally LAN/development hosts.
            return true
        }

        val ipv4 = normalized.split('.').mapNotNull { it.toIntOrNull() }
        if (ipv4.size == 4 && ipv4.all { it in 0..255 }) {
            return ipv4[0] == 10 ||
                ipv4[0] == 127 ||
                (ipv4[0] == 172 && ipv4[1] in 16..31) ||
                (ipv4[0] == 192 && ipv4[1] == 168) ||
                (ipv4[0] == 169 && ipv4[1] == 254)
        }

        return normalized == "::1" ||
            normalized.startsWith("fc") ||
            normalized.startsWith("fd") ||
            normalized.startsWith("fe8") ||
            normalized.startsWith("fe9") ||
            normalized.startsWith("fea") ||
            normalized.startsWith("feb")
    }
}
