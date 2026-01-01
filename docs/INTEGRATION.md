# Chronos Integration Guide

This guide covers everything you need to integrate Chronos into your Android application.

## Table of Contents

- [Installation](#installation)
- [ProGuard/R8 Configuration](#prguardr8-configuration)
- [Annotations Reference](#annotations-reference)
- [Multi-Process Setup](#multi-process-setup)
- [Memory Configuration](#memory-configuration)
- [Security Considerations](#security-considerations)

---

## Installation

### Gradle Setup

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// app/build.gradle.kts
android {
    buildTypes {
        debug {
            // Enable Chronos in debug builds
            buildConfigField("boolean", "CHRONOS_ENABLED", "true")
        }
        release {
            // Ensure Chronos is disabled in release
            buildConfigField("boolean", "CHRONOS_ENABLED", "false")
        }
    }
}

dependencies {
    // Real Chronos agent for debug builds ONLY
    debugImplementation("com.chronos:chronos-agent:0.1.0")
    
    // No-op stub for release builds (safe, does nothing)
    releaseImplementation("com.chronos:chronos-agent-noop:0.1.0")
}
```

### Application Initialization

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Chronos (safe - throws if in release build)
        Chronos.initialize(this)
        
        // Optional: Initialize memory pressure monitoring
        MemoryPressureMonitor.initialize(this)
        
        // Optional: Start metrics tracking
        RecordingMetrics.startSession()
    }
}
```

---

## ProGuard/R8 Configuration

### Consumer ProGuard Rules (Recommended)

Add to your `app/proguard-rules.pro`:

```proguard
# ============================================================
# CHRONOS: Complete removal from release builds
# ============================================================

# Remove all Chronos method calls (no-op)
-assumenosideeffects class com.chronos.agent.** {
    *;
}

# Specifically remove initialization and registration
-assumenosideeffects class com.chronos.agent.Chronos {
    public static void initialize(...);
    public static void registerSource(...);
    public static void unregisterSource(...);
    public static *** get*(...);
    public *** *(...);
}

# Remove monitoring
-assumenosideeffects class com.chronos.agent.monitoring.** {
    *;
}

# Suppress warnings
-dontwarn com.chronos.agent.**

# Keep no-op stubs for API compatibility
-keep class com.chronos.agent.Chronos {
    public static *;
}
```

### Verification

To verify Chronos is stripped from release builds:

```bash
# Build release APK
./gradlew assembleRelease

# Check for Chronos classes
unzip -l app/build/outputs/apk/release/app-release.apk | grep chronos
# Should return nothing or only no-op classes
```

---

## Annotations Reference

### @ChronosDeterministic

Marks a class as Class A (Guaranteed Deterministic).

```kotlin
@ChronosDeterministic
data class UserState(
    val id: String,
    val name: String,
    val isLoggedIn: Boolean
)
```

**Requirements:**
- Must be a `data class` or `sealed class`
- All properties must be immutable (`val`)
- No side-effect-producing methods

---

### @ChronosVerifiable

Marks a class as Class B (Runtime Verifiable).

```kotlin
@ChronosVerifiable
class ViewModelState(
    private val _state: MutableStateFlow<UiState>
) : StateSource<UiState> {
    
    override val sourceId = "MainViewModel.state"
    
    override fun captureState() = _state.value
    
    override fun getDeterminismClass() = DeterminismClass.CLASS_B_VERIFIABLE
}
```

**Requirements:**
- Must implement `StateSource<T>`
- State must be verifiable at runtime (e.g., StateFlow, LiveData)

---

### @ChronosConditionalSafe

Marks a class as Class C (Conditionally Safe). **Requires explicit developer attestation.**

```kotlin
@ChronosConditionalSafe(
    reason = "This legacy module has no external dependencies and is isolated",
    author = "developer@company.com",
    reviewedDate = "2026-02-05"
)
class LegacyStateAdapter : StateSource<LegacyState> {
    
    override val sourceId = "Legacy.adapter"
    
    override fun captureState(): LegacyState {
        // Convert legacy state to Chronos-compatible format
        return LegacyState(...)
    }
    
    override fun getDeterminismClass() = DeterminismClass.CLASS_C_CONDITIONAL
}
```

**Requirements:**
- Must provide `reason` explaining why this is safe
- Chronos will log warnings when replaying with Class C sources
- Consider migrating to Class A/B where possible

---

### @ChronosUnsafe

Explicitly marks a source as Class D (Unsafe). **Replay is ALWAYS blocked.**

```kotlin
@ChronosUnsafe(
    reason = "Makes network calls"
)
class NetworkRepository : StateSource<NetworkState> {
    
    override val sourceId = "Network.repository"
    
    override fun captureState() = NetworkState(
        lastResponse = cachedResponse,
        timestamp = System.currentTimeMillis() // Non-deterministic!
    )
    
    override fun getDeterminismClass() = DeterminismClass.CLASS_D_UNSAFE
}
```

**Use for:**
- Network operations
- Database access
- File I/O
- System time/random values
- Hardware sensors

---

### @ChronosRedact

Prevents sensitive data from being recorded.

```kotlin
data class UserCredentials(
    val username: String,
    
    @ChronosRedact
    val password: String,
    
    @ChronosRedact
    val authToken: String
)

// In recordings: password = "[REDACTED]", authToken = "[REDACTED]"
```

---

### @ChronosIgnore

Excludes a property from Chronos recording entirely.

```kotlin
data class DebugState(
    val visibleItems: List<Item>,
    
    @ChronosIgnore
    val debugCache: MutableMap<String, Any>  // Not recorded
)
```

---

## Multi-Process Setup

For apps with multiple processes:

```kotlin
// In each process's Application class
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Chronos.initialize(this)
        
        // Register this process with the coordinator
        val authToken = MultiProcessManager.registerProcess(
            processName = getProcessName()
        )
        
        // authToken is used for cross-process authentication
    }
    
    private fun getProcessName(): String {
        return if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            // Fallback for older APIs
            "main"
        }
    }
}
```

---

## Memory Configuration

### Automatic Memory Pressure Handling

```kotlin
// In Application.onCreate()
MemoryPressureMonitor.initialize(this)

// Add a listener for custom handling
MemoryPressureMonitor.addListener(object : MemoryPressureMonitor.MemoryPressureListener {
    override fun onRecordingPaused(reason: String) {
        Log.w("MyApp", "Chronos recording paused: $reason")
    }
    
    override fun onRecordingResumed() {
        Log.i("MyApp", "Chronos recording resumed")
    }
})
```

### Custom Thresholds

The default thresholds are:
- **Pause**: When <15% memory available
- **Resume**: When >25% memory available

---

## Security Considerations

### 1. Never Log Auth Tokens

```kotlin
// ❌ WRONG
Log.d("Debug", "Token: ${Chronos.getIPCAuthToken()}")

// ✅ CORRECT
// Auth token is retrieved programmatically, never logged
val token = Chronos.getIPCAuthToken()
```

### 2. Use No-Op in Release

Always use the no-op artifact in release builds:

```kotlin
releaseImplementation("com.chronos:chronos-agent-noop:0.1.0")
```

### 3. Verify Build Configuration

```kotlin
// Chronos automatically checks this, but you can verify:
if (!BuildConfig.DEBUG) {
    throw SecurityException("Chronos should not be in release builds!")
}
```

### 4. IPC Security

Chronos uses:
- **Authentication**: Random UUID tokens per session
- **Encryption**: AES-256-GCM for all IPC communication
- **Rate limiting**: Max 1000 messages/minute to prevent DoS

---

## Next Steps

- [Troubleshooting Guide](./TROUBLESHOOTING.md)
- [API Reference](./docs/API.md)
- [Architecture Overview](./docs/ARCHITECTURE.md)
