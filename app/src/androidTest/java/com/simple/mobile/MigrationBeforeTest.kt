package com.simple.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.simple.mobile.data.local.EncryptedReadingStore
import com.simple.mobile.data.parser.CubacelParser
import com.simple.mobile.data.recharge.BankPinVault
import com.simple.mobile.data.updates.AppUpdates
import com.simple.mobile.domain.carrier.CubacelQuery
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit, synthetic-only two-install probe. Never run on a user's phone. */
class MigrationBeforeTest {
    @Test fun seedPublishedVersionAndCheckItsUpdater() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("migrationStage") == "before")
        check(android.os.Build.MODEL.startsWith("sdk_gphone"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        assertEquals(22, info.versionCode)
        context.deleteDatabase("migration-probe.db")
        EncryptedReadingStore(context, "migration-probe.db").use { store ->
            store.save(900099, CubacelParser().parse(CubacelQuery.BALANCE, "Saldo: 12.34 CUP", 123L))
            store.saveLineProfile(900099, "Synthetic migration", "55500000")
            store.saveRecipient(null, "Synthetic recipient", "55500001", true)
        }
        val pin = BankPinVault(context, 900099)
        pin.forget(); pin.save("4826".toCharArray())
        assertTrue(context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE).getBoolean("900099.automatic", false))
        context.getSharedPreferences("migration_probe", Context.MODE_PRIVATE).edit()
            .putInt("uid", android.os.Process.myUid()).putString("marker", "synthetic-v22").commit()
        // The rotated APK is staged in the old debug app's cache by the test script.
        AppUpdates(context.applicationContext as android.app.Application).validate(23)
    }
}
