package com.milkrun.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationTest {
    private val seeds = System.getProperty("milkrun.sim.seeds")?.toLong() ?: 300L

    @Test
    fun everySeedConvergesWithNoLostOrDoubledDelivery() {
        val failures = (1L..seeds).map { Simulation(SimConfig(seed = it)).run() }.filterNot { it.ok }
        assertTrue(failures.isEmpty(), failures.take(3).joinToString("\n") { "seed ${it.seed}: ${it.violations}" })
    }

    @Test
    fun theNetworkActuallyMisbehaves() {
        val s = SimStats().apply { (1L..20L).forEach { this += Simulation(SimConfig(seed = it)).run().stats } }
        assertTrue(
            s.droppedResponses > 0 && s.staleDuplicates > 0 && s.serverDuplicates > 0 && s.offlineFailures > 0,
            "$s",
        )
        assertTrue(s.rejected > 0, "two devices on one route should produce some rejections: $s")
        assertTrue(s.restarts > 0, "$s")
    }

    @Test
    fun aSeedReproducesTheSameRun() {
        assertEquals(Simulation(SimConfig(seed = 7)).run(), Simulation(SimConfig(seed = 7)).run())
    }

    @Test
    fun aSingleDeviceOnAPerfectNetworkNeedsOneHealRound() {
        val quiet = NetworkProfile(dropRequest = 0.0, dropResponse = 0.0, duplicate = 0.0, deadZones = 0)
        val report = Simulation(SimConfig(seed = 1, clients = 1, network = quiet, dispatcherActions = 0)).run()
        assertTrue(report.ok, "${report.violations}")
        assertEquals(1, report.stats.healRounds)
        assertEquals(0, report.stats.rejected)
    }

    @Test
    fun theChecksCatchAServerThatForgetsWhatItProcessed() {
        val caught =
            (1L..20L).count {
                !Simulation(
                    SimConfig(seed = it, faults = setOf(Fault.SERVER_FORGETS_SEQ)),
                ).run().ok
            }
        assertTrue(caught >= 18, "only $caught of 20 runs flagged a non-idempotent server")
    }

    @Test
    fun theChecksCatchAServerThatLosesAcknowledgedWrites() {
        val caught =
            (1L..20L).count {
                !Simulation(
                    SimConfig(seed = it, faults = setOf(Fault.SERVER_ROLLBACK)),
                ).run().ok
            }
        assertTrue(caught >= 18, "only $caught of 20 runs flagged a lost write")
    }
}
