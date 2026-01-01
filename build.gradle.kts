// Root build.gradle.kts
plugins {
    // Common plugins for subprojects
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.android.library") version "8.2.2" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("org.jetbrains.intellij") version "1.17.2" apply false
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
}
