package com.simple.mobile.data.updates

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun acceptsOnlyVersionedApkAssets() {
        assertEquals(12, UpdatePolicy.code("SIMple-12.apk"))
        assertNull(UpdatePolicy.code("SIMple-0.apk"))
        assertNull(UpdatePolicy.code("SIMple-999999999999999.apk"))
        assertNull(UpdatePolicy.code("app-debug.apk"))
    }
    @Test fun acceptsOnlyOwnReleaseAndHttpsGithubDownloadHosts() {
        assertTrue(UpdatePolicy.releaseUrl("https://github.com/deborahfam/SIMple/releases/download/v0.3/SIMple-3.apk"))
        assertFalse(UpdatePolicy.releaseUrl("https://github.com/other/SIMple/releases/download/v1/a.apk"))
        assertFalse(UpdatePolicy.downloadHost("http://github.com/a"))
        assertFalse(UpdatePolicy.downloadHost("https://github.com.evil.example/a"))
        assertFalse(UpdatePolicy.downloadHost("https://user@github.com/a"))
        assertTrue(UpdatePolicy.downloadHost("https://release-assets.githubusercontent.com/a"))
    }
}
