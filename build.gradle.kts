// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    extra.apply {
        // Cannot be called "ndkVersion" as that leads to naming collision
        // Changes to this value must be reflected in `./docker/Dockerfile`
        set("ndkVersionShared", "27.0.12077973")
        set("versionMajor", 1)
        set("versionMinor", 28)
        set("versionPatch", 0)
        set("versionWrapper", 0)
    }

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.6.1")
        classpath("com.github.ben-manes:gradle-versions-plugin:0.42.0")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
