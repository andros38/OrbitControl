import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "id.orbitcontrol"
version = "0.5.0-windows"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

compose.desktop {
    application {
        mainClass = "id.orbitcontrol.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Orbit Control"
            packageVersion = "0.5.0"
            description = "Huawei/Orbit B312 local modem monitoring and management utility"
            vendor = "Orbit Control"
            copyright = "Copyright © 2026"
            windows {
                dirChooser = true
                perUserInstall = true
                menuGroup = "Orbit Control"
                upgradeUuid = "5e7f5b27-c1b7-49b3-8b73-59c49c63b1d9"
            }
        }
    }
}
