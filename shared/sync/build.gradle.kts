plugins {
    id("milkrun.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.protocol)
            api(projects.shared.database)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        val androidHostTest by getting {
            dependencies { implementation(libs.sqldelight.driver.sqlite) }
        }
    }
}
