plugins {
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.aboutlibraries.core)
    implementation(libs.activity.compose)
    implementation(libs.activity.ktx)
    implementation(libs.android.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.constraintlayout)
    implementation(libs.core.ktx)
    implementation(libs.dagger)
    implementation(libs.documentfile)
    implementation(libs.fragment.ktx)
    implementation(libs.gson)
    implementation(libs.guava)
    implementation(libs.jbcrypt)
    implementation(libs.libsuperuser)
    implementation(libs.lingala.zip4j)
    implementation(libs.localbroadcastmanager)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.stream)
    implementation(libs.volley)
    implementation(libs.zxing.android.embedded) { isTransitive = false }
    implementation(libs.zxing.core)
    ksp(libs.dagger.compiler)
}

android {
    compileSdk = libs.versions.compile.sdk.get().toInt()
    namespace = "com.nutomic.syncthingandroid"
    ndkVersion = libs.versions.ndk.version.get()

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/libSyncthingNative.mk")
        }
    }

    buildFeatures {
        compose = true
    }

    defaultConfig {
        applicationId = "com.github.catfriend1.syncthingandroid"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = libs.versions.version.code.get().toInt()
        versionName = libs.versions.version.name.get()
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("SYNCTHING_RELEASE_STORE_FILE")?.let(::file)
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = System.getenv("SYNCTHING_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.runCatching { getByName("release") }
                .getOrNull()
                .takeIf { it?.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    splits {
        abi {
            // Only enable splits for release builds
            isEnable = project.gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            // Otherwise libsyncthing.so doesn't appear where it should in installs
            // based on app bundles, and thus nothing works.
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = true
        targetSdk = libs.versions.target.sdk.get().toInt()
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

}

/**
 * Some languages are not supported by Google Play, so we ignore them.
 */
tasks.register<Delete>("deleteUnsupportedPlayTranslations") {
    delete(
            "src/main/play/listings/el-EL/",
            "src/main/play/listings/en/",
            "src/main/play/listings/eu/",
            "src/main/play/listings/nb/",
            "src/main/play/listings/nl_BE/",
            "src/main/play/listings/nl-BE/",
            "src/main/play/listings/nn/",
            "src/main/play/listings/ta/",
    )
}

tasks.register("validateAppVersionCode") {
    doFirst {
        val versionName = libs.versions.version.name.get()
        val versionCode = libs.versions.version.code.get().toInt()

        val parts = versionName.split(".")
        if (parts.size != 4) {
            throw GradleException("Invalid versionName format: '$versionName'. Expected format 'major.minor.patch.wrapper'.")
        }

        val calculatedCode = parts[0].toInt() * 1_000_000 +
                             parts[1].toInt() * 10_000 +
                             parts[2].toInt() * 100 +
                             parts[3].toInt()

        if (calculatedCode != versionCode) {
            throw GradleException("Version mismatch: Calculated versionCode ($calculatedCode) does not match declared versionCode ($versionCode). Please review 'gradle/libs.versions.toml'.")
        }
    }
}

project.afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
        dependsOn("validateAppVersionCode")
    }

    val isCopilot = System.getenv("IS_COPILOT")?.toBoolean() ?: false
    if (!isCopilot) {
        android.buildTypes.forEach {
            val capitalizedName = it.name.replaceFirstChar { ch -> ch.uppercase() }
            tasks.named("merge${capitalizedName}JniLibFolders") {
                dependsOn(":syncthing:buildNative")
            }
        }
    }
}
