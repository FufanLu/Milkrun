plugins {
    id("milkrun.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.sync)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        androidMain.dependencies { api(libs.ktor.client.okhttp) }
        iosMain.dependencies { api(libs.ktor.client.darwin) }
        jvmMain.dependencies { api(libs.ktor.client.cio) }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
