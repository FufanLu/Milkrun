package com.milkrun.sync

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Capped exponential backoff with full jitter: `sleep = random(0, min(cap, base * 2^attempt))`.
 *
 * Full jitter spreads a fleet of phones that lost signal at the same time (a whole delivery
 * station going through one tunnel) instead of having them retry in lock-step.
 * See "Exponential Backoff And Jitter", AWS Architecture Blog, and the Builders' Library article on
 * timeouts, retries and backoff.
 */
public class Backoff(
    private val base: Duration = 1.seconds,
    private val cap: Duration = 5.minutes,
    private val random: Random,
) {
    public var attempt: Int = 0
        private set

    public fun next(): Duration {
        val ceiling = (base.inWholeMilliseconds shl minOf(attempt, MAX_SHIFT)).coerceAtMost(cap.inWholeMilliseconds)
        attempt++
        return random.nextLong(0, ceiling + 1).milliseconds
    }

    public fun reset() {
        attempt = 0
    }

    private companion object {
        const val MAX_SHIFT = 20
    }
}
