package com.example.englishlearning.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

interface ClockProvider {
    fun instant(): Instant

    fun zoneId(): ZoneId
}

class SystemClockProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
) : ClockProvider {
    override fun instant(): Instant = clock.instant()

    override fun zoneId(): ZoneId = clock.zone
}

class FixedClockProvider(
    private val fixedInstant: Instant,
    private val fixedZoneId: ZoneId,
) : ClockProvider {
    override fun instant(): Instant = fixedInstant

    override fun zoneId(): ZoneId = fixedZoneId
}
