package com.milkrun.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StopRulesTest {
    private val stopId = StopId("S-014")
    private val p1 = ParcelId("TBA312459870001")
    private val p2 = ParcelId("TBA312459870002")
    private val stop =
        Stop(
            id = stopId,
            routeId = RouteId("R-1"),
            sequence = 14,
            address = "410 Terry Ave N, Seattle, WA",
            location = GeoPoint(47.6223, -122.3365),
            parcels = listOf(Parcel(p1, "small box"), Parcel(p2, "padded mailer")),
        )
    private val hlc = Hlc(1_000, 0, "a")

    private fun Decision.accepted(): Stop = assertIs<Decision.Accepted>(this).stop

    @Test
    fun happyPathPlannedArrivedDelivered() {
        val arrived = StopRules.apply(stop, Mutation.Arrive(stopId, 100), hlc).accepted()
        assertEquals(StopStatus.ARRIVED, arrived.status)
        val delivered =
            StopRules
                .apply(arrived, Mutation.Deliver(stopId, listOf(p1, p2), HandoffMethod.FRONT_DOOR, 200), hlc)
                .accepted()
        assertEquals(StopStatus.DELIVERED, delivered.status)
        assertEquals(100, delivered.arrivedAtMillis)
        assertEquals(200, delivered.completedAtMillis)
        assertTrue(delivered.parcels.all { it.delivered && it.scanned })
    }

    @Test
    fun deliverTwiceIsRejected() {
        val delivered =
            StopRules
                .apply(
                    stop,
                    Mutation.Deliver(stopId, listOf(p1), HandoffMethod.IN_PERSON, 1),
                    hlc,
                ).accepted()
        val again = StopRules.apply(delivered, Mutation.Deliver(stopId, listOf(p2), HandoffMethod.IN_PERSON, 2), hlc)
        assertEquals(Decision.Rejected(RejectReason.ALREADY_DELIVERED), again)
    }

    @Test
    fun deliveryWinsOverACancelTheDriverNeverSaw() {
        val cancelled = StopRules.apply(stop, DispatcherCommand.Cancel(stopId)).accepted()
        val delivered =
            StopRules
                .apply(cancelled, Mutation.Deliver(stopId, listOf(p1), HandoffMethod.MAILROOM, 5), hlc)
                .accepted()
        assertEquals(StopStatus.DELIVERED, delivered.status)
        assertEquals(ConflictFlag.DELIVERED_AFTER_CANCEL, delivered.conflict)
    }

    @Test
    fun otherDriverActionsOnACancelledStopAreRejected() {
        val cancelled = StopRules.apply(stop, DispatcherCommand.Cancel(stopId)).accepted()
        assertEquals(
            Decision.Rejected(RejectReason.STOP_CANCELLED),
            StopRules.apply(cancelled, Mutation.Arrive(stopId, 1), hlc),
        )
        assertEquals(
            Decision.Rejected(RejectReason.STOP_CANCELLED),
            StopRules.apply(cancelled, Mutation.Attempt(stopId, AttemptReason.NO_ACCESS, 1), hlc),
        )
    }

    @Test
    fun dispatcherCannotCancelADeliveredStop() {
        val delivered =
            StopRules
                .apply(
                    stop,
                    Mutation.Deliver(stopId, listOf(p1), HandoffMethod.LOCKER, 1),
                    hlc,
                ).accepted()
        assertEquals(
            Decision.Rejected(RejectReason.ALREADY_DELIVERED),
            StopRules.apply(delivered, DispatcherCommand.Cancel(stopId)),
        )
    }

    @Test
    fun attemptThenReattemptThenDeliver() {
        val attempted = StopRules.apply(stop, Mutation.Attempt(stopId, AttemptReason.NOBODY_HOME, 10), hlc).accepted()
        assertEquals(AttemptReason.NOBODY_HOME, attempted.attemptReason)
        val back = StopRules.apply(attempted, Mutation.Arrive(stopId, 20), hlc).accepted()
        assertEquals(StopStatus.ARRIVED, back.status)
        assertEquals(null, back.attemptReason)
    }

    @Test
    fun unknownParcelIsRejected() {
        val result = StopRules.apply(stop, Mutation.ScanParcel(stopId, ParcelId("NOT-ON-TRUCK"), 1), hlc)
        assertEquals(Decision.Rejected(RejectReason.UNKNOWN_PARCEL), result)
    }

    @Test
    fun notesAreLastWriterWinsOnHlc() {
        val newer = Hlc(2_000, 0, "b")
        val older = Hlc(1_500, 9, "a")
        val s1 = StopRules.apply(stop, Mutation.SetNote(stopId, "gate code 4512"), newer).accepted()
        val s2 = StopRules.apply(s1, Mutation.SetNote(stopId, "leave at back"), older).accepted()
        assertEquals("gate code 4512", s2.note)
        assertEquals(newer, s2.noteHlc)
    }

    private val arbMutation: Arb<Mutation> =
        Arb.choice(
            Arb.long(0L..1_000L).map { Mutation.Arrive(stopId, it) },
            Arb.element(p1, p2, ParcelId("X")).map { Mutation.ScanParcel(stopId, it, 1) },
            Arb.list(Arb.element(p1, p2), 0..2).map { Mutation.Deliver(stopId, it, HandoffMethod.FRONT_DOOR, 2) },
            Arb.enum<AttemptReason>().map { Mutation.Attempt(stopId, it, 3) },
        )

    @Test
    fun deliveredIsTerminalForAnySequenceOfMutations() = runTest {
        checkAll(Arb.list(arbMutation, 1..20)) { mutations ->
            var s = stop
            var deliveredOnce = false
            for (m in mutations) {
                val d = StopRules.apply(s, m, hlc)
                if (d is Decision.Accepted) s = d.stop
                if (deliveredOnce) assertEquals(StopStatus.DELIVERED, s.status)
                if (s.status == StopStatus.DELIVERED) deliveredOnce = true
            }
        }
    }
}
