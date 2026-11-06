package com.milkrun.server

import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * One PostgreSQL per test JVM, migrated once. Tests isolate themselves by route id.
 *
 * Testcontainers (`postgres:17-alpine`) by default, as in CI. Set `MILKRUN_TEST_DATABASE_URL`
 * (plus `MILKRUN_TEST_DATABASE_USER` / `_PASSWORD`) to run against an existing server instead, for
 * machines without a working Docker daemon.
 */
object Postgres {
    val dataSource: HikariDataSource by lazy {
        val env = System.getenv()
        val external = env["MILKRUN_TEST_DATABASE_URL"]
        val ds = if (external != null) {
            dataSource(
                external,
                env["MILKRUN_TEST_DATABASE_USER"] ?: "milkrun",
                env["MILKRUN_TEST_DATABASE_PASSWORD"] ?: "",
                poolSize = 16,
            )
        } else {
            val c = PostgreSQLContainer("postgres:17-alpine")
                .withCommand("postgres", "-c", "fsync=on", "-c", "synchronous_commit=on")
                .apply { start() }
            dataSource(c.jdbcUrl, c.username, c.password, poolSize = 16)
        }
        migrate(ds)
        ds
    }
}
