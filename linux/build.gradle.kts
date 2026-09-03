import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "id.orbitcontrol"
version = "0.5.0-linux"

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
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "orbit-control"
            packageVersion = "0.5.0"
            description = "Huawei/Orbit B312 local modem monitoring and management utility"
            vendor = "Orbit Control"
            copyright = "Copyright © 2026"
            modules(
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.xml",
                "jdk.crypto.ec",
            )
            linux {
                packageName = "orbit-control"
                debMaintainer = "Orbit Control"
                menuGroup = "Network"
                appCategory = "Network"
                rpmLicenseType = "Proprietary"
            }
        }
    }
}
