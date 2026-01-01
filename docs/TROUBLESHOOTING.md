# Chronos Troubleshooting Guide

Quick solutions for common Chronos issues.

---

## Build Issues

### ❌ "Chronos must NEVER be included in a release build"

**Cause**: Chronos agent detected in a non-debug build.

**Solution**:
```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("com.chronos:chronos-agent:0.1.0")
    releaseImplementation("com.chronos:chronos-agent-noop:0.1.0")  // Add this!
}
```

---

### ❌ Build fails with "Unresolved reference: Chronos"

**Cause**: Dependency not found.

**Solution**:
1. Check repository configuration:
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

2. Sync Gradle and rebuild.

---

## Runtime Issues

### ❌ "IPC Auth Failed. Closing connection."

**Cause**: Studio plugin using wrong/expired auth token.

**Solution**:
1. Restart the app to generate a new token
2. In Studio, click "Reconnect" or restart the Chronos panel
3. The token refreshes on each app launch

---

### ❌ Recording pauses unexpectedly

**Cause**: Memory pressure triggered automatic pause.

**Solution**:
```kotlin
// Check current memory state
val stats = MemoryPressureMonitor.getStats()
Log.d("Chronos", "Pause count: ${stats.pauseCount}")

// Check if currently paused
if (MemoryPressureMonitor.isPaused()) {
    // Free up memory or wait for GC
}
```

---

### ❌ "Rate limit exceeded, closing connection"

**Cause**: IPC messages exceeding 1000/minute.

**Solution**:
- Reduce state capture frequency
- Check for loops in state source registration
- Consider using sampling:
```kotlin
// Only record every 100ms instead of every change
Chronos.registerSource(
    sourceId = "HighFrequency.state",
    source = { state },
    samplingIntervalMs = 100
)
```

---

## Replay Issues

### ❌ "CHRONOS REPLAY REFUSED"

**Cause**: Non-deterministic sources detected.

**Solution**:
1. Check the refusal report:
```kotlin
val report = Chronos.getRefusalReport()
report.blockingReasons.forEach { reason ->
    Log.w("Chronos", "Blocked by: ${reason.sourceId} - ${reason.reason}")
}
```

2. Address blocking sources:
   - **Class D sources**: Mock or exclude from replay
   - **Class C sources**: Verify and add `@ChronosConditionalSafe`
   - **Unregistered sources**: Register with proper classification

---

### ❌ Replay diverges from recording

**Cause**: State changed differently during replay.

**Solution**:
1. Check the divergence report:
```kotlin
val divergence = Chronos.getDivergenceReport()
divergence.differences.forEach { diff ->
    Log.w("Chronos", "Divergence at ${diff.sequenceNumber}: ${diff.description}")
}
```

2. Common causes:
   - **Random values**: Use `@ChronosUnsafe` or mock
   - **Timestamps**: Use mocked time during replay
   - **Lazy initialization**: Ensure order matches

---

## Performance Issues

### ❌ App becomes slow with Chronos enabled

**Solution**:
1. Check metrics:
```kotlin
val summary = RecordingMetrics.getSummary()
Log.d("Chronos", "Events/sec: ${summary.eventsPerSecond}")
Log.d("Chronos", "Avg capture: ${summary.avgCaptureTimeUs}μs")
```

2. Reduce recording frequency:
```kotlin
// Set recording level
Chronos.setRecordingLevel(RecordingLevel.REDUCED)  // Skip non-essential sources
```

3. Exclude high-frequency sources:
```kotlin
@ChronosIgnore
val highFrequencyCounter: Int  // Not recorded
```

---

### ❌ Large memory usage

**Solution**:
1. Reduce ring buffer size:
```kotlin
Chronos.configure {
    maxRingBufferSize = 1000  // Default is 10000
}
```

2. Enable aggressive memory handling:
```kotlin
MemoryPressureMonitor.configure {
    pauseThreshold = 0.20f  // Pause earlier (at 20% memory)
}
```

---

## Studio Plugin Issues

### ❌ "Device not found" in Studio

**Solution**:
1. Verify ADB connection:
```bash
adb devices
```

2. Restart ADB:
```bash
adb kill-server
adb start-server
```

3. Reinstall the debug app

---

### ❌ Timeline shows gaps

**Cause**: Recording was paused (memory pressure, rate limiting, or manual pause).

**Solution**:
- Check `RecordingMetrics.getSummary().gapCount`
- Gap markers include the reason for the gap
- Reduce system load or increase recording thresholds

---

### ❌ State tree not loading

**Cause**: Serialization failed for some objects.

**Solution**:
1. Check for serialization errors:
```kotlin
val errors = RecordingMetrics.getSummary().serializationErrors
```

2. Ensure state classes are serializable:
```kotlin
// ✅ Good - data class with primitives
data class GoodState(val count: Int, val name: String)

// ❌ Bad - contains non-serializable types
data class BadState(val socket: Socket)  // Add @ChronosIgnore
```

---

## Multi-Process Issues

### ❌ "Invalid auth token for PID X"

**Cause**: Process hasn't registered or token expired.

**Solution**:
```kotlin
// Ensure each process registers
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Chronos.initialize(this)
        MultiProcessManager.registerProcess(getProcessName())
    }
}
```

---

### ❌ Cross-process events not syncing

**Solution**:
1. Verify all processes are registered
2. Check IPC server is running:
```kotlin
// Main process
SecureIPCServer("chronos_ipc").start()
```

3. Verify authentication:
```kotlin
val token = MultiProcessManager.getAuthTokenForProcess(pid)
// token should not be null
```

---

## Getting Help

### Collect Debug Information

```kotlin
fun collectChronosDebugInfo(): String {
    val metrics = RecordingMetrics.getSummary()
    val memory = MemoryPressureMonitor.getStats()
    
    return """
        Chronos Debug Info:
        - Session duration: ${metrics.sessionDurationMs}ms
        - Snapshots: ${metrics.snapshotCount}
        - Errors: ${metrics.totalErrors}
        - Memory pauses: ${memory.pauseCount}
        - Currently paused: ${memory.currentlyPaused}
    """.trimIndent()
}
```

### Enable Verbose Logging

```bash
adb shell setprop log.tag.Chronos VERBOSE
```

### File an Issue

Include:
1. Debug info from above
2. Android version
3. Device model
4. Chronos version
5. Steps to reproduce
