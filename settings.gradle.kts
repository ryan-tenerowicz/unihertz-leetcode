pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "titan-code"

// ":app" is Titan Code. ":probe" is the Phase 0 Key & Gesture Probe, kept as a diagnostic tool.
include(":app")
include(":probe")
