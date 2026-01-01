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

rootProject.name = "Chronos"

include(":chronos-agent")
include(":chronos-agent-noop")
include(":chronos-protocol")
// include(":chronos-studio-plugin")
