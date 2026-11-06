package com.milkrun.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HlcTest {
    private class FakeWall(var now: Long) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test
    fun nowIsMonotonicWhenWallClockStallsOrGoesBack() {
        val wall = FakeWall(1_000)
        val clock = HlcClock("a", wall)
        val t1 = clock.now()
        val t2 = clock.now()
        wall.now = 900 // NTP step backwards after a reboot
        val t3 = clock.now()
        assertEquals(Hlc(1_000, 0, "a"), t1)
        assertEquals(Hlc(1_000, 1, "a"), t2)
        assertEquals(Hlc(1_000, 2, "a"), t3)
        wall.now = 1_001
        assertEquals(Hlc(1_001, 0, "a"), clock.now())
    }

    @Test
    fun observeOrdersLocalEventsAfterRemoteOnes() {
        val clock = HlcClock("phone", FakeWall(1_000))
        clock.now()
        val fromServer = Hlc(5_000, 3, "server") // server clock is ahead
        clock.observe(fromServer)
        val next = clock.now()
        assertTrue(next > fromServer, "$next should sort after $fromServer")
        assertEquals(Hlc(5_000, 5, "phone"), next)
    }

    @Test
    fun observeRejectsRemoteClockTooFarAhead() {
        val clock = HlcClock("phone", FakeWall(1_000), maxDriftMillis = 60_000)
        assertFailsWith<ClockDriftException> { clock.observe(Hlc(1_000 + 60_001, 0, "server")) }
    }

    @Test
    fun counterOverflowBorrowsNextMillisecond() {
        val clock = HlcClock("a", FakeWall(10), initial = Hlc(10, Hlc.MAX_COUNTER, "a"))
        assertEquals(Hlc(11, 0, "a"), clock.now())
    }

    private val arbHlc =
        Arb.bind(
            Arb.long(0L..9_999_999_999_999L),
            Arb.int(0..Hlc.MAX_COUNTER),
            Arb.string(1..6).filter { it.isNotEmpty() && '-' !in it && it.all { c -> c in 'a'..'z' } },
        ) { w, c, n -> Hlc(w, c, n) }

    @Test
    fun encodedFormSortsLikeTheValue() = runTest {
        checkAll(arbHlc, arbHlc) { a, b ->
            assertEquals(a.compareTo(b).coerceIn(-1, 1), a.encode().compareTo(b.encode()).coerceIn(-1, 1))
            assertEquals(a, Hlc.parse(a.encode()))
        }
    }
}
