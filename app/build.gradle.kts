import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "dev.titan.code"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.titan.code"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            // The Titan 2 Elite is arm64; one ABI keeps the APK small (PLAN §13 "APK size").
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // android.util.Log etc. return defaults in JVM tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    implementation(platform(libs.sora.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)

    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // The real org.json for JVM tests (android.jar's copy is stubbed).
    testImplementation(libs.json)
}

// M4 local runner (PLAN §7). Python matches LeetCode's judge; re-check when LeetCode changes it.
// No pip: sortedcontainers is vendored in src/main/python (pure Python), so no build Python is needed.
chaquopy {
    defaultConfig {
        version = "3.14"
        pyc {
            src = false
        }
        // Jedi reads its own stub files and parso its grammars from disk (rev 38).
        extractPackages("jedi", "parso", "sortedcontainers")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
