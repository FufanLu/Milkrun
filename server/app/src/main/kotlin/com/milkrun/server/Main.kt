package com.milkrun.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Configuration comes from the environment (12-factor):
 *
 * | variable | default |
 * |---|---|
 * | `PORT` | 8080 |
 * | `DATABASE_URL` | `jdbc:postgresql://localhost:5432/milkrun` |
 * | `DATABASE_USER` / `DATABASE_PASSWORD` | `milkrun` / `milkrun` |
 * | `DATABASE_POOL_SIZE` | 16 |
 * | `JWT_SECRET` | required, at least 32 characters |
 * | `MILKRUN_DEV_MODE` | `false`; `true` enables `POST /v1/dev/session` (demo route + tokens) |
 */
public fun main() {
    val log = LoggerFactory.getLogger("milkrun")
    val env = System.getenv()
    val dataSource = dataSource(
        url = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/milkrun",
        user = env["DATABASE_USER"] ?: "milkrun",
        password = env["DATABASE_PASSWORD"] ?: "milkrun",
        poolSize = env["DATABASE_POOL_SIZE"]?.toInt() ?: 16,
    )
    migrate(dataSource)
    val deps = ServerDeps(
        store = PostgresSyncStore(dataSource),
        jwt = JwtConfig(env["JWT_SECRET"] ?: error("JWT_SECRET is required")),
        readiness = { runCatching { dataSource.connection.use { it.isValid(1) } }.getOrDefault(false) },
        devMode = env["MILKRUN_DEV_MODE"] == "true",
    )
    val port = env["PORT"]?.toInt() ?: 8080
    log.info("milkrun server starting on :{}", port)
    embeddedServer(Netty, port = port) { milkrun(deps) }.start(wait = true)
}

public fun dataSource(url: String, user: String, password: String, poolSize: Int = 16): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = poolSize
            connectionTimeout = 3_000
            // The sync protocol requires an acknowledged mutation to be durable (ADR 0002).
            addDataSourceProperty("options", "-c synchronous_commit=on")
            poolName = "milkrun"
        },
    )

public fun migrate(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
}
