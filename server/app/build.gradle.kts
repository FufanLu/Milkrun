plugins {
    id("milkrun.jvm.service")
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("com.milkrun.server.MainKt")
}

ktor {
    fatJar { archiveFileName.set("milkrun-server.jar") }
}

dependencies {
    implementation(projects.server.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.prometheus)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    implementation(libs.logback)

    testImplementation(testFixtures(projects.server.core))
    testImplementation(projects.tools.simulation)
    testImplementation(projects.shared.network)
    testImplementation(projects.shared.sync)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.postgres)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    // Forwarded so `MILKRUN_TEST_DATABASE_URL=... ./gradlew :server:app:test` reaches the test JVM.
    listOf("MILKRUN_TEST_DATABASE_URL", "MILKRUN_TEST_DATABASE_USER", "MILKRUN_TEST_DATABASE_PASSWORD").forEach { k ->
        providers.environmentVariable(k).orNull?.let { environment(k, it) }
    }
    systemProperty("milkrun.pgsim.seeds", providers.gradleProperty("pgSimSeeds").getOrElse("25"))
    inputs.property("pgUrl", providers.environmentVariable("MILKRUN_TEST_DATABASE_URL").orElse("testcontainers"))
}

tasks.register<JavaExec>("trace") {
    description = "Writes the data quoted in docs/illustrated/02-server-side.md to build/trace/."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.milkrun.server.ServerTrace")
    args(
        providers.gradleProperty("pgAdminUrl").getOrElse("jdbc:postgresql://localhost:55432/postgres"),
        providers.gradleProperty("pgLog").getOrElse(""),
        layout.buildDirectory.dir("trace").get().asFile.path,
    )
}
