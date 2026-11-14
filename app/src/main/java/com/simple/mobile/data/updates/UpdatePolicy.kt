package com.simple.mobile.data.updates

import java.net.URI

internal object UpdatePolicy {
    fun code(name: String): Int? = Regex("SIMple-([1-9][0-9]*)\\.apk").matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
    fun releaseUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.rawUserInfo == null && uri.port == -1 &&
            uri.path.startsWith("/deborahfam/SIMple/releases/download/")
    }.getOrDefault(false)
    fun downloadHost(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1 && uri.host in setOf(
            "api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)
}
