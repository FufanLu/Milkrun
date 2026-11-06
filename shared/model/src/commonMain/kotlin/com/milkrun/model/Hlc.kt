package com.milkrun.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Hybrid logical clock timestamp (Kulkarni et al., 2014).
 *
 * Ordered by wall time, then counter, then node, so two timestamps from different devices are never
 * equal. The string form sorts the same way as the value, which lets SQLite and Postgres compare
 * them as text:
 *
 * ```
 * 0001762419600123-0002-c7f3a9
 * └ wall millis ─┘ └ctr┘ └node┘
 * ```
 */
@Serializable(with = HlcSerializer::class)
public data class Hlc(val wallMillis: Long, val counter: Int, val node: String) : Comparable<Hlc> {
    init {
        require(wallMillis >= 0) { "wallMillis must be >= 0" }
        require(counter in 0..MAX_COUNTER) { "counter out of range: $counter" }
        require(node.isNotEmpty() && '-' !in node) { "node must be non-empty and contain no '-'" }
    }

    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, Hlc::wallMillis, Hlc::counter, Hlc::node)

    public fun encode(): String = wallMillis.toString().padStart(WALL_DIGITS, '0') + "-" +
        counter.toString(16).padStart(COUNTER_DIGITS, '0') + "-" + node

    override fun toString(): String = encode()

    public companion object {
        internal const val WALL_DIGITS: Int = 16
        internal const val COUNTER_DIGITS: Int = 4
        public const val MAX_COUNTER: Int = 0xFFFF

        public val ZERO: Hlc = Hlc(0, 0, "0")

        public fun parse(text: String): Hlc {
            val parts = text.split('-', limit = 3)
            require(parts.size == 3) { "not an HLC: $text" }
            return Hlc(parts[0].toLong(), parts[1].toInt(16), parts[2])
        }
    }
}

internal object HlcSerializer : KSerializer<Hlc> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.milkrun.Hlc", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Hlc): Unit = encoder.encodeString(value.encode())

    override fun deserialize(decoder: Decoder): Hlc = Hlc.parse(decoder.decodeString())
}

/** Thrown when a remote timestamp is further ahead of the local wall clock than [HlcClock.maxDriftMillis]. */
public class ClockDriftException(public val remote: Hlc, public val localWallMillis: Long) :
    IllegalStateException("remote clock $remote is too far ahead of local wall clock $localWallMillis")

/**
 * Issues [Hlc] timestamps for one node.
 *
 * Not thread-safe: each instance is owned by one component that already serialises its writes
 * (the client's sync engine, or one server transaction).
 */
public class HlcClock(
    private val node: String,
    private val wallClock: () -> Long,
    initial: Hlc = Hlc.ZERO,
    public val maxDriftMillis: Long = 60_000,
) {
    public var last: Hlc = initial
        private set

    /** Timestamp for a local event. Monotonic even if the wall clock moves backwards. */
    public fun now(): Hlc {
        val wall = wallClock()
        last =
            if (wall > last.wallMillis) {
                Hlc(wall, 0, node)
            } else {
                bump(last.wallMillis, last.counter)
            }
        return last
    }

    /** Merge a timestamp received from another node, so later local events sort after it. */
    public fun observe(remote: Hlc): Hlc {
        val wall = wallClock()
        if (remote.wallMillis - wall > maxDriftMillis) throw ClockDriftException(remote, wall)
        val maxWall = maxOf(wall, last.wallMillis, remote.wallMillis)
        last =
            when {
                maxWall == last.wallMillis && maxWall == remote.wallMillis -> {
                    bump(maxWall, maxOf(last.counter, remote.counter))
                }

                maxWall == last.wallMillis -> {
                    bump(maxWall, last.counter)
                }

                maxWall == remote.wallMillis -> {
                    bump(maxWall, remote.counter)
                }

                else -> {
                    Hlc(maxWall, 0, node)
                }
            }
        return last
    }

    private fun bump(wall: Long, counter: Int): Hlc = if (counter < Hlc.MAX_COUNTER) {
        Hlc(wall, counter + 1, node)
    } else {
        // Counter exhausted within one millisecond: borrow the next millisecond.
        Hlc(wall + 1, 0, node)
    }
}
