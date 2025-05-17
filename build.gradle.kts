// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

buildscript {
    extra.apply {
        // Cannot be called "ndkVersion" as that leads to naming collision
        // Changes to this value must be reflected in `./docker/Dockerfile`
        set("ndkVersionShared", "28.0.13004108")
        set("versionMajor", 1)
        set("versionMinor", 29)
        set("versionPatch", 6)
        set("versionWrapper", 4)
    }

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.8.2")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
