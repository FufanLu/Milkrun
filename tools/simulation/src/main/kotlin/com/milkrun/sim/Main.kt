package com.milkrun.sim

import kotlin.system.exitProcess
import kotlin.time.measureTimedValue

/**
 * ```
 * ./gradlew :tools:simulation:run --args="--seeds 10000"
 * ./gradlew :tools:simulation:run --args="--seed 4242"      # reproduce one run
 * ./gradlew :tools:simulation:run --args="--seeds 200 --fault SERVER_ROLLBACK"  # the checks must fail
 * ```
 */
public fun main(args: Array<String>) {
    val opts = args.toList().chunked(2).associate { it[0].removePrefix("--") to it.getOrElse(1) { "" } }
    val seeds: List<Long> =
        opts["seed"]?.let { listOf(it.toLong()) }
            ?: (1L..(opts["seeds"]?.toLong() ?: 1_000L)).toList()
    val clients = opts["clients"]?.toInt() ?: 2
    val faults = opts["fault"]?.split(',')?.filter { it.isNotBlank() }?.map { Fault.valueOf(it) }?.toSet().orEmpty()

    val total = SimStats()
    val failures = mutableListOf<SimReport>()
    val (_, elapsed) =
        measureTimedValue {
            for (seed in seeds) {
                val report = Simulation(SimConfig(seed = seed, clients = clients, faults = faults)).run()
                total += report.stats
                if (!report.ok) failures += report
            }
        }

    println("Milkrun sync simulation: ${seeds.size} seeds × $clients devices, faults=$faults, wall time $elapsed")
    println(
        """
        |  mutations recorded        ${total.recorded}
        |  refused locally           ${total.refusedLocally}
        |  pushes / pulls            ${total.pushes} / ${total.pulls}
        |  requests lost             ${total.droppedRequests}
        |  responses lost            ${total.droppedResponses}   (server applied, phone never heard)
        |  dead-zone failures        ${total.offlineFailures}
        |  app restarts              ${total.restarts}
        |  stale duplicate pushes    ${total.staleDuplicates}
        |  duplicates deduplicated   ${total.serverDuplicates}
        |  rulings accepted/rejected ${total.accepted} / ${total.rejected}
        |  delivered-after-cancel    ${total.deliveredAfterCancel}
        |  heal rounds (avg)         ${"%.2f".format(total.healRounds.toDouble() / seeds.size)}
        |  runs with violations      ${failures.size}
        """.trimMargin(),
    )
    println("  rejections by mutation/reason/device:")
    total.rejectedBy.entries.sortedByDescending { it.value }.forEach { (k, n) -> println("    %-44s %6d".format(k, n)) }
    failures.take(5).forEach { r -> println("  seed ${r.seed}: ${r.violations.take(3)}") }
    if (failures.isNotEmpty()) exitProcess(1)
}
