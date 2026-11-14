package com.simple.mobile

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simple.mobile.data.parser.CubacelParser
import com.simple.mobile.domain.carrier.CubacelQuery
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Opt-in ONLY: normal test runs never contact a carrier. No purchase codes or retries. */
@RunWith(AndroidJUnit4::class)
class LiveUssdProbeTest {
    @Test fun explicitlyRequestedReadOnlyQueries() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveCubacel") == "true")
        val subscription = args.getString("subscriptionId")?.toIntOrNull() ?: error("Explicit subscription required")
        val requested = args.getString("queries")?.split(',')?.map { CubacelQuery.valueOf(it) } ?: listOf(CubacelQuery.BALANCE)
        val app = ApplicationProvider.getApplicationContext<SimpleApplication>()
        for (query in requested) {
            val response = try { app.dispatcher().queryText(subscription, query).get(40, TimeUnit.SECONDS) }
            catch (_: Exception) {
                report("${query.name}: modem request failed; stopped without retries.")
                return
            }
            val accepted = app.worker.submit<Boolean> {
                try {
                    val parsed = CubacelParser().parse(query, response, System.currentTimeMillis())
                    app.store.save(subscription, parsed)
                    app.refreshReadings()
                    true
                } catch (_: CubacelParser.ParseException) { false }
            }.get(10, TimeUnit.SECONDS)
            // Emit only a redacted grammar, never balances, subscriber numbers or raw logs.
            val grammar = response.replace(Regex("\\d"), "0")
                .replace(Regex("https?://\\S+"), "[URL]")
                .replace(Regex("(?i)(nombre|cliente|numero|telefono)\\s*[:=]\\s*[^;,\\n]+"), "$1:[REDACTED]")
            report("${query.name}: parsed=$accepted; redacted grammar: $grammar")
        }
    }
    private fun report(text: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "\n$text\n") })
    }
}
