plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.chronos.agent" // Same namespace to allow swapping
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
    
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.chronos"
                artifactId = "chronos-agent-noop"
                version = "0.1.0"
            }
        }
    }
}
