plugins {
    kotlin("multiplatform") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("app.cash.zipline") version "1.28.0"
    id("org.spotube.gradle-plugin") version "0.1.2"
}

kotlin {
    js {
        browser()
        binaries.executable()
        // QuickJS in Spotube 6.0.0 rejects some ES generator control-flow patterns.
        compilerOptions { target.set("es5") }
    }
    jvm()
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            implementation("org.spotube.spotube:plugin_interfaces:0.1.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("net.swiftzer.semver:semver:2.1.0")
            api("app.cash.zipline:zipline:1.28.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        jvmTest.dependencies {
            implementation("app.cash.zipline:zipline-loader:1.28.0")
        }
    }
}
zipline { mainFunction.set("org.community.listenbrainz.main") }

tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest") {
    dependsOn("packageProductionPlugin")
    inputs.property("liveApiTests", providers.environmentVariable("BRAINZ_LIVE_TESTS").orElse("0"))
}
