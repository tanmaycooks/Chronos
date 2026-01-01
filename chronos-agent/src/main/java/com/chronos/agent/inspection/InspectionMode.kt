package com.chronos.agent.inspection

/**
 * Defines the strictness of state inspection.
 * Chronos defaults to SNAPSHOT to guarantee zero side effects.
 */
enum class InspectionMode {
    /**
     * READ-ONLY, SERIALIZED data.
     * Guaranteed zero side effects because it reads from the buffer, not live objects.
     * This is the DEFAULT mode.
     */
    SNAPSHOT,

    /**
     * READ-ONLY, LIVE objects via reflection.
     * Strictly allows mostly direct field access.
     * Getters are FORBIDDEN unless whitelisted (e.g., standard libraries).
     */
    SAFE_READ,

    /**
     * READ-ONLY, LIVE objects with side effect monitoring.
     * (Not yet implemented)
     * Detects state changes during inspection and rolls back (or aborts).
     */
    MONITORED,

    /**
     * UNSAFE, LIVE objects.
     * Allows getter execution, method calls, etc.
     * Requires explicit developer opt-in with warnings.
     */
    LIVE_UNSAFE
}
