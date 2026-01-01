# ProGuard/R8 rules for Chronos Android Debugger
# These rules ensure Chronos is completely stripped from release builds

# ======================================================================
# CRITICAL: Strip ALL Chronos code from release builds
# ======================================================================

# Remove all Chronos agent classes completely
-assumenosideeffects class com.chronos.agent.** {
    *;
}

# Remove specific Chronos API calls
-assumenosideeffects class com.chronos.agent.Chronos {
    public static void initialize(...);
    public static void registerSource(...);
    public static void unregisterSource(...);
    public static *** get*(...);
    public static void set*(...);
    public *** *(...);
}

# Remove monitoring classes
-assumenosideeffects class com.chronos.agent.monitoring.RecordingMetrics {
    *;
}
-assumenosideeffects class com.chronos.agent.monitoring.MemoryPressureMonitor {
    *;
}

# Remove all recording classes
-assumenosideeffects class com.chronos.agent.recording.** {
    *;
}

# Remove all verification classes
-assumenosideeffects class com.chronos.agent.verification.** {
    *;
}

# Remove all replay classes
-assumenosideeffects class com.chronos.agent.replay.** {
    *;
}

# Remove all IPC classes
-assumenosideeffects class com.chronos.agent.ipc.** {
    *;
}

# Suppress warnings for Chronos classes
-dontwarn com.chronos.agent.**

# ======================================================================
# Keep rules for the no-op artifact (used in release builds)
# ======================================================================

# Keep no-op Chronos class for API compatibility
-keep class com.chronos.agent.Chronos {
    public static *; 
}

# Keep StateSource interface for compatibility
-keep interface com.chronos.agent.recording.StateSource {
    *;
}

# ======================================================================
# Security: Ensure sensitive data is not retained
# ======================================================================

# Remove all logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static boolean isLoggable(...);
}

# ======================================================================
# Optimization: Remove debugging-only code paths
# ======================================================================

# Remove runtime type checks for generics (if safe)
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# ======================================================================
# Protocol Buffers (if used in debug builds)
# ======================================================================

-keep class * extends com.google.protobuf.GeneratedMessageV3 { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ======================================================================
# Kotlin-specific rules
# ======================================================================

# Remove Kotlin intrinsics checks in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
}

# ======================================================================
# Consumer ProGuard Rules (for libraries)
# ======================================================================

# If this is included as a library dependency, these rules propagate
-keep,allowshrinking class com.chronos.** { *; }
