# Chronos Migration Guide

## Migrating from LogCat-based Debugging

### Before: Manual Logging
```kotlin
Log.d("MainViewModel", "State changed: $uiState")
```

### After: Chronos Recording
```kotlin
Chronos.registerSource("MainViewModel.uiState") { uiState }
```

**Benefits:**
- Automatic timestamps and sequencing
- Full state inspection (not just toString)
- Timeline navigation
- State diffing

## Migrating from Stetho/Flipper

### Stetho/Flipper Approach
```kotlin
// Manual network interception
StethoInterceptor()
```

### Chronos Approach
```kotlin
// Automatic state recording
Chronos.initialize(this)
```

**Key Difference:** Chronos focuses on *state* rather than *network traffic*. For network debugging, continue using existing tools. Chronos complements them by showing how network responses affect state.

## Handling Unsafe Sources

### Problem: Class D Sources Block Replay

```
DeterminismViolationException:
  NetworkRepository: Contains HTTP client
```

### Solution 1: Exclude from Recording

```kotlin
// Don't register network layer
// Only register the resulting state
Chronos.registerSource("userState") { userRepository.currentUser }
```

### Solution 2: Use Snapshot Mode

Instead of replay, use inspection-only mode:

```kotlin
// In Chronos Studio, select "Snapshot Mode"
// This reads from recorded history, no live execution
```

### Solution 3: Dependency Injection for Testing

```kotlin
class UserRepository(
    private val api: UserApi = RealUserApi()
) {
    // In tests, inject a mock
}

// During replay:
val mockApi = MockUserApi(recordedResponses)
val repo = UserRepository(mockApi)
```

## Performance Considerations

### Recording Overhead

Chronos uses adaptive recording:
- Normal: Record all events
- High load (>200/s): Skip Class C sources
- Very high (>500/s): Only Class A sources
- Extreme (>1000/s): Pause and mark gaps

### Memory Usage

Default ring buffer: 10,000 events
Adjust in initialization:

```kotlin
Chronos.initialize(this, ChronosConfig(
    bufferCapacity = 5000  // Reduce for memory-constrained devices
))
```

## Common Patterns

### ViewModel Integration

```kotlin
class MyViewModel : ViewModel() {
    private val _state = MutableStateFlow(State())
    
    init {
        Chronos.registerSource("MyViewModel") { _state.value }
    }
    
    override fun onCleared() {
        Chronos.unregisterSource("MyViewModel")
        super.onCleared()
    }
}
```

### Compose Integration

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    
    // State changes are automatically recorded
    // No additional Chronos code needed in Composables
}
```

### Multi-Module Apps

```kotlin
// In each module's initialization
Chronos.registerSource("feature-auth:LoginState") { ... }
Chronos.registerSource("feature-profile:ProfileState") { ... }
```

Use namespaced source IDs to avoid collisions.

## Troubleshooting

### "Chronos must NEVER be included in a release build"

Check your dependencies:
```kotlin
// Wrong
implementation("com.chronos:chronos-agent:0.1.0")

// Correct
debugImplementation("com.chronos:chronos-agent:0.1.0")
releaseImplementation("com.chronos:chronos-agent-noop:0.1.0")
```

### Replay Blocked with Score 0

You have Class D sources registered. Either:
1. Unregister them before replay
2. Use Snapshot mode instead
3. Mock the unsafe dependencies

### State Not Appearing in Timeline

Verify registration:
```kotlin
val sources = Chronos.getRegisteredSources()
// Check if your source ID is listed
```
