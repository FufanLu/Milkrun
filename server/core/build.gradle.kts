plugins {
    id("milkrun.jvm.service")
    `java-test-fixtures`
}

dependencies {
    api(projects.shared.protocol)
    api(libs.kotlinx.coroutines.core)

    testFixturesApi(kotlin("test"))
    testFixturesApi(libs.kotlinx.coroutines.test)
    testFixturesApi(libs.junit.jupiter)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
