package com.milkrun.protocol

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TraceparentTest {
    @Test
    fun roundTripsTheSpecExample() {
        val h = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        assertEquals(h, Traceparent.parse(h)!!.encode())
    }

    @Test
    fun rejectsWhatTheSpecRejects() {
        assertNull(Traceparent.parse("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
        assertNull(Traceparent.parse("00-00000000000000000000000000000000-00f067aa0ba902b7-01"))
        assertNull(Traceparent.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01"))
        assertNull(Traceparent.parse("00-xyz-00f067aa0ba902b7-01"))
        assertNull(Traceparent.parse(null))
    }

    @Test
    fun childKeepsTheTrace() {
        val t = Traceparent.random(Random(1))
        val c = t.child(Random(2))
        assertEquals(t.traceId, c.traceId)
        assertEquals(16, c.spanId.length)
    }
}
