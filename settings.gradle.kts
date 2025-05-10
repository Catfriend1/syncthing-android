pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") }

        versionCatalogs {
            create("baseLibs") {
                from("com.mikepenz:version-catalog:0.3.4")
            }
        }
    }
}

include(
    ":app",
    ":syncthing"
)

include(":aboutlibraries-core")
include(":aboutlibraries")
include(":aboutlibraries-compose")
include(":aboutlibraries-compose-m2")
include(":aboutlibraries-compose-m3")
