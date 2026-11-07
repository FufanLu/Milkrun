package com.milkrun.protocol

import kotlin.random.Random

/**
 * W3C Trace Context (`traceparent: 00-<trace-id>-<span-id>-01`). The phone starts a trace per
 * request; the server continues it, logs every line with the trace id, and echoes it back in
 * `traceresponse`, so a driver's "it didn't sync" can be matched to the exact server log lines.
 */
public data class Traceparent(val traceId: String, val spanId: String, val sampled: Boolean = true) {
    init {
        require(traceId.length == 32 && traceId.all(::isHex) && traceId != "0".repeat(32)) { "bad trace id" }
        require(spanId.length == 16 && spanId.all(::isHex) && spanId != "0".repeat(16)) { "bad span id" }
    }

    public fun encode(): String = "00-$traceId-$spanId-${if (sampled) "01" else "00"}"

    /** Same trace, new span: what a server hop or a retry gets. */
    public fun child(random: Random = Random.Default): Traceparent = copy(spanId = hex(random, 8))

    public companion object {
        public const val HEADER: String = "traceparent"
        public const val RESPONSE_HEADER: String = "traceresponse"

        public fun random(random: Random = Random.Default): Traceparent = Traceparent(hex(random, 16), hex(random, 8))

        /** Null for anything that is not a version-00 traceparent, as the spec requires. */
        public fun parse(header: String?): Traceparent? {
            val parts = header?.trim()?.split('-') ?: return null
            if (parts.size != 4 || parts[0] != "00" || parts[3].length != 2) return null
            return runCatching { Traceparent(parts[1].lowercase(), parts[2].lowercase(), parts[3] == "01") }.getOrNull()
        }

        private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f'

        private fun hex(random: Random, bytes: Int): String =
            random.nextBytes(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
