package com.example.englishlearning.core.time

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

class ClockProviderTest {
    @Test
    fun `fixed clock returns its configured instant and zone`() {
        val expectedInstant = Instant.parse("2026-09-17T08:15:30Z")
        val expectedZone = ZoneId.of("Asia/Shanghai")
        val clock = FixedClockProvider(expectedInstant, expectedZone)

        assertEquals(expectedInstant, clock.instant())
        assertEquals(expectedZone, clock.zoneId())
    }
}
