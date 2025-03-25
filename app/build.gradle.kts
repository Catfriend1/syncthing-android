plugins {
    id("com.android.application")
    id("com.github.ben-manes.versions")
    id("com.github.triplet.play") version "3.7.0"
}

dependencies {
    androidTestImplementation("androidx.annotation:annotation:1.2.0")
    androidTestImplementation("androidx.test:rules:1.6.1")
    annotationProcessor("com.google.dagger:dagger-compiler:2.56")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.fragment:fragment:1.8.6")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.annimon:stream:1.2.2")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.google.dagger:dagger:2.56")
    implementation("com.google.guava:guava:33.4.0-android")
    // Do not upgrade zxing:core beyond 3.3.0 to ensure Android 6.0 compatibility, see issue #761.
    implementation("com.google.zxing:core:3.3.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    implementation("eu.chainfire:libsuperuser:1.1.1")
    implementation("org.mindrot:jbcrypt:0.4")

    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.20") {
            because("kotlin-stdlib-jdk7 is now a part of kotlin-stdlib")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.20") {
            because("kotlin-stdlib-jdk8 is now a part of kotlin-stdlib")
        }
    }
}

android {
    val ndkVersionShared = rootProject.extra.get("ndkVersionShared")
    val versionMajor: kotlin.Int by rootProject.extra
    val versionMinor: kotlin.Int by rootProject.extra
    val versionPatch: kotlin.Int by rootProject.extra
    val versionWrapper: kotlin.Int by rootProject.extra

    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "${ndkVersionShared}"

    namespace = "com.nutomic.syncthingandroid"
    buildFeatures.dataBinding = true

    defaultConfig {
        applicationId = "com.github.catfriend1.syncthingandroid"
        minSdk = 21
        targetSdk = 35
        versionCode = versionMajor * 1000000 + versionMinor * 10000 + versionPatch * 100 + versionWrapper
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}.${versionWrapper}"
        testApplicationId = "com.github.catfriend1.syncthingandroid.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            signingConfig = signingConfigs.runCatching { getByName("release") }
                .getOrNull()
                .takeIf { it?.storeFile != null }
        }
        create("gplay") {
            initWith(getByName("release"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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


    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    lint {
        abortOnError = true
        disable += "ExpiringTargetSdkVersion"
        disable += "ExpiredTargetSdkVersion"
    }
}

play {
    // Use ANDROID_PUBLISHER_CREDENTIALS environment variable to specify serviceAccountCredentials.
    track = "beta"
    resolutionStrategy = com.github.triplet.gradle.androidpublisher.ResolutionStrategy.IGNORE
    defaultToAppBundles = true
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

task<Exec>("postBuildScript") {
    commandLine("python", "-u" , "./postbuild.py")
}

project.afterEvaluate {
    project.getTasks().getByName("mergeDebugJniLibFolders").dependsOn(":syncthing:buildNative")
}
