package com.simple.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.telephony.SmsImportSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DistributionSecurityTest {
    @Test fun permissionsMatchDistributionAndHttpIsBlocked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS)
        val permissions = info.requestedPermissions.orEmpty().toSet()
        for (permission in listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
            Manifest.permission.INTERNET, Manifest.permission.REQUEST_INSTALL_PACKAGES)) {
            assertEquals(permission, BuildConfig.DIRECT_DISTRIBUTION, permission in permissions)
        }
        assertEquals(BuildConfig.DIRECT_DISTRIBUTION, info.providers.orEmpty().any { it.authority == "${context.packageName}.updates" })
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @Test fun playIgnoresSmsConsentFromAnOlderDirectInstallation() {
        if (BuildConfig.DIRECT_DISTRIBUTION) return
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "distribution_security_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            prefs.edit().putBoolean("carrier_sms", true).putBoolean("bank_sms", true).commit()
            val settings = SmsImportSettings(context, name)
            settings.setEnabled(true); settings.setBankEnabled(true)
            assertFalse(settings.state.value.enabled)
            assertFalse(settings.state.value.bankEnabled)
            assertNull(settings.token())
            assertNull(settings.beginSync())
        } finally { prefs.edit().clear().commit() }
    }
}
