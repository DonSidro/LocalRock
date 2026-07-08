import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose UI
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(compose.materialIconsExtended)

            // Former :shared module dependencies
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.ktor.client.logging)
            implementation(libs.multiplatformSettings)
            implementation(libs.multiplatformSettings.coroutines)
            implementation(libs.kotlincrypto.hash.md)
            implementation(libs.kotlincrypto.macs.hmacSha2)
            implementation(libs.kmqtt.client)
            implementation(libs.kmqtt.common)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            // Pre-built libwebrtc for the camera live view (shared/webrtc actuals)
            implementation(libs.stream.webrtc.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.kodraliu.localrock.resources"
}

android {
    namespace = "com.kodraliu.localrock"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kodraliu.localrock"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val generateTestFixtures = tasks.register("generateTestFixtures") {
    val fixtureFile = layout.projectDirectory.file(
        "../tests/contracts/fixtures/ios_app_init_v4_59_02_anonymized.json"
    )
    val mapFile = layout.projectDirectory.file(
        "../tests/fixtures/b01_map_sample.bin.gz"
    )
    val legacyMapFile = layout.projectDirectory.file(
        "../tests/fixtures/real_map_sample.bin"
    )
    val outDir = layout.buildDirectory.dir("generated/testFixtures/kotlin")
    inputs.file(fixtureFile)
    inputs.file(mapFile)
    inputs.file(legacyMapFile)
    outputs.dir(outDir)
    doLast {
        val text = fixtureFile.asFile.readText(Charsets.UTF_8)
        val mapB64 = Base64.getEncoder().encodeToString(mapFile.asFile.readBytes())
        val legacyMapB64 = Base64.getEncoder().encodeToString(legacyMapFile.asFile.readBytes())
        val pkgDir = outDir.get().asFile.resolve(
            "com/kodraliu/localrock/shared/testing"
        )
        pkgDir.mkdirs()
        pkgDir.resolve("Fixtures.kt").writeText(buildString {
            appendLine("package com.kodraliu.localrock.shared.testing")
            appendLine()
            appendLine("internal object Fixtures {")
            append("    const val IOS_APP_INIT_V4_59_02: String = \"\"\"")
            append(text)
            appendLine("\"\"\"")
            appendLine()
            appendLine("    // gzip(inflated B01 SCMap), base64-encoded; copied from python-roborock's tests/map/testdata.")
            appendLine("    const val B01_MAP_SAMPLE_GZ_B64: String = \"$mapB64\"")
            appendLine()
            appendLine("    // Raw legacy 'rr'-magic TLV map blob captured from a real Roborock device, base64-encoded.")
            appendLine("    // Split into ~30KB chunks because the JVM caps string constants at 65535 UTF-8 bytes.")
            val chunkSize = 30_000
            val parts = legacyMapB64.chunked(chunkSize)
            appendLine("    val LEGACY_MAP_SAMPLE_B64: String = buildString(${legacyMapB64.length}) {")
            for (part in parts) appendLine("        append(\"$part\")")
            appendLine("    }")
            appendLine("}")
        })
    }
}

kotlin.sourceSets.commonTest.configure {
    kotlin.srcDir(generateTestFixtures)
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
