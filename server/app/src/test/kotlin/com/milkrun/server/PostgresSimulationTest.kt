package com.milkrun.server

import com.milkrun.sim.SimConfig
import com.milkrun.sim.Simulation
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertTrue

/**
 * The chaos shifts from tools/simulation, with the server's state in Postgres instead of memory.
 * JDBC runs inline on the simulator's scheduler (EmptyCoroutineContext) so runs stay deterministic.
 */
class PostgresSimulationTest {
    private val seeds = System.getProperty("milkrun.pgsim.seeds")?.toLong() ?: 25L

    @Test
    fun chaosShiftsConvergeOnPostgres() {
        val store = PostgresSyncStore(Postgres.dataSource, EmptyCoroutineContext)
        val tag = "pg${System.currentTimeMillis()}-"
        val reports = (1L..seeds).map { Simulation(SimConfig(seed = it, routeTag = tag)) { store }.run() }
        val failures = reports.filterNot { it.ok }
        assertTrue(failures.isEmpty(), failures.take(3).joinToString("\n") { "seed ${it.seed}: ${it.violations}" })
        assertTrue(reports.sumOf { it.stats.droppedResponses } > 0)
    }
}
