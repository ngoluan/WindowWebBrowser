package com.windowweb.browser.util

import java.util.UUID

object Ids {
    fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}
