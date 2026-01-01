# Chronos Testing Guide

This guide explains how to test the Chronos Android Debugger.

---

## Quick Start

```powershell
# Navigate to project root
cd c:\Users\anand\Chronos

# Run all tests
.\gradlew test

# Run tests with detailed output
.\gradlew test --info
```

---

## Test Structure

```
chronos-agent/src/test/java/com/chronos/agent/
├── DeterminismClassifierTest.kt   # Tests for Class A/B/C/D classification
├── InspectionSafetyTest.kt        # Tests for side-effect detection
├── ReplayTest.kt                  # Tests for replay sandbox and refusal engine
└── RingBufferTest.kt              # Tests for circular buffer storage
```

---

## Running Specific Tests

```powershell
# Run all tests in chronos-agent module
.\gradlew :chronos-agent:test

# Run a specific test class
.\gradlew :chronos-agent:test --tests "com.chronos.agent.RingBufferTest"

# Run a specific test method
.\gradlew :chronos-agent:test --tests "com.chronos.agent.RingBufferTest.append adds events to buffer"

# Run tests with pattern matching
.\gradlew :chronos-agent:test --tests "*Determinism*"
```

---

## Test Categories

### Unit Tests

Test individual components in isolation:

```powershell
.\gradlew :chronos-agent:test
```

**Covered:**
- `RingBuffer` - Event storage and overflow
- `DeterminismScorer` - Score calculation
- `StaticAnalyzer` - Type analysis
- `StateRecorder` - Recording with redaction

### Integration Tests

Test component interactions (requires Android emulator):

```powershell
.\gradlew :chronos-agent:connectedAndroidTest
```

**Covered:**
- IPC communication
- Multi-process synchronization
- Memory pressure handling

---

## Manual Testing Checklist

### 1. Basic Recording

```kotlin
// In a test app
class TestActivity : AppCompatActivity() {
    
    private val _state = MutableStateFlow(TestState())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Chronos
        Chronos.initialize(this)
        
        // Register a state source
        Chronos.registerSource(
            sourceId = "TestActivity.state",
            source = { _state.value }
        )
        
        // Trigger some state changes
        _state.value = TestState(count = 1)
        _state.value = TestState(count = 2)
        
        // Verify recording
        Log.d("Test", "Snapshots: ${RecordingMetrics.getSummary().snapshotCount}")
    }
}

data class TestState(val count: Int = 0)
```

### 2. Determinism Classification

```kotlin
// Test Class A (should allow replay)
@ChronosDeterministic
data class SafeState(val id: String, val value: Int)

// Test Class D (should block replay)
@ChronosUnsafe(reason = "Contains timestamp")
data class UnsafeState(val timestamp: Long = System.currentTimeMillis())

// Verify classification
val scoreA = DeterminismScorer.scoreSource(SafeStateSource())
assertEquals(Level.PERFECT, scoreA.level)

val scoreD = DeterminismScorer.scoreSource(UnsafeStateSource())
assertEquals(Level.UNSAFE, scoreD.level)
```

### 3. Memory Pressure

```kotlin
// Simulate memory pressure
val context = ApplicationProvider.getApplicationContext<Context>()
MemoryPressureMonitor.initialize(context)

// Check before
assertFalse(MemoryPressureMonitor.isPaused())

// Trigger low memory callback
MemoryPressureMonitor.onLowMemory()

// Should be paused now
assertTrue(MemoryPressureMonitor.isPaused())
```

### 4. Encryption Performance

```kotlin
// Test cipher caching performance
val server = SecureIPCServer()
val testData = ByteArray(1024) { it.toByte() }

// Warm up
repeat(10) { server.encrypt(testData) }

// Measure
val start = System.nanoTime()
repeat(1000) {
    val encrypted = server.encrypt(testData)
    server.decrypt(encrypted)
}
val elapsed = System.nanoTime() - start

// Should be < 50ms for 1000 operations with caching
assertTrue(elapsed < 50_000_000)
```

### 5. Multi-Process Communication

```kotlin
// Test process registration
val token1 = MultiProcessManager.registerProcess("process1")
val token2 = MultiProcessManager.registerProcess("process2")

assertNotNull(token1)
assertNotNull(token2)
assertFalse(token1.contentEquals(token2))

// Test authenticated update
val success = MultiProcessManager.updateReplayState(
    isReplaying = true,
    sequenceNumber = 100,
    authToken = token1
)
assertTrue(success)

// Test with wrong token
val failure = MultiProcessManager.updateReplayState(
    isReplaying = true,
    sequenceNumber = 101,
    authToken = ByteArray(32) // Wrong token
)
assertFalse(failure)
```

---

## Test Coverage

Generate coverage report:

```powershell
.\gradlew :chronos-agent:testDebugUnitTestCoverage
```

Report location: `chronos-agent/build/reports/coverage/`

---

## Debugging Failed Tests

### View Test Results

```powershell
# Test reports are in:
# chronos-agent/build/reports/tests/testDebugUnitTest/index.html
start chronos-agent\build\reports\tests\testDebugUnitTest\index.html
```

### Enable Verbose Logging

```kotlin
// In test setup
@Before
fun setup() {
    // Enable debug logging for tests
    if (Log.isLoggable("Chronos", Log.DEBUG)) {
        // Already enabled
    }
}
```

### Common Issues

| Issue | Solution |
|-------|----------|
| "BuildConfig not found" | Run `.\gradlew :chronos-agent:generateDebugBuildConfig` |
| "No tests found" | Check test file location in `src/test/java/` |
| Test timeout | Increase timeout: `@Test(timeout = 10000)` |
| Mock issues | Add Mockito dependency in build.gradle |

---

## Adding New Tests

### Template

```kotlin
package com.chronos.agent

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MyComponentTest {

    private lateinit var component: MyComponent

    @Before
    fun setup() {
        component = MyComponent()
    }

    @Test
    fun `test basic functionality`() {
        // Given
        val input = "test"
        
        // When
        val result = component.process(input)
        
        // Then
        assertEquals("expected", result)
    }
    
    @Test
    fun `test edge case`() {
        // Given empty input
        val input = ""
        
        // When/Then - should throw
        assertThrows(IllegalArgumentException::class.java) {
            component.process(input)
        }
    }
}
```

---

## CI/CD Integration

### GitHub Actions

```yaml
# .github/workflows/test.yml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Run Tests
      run: ./gradlew test
    
    - name: Upload Test Results
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: test-results
        path: '**/build/reports/tests/'
```
