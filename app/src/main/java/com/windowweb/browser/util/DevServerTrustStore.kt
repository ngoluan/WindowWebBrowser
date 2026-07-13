package com.windowweb.browser.util

import android.content.Context
import java.util.Locale

/**
 * Stores explicit user-approved exceptions for development servers with invalid/self-signed TLS.
 *
 * These exceptions are host-scoped and are only used after the user chooses
 * "Always trust this host" from the browser warning. They do not disable
 * certificate validation globally.
 */
object DevServerTrustStore {
    private const val PREFS_NAME = "windowweb_dev_server_tls"
    private const val TRUSTED_HOSTS_KEY = "trusted_hosts"

    fun isTrusted(context: Context, host: String): Boolean {
        val normalized = normalizeHost(host)
        if (normalized.isBlank()) return false
        return preferences(context)
            .getStringSet(TRUSTED_HOSTS_KEY, emptySet())
            .orEmpty()
            .contains(normalized)
    }

    fun trust(context: Context, host: String) {
        val normalized = normalizeHost(host)
        if (normalized.isBlank()) return

        val current = preferences(context)
            .getStringSet(TRUSTED_HOSTS_KEY, emptySet())
            .orEmpty()
            .toMutableSet()
        current += normalized
        preferences(context).edit().putStringSet(TRUSTED_HOSTS_KEY, current).apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().remove(TRUSTED_HOSTS_KEY).apply()
    }

    fun trustedHostCount(context: Context): Int =
        preferences(context).getStringSet(TRUSTED_HOSTS_KEY, emptySet()).orEmpty().size

    private fun normalizeHost(host: String): String =
        host.trim().trimEnd('.').lowercase(Locale.US)

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
