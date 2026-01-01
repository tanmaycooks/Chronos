plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij")
}

group = "com.chronos"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":chronos-protocol"))
}

// Configure IntelliJ Platform Plugin
intellij {
    version.set("2023.3") // Target Android Studio Hedgehog base
    type.set("IC") // IntelliJ Community Edition (Android Studio base)
    plugins.set(listOf("android"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("241.*")
    }
}
