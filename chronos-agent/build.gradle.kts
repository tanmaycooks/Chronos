plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.chronos.agent"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        
        // Ensure the build config field DEBUG is available
        buildFeatures {
            buildConfig = true
        }
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    
    publishing {
        singleVariant("debug") {
            withSourcesJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("debug") {
                from(components["debug"])
                groupId = "com.chronos"
                artifactId = "chronos-agent"
                version = "0.1.0"
            }
        }
    }
}

dependencies {
    implementation(project(":chronos-protocol"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.11.1")
    
    // Android test dependencies
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
