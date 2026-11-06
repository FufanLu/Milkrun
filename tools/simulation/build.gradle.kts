plugins {
    id("milkrun.jvm.service")
    application
}

dependencies {
    implementation(projects.shared.sync)
    implementation(projects.server.core)
    // The simulator drives everything on a virtual clock; the test scheduler is that clock.
    implementation(libs.kotlinx.coroutines.test)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("com.milkrun.sim.MainKt")
}

tasks.test {
    systemProperty("milkrun.sim.seeds", providers.gradleProperty("simSeeds").getOrElse("300"))
}

tasks.register<JavaExec>("trace") {
    description = "Writes the data quoted in docs/illustrated/01-offline-delivery.md to build/trace/."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.milkrun.sim.TraceKt")
    args(
        layout.buildDirectory
            .dir("trace")
            .get()
            .asFile.path,
    )
}
