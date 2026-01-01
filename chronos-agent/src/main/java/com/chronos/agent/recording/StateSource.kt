package com.chronos.agent.recording

/**
 * Represents a source of observable state in the application.
 * 
 * State sources must be explicitly registered with Chronos.
 * Each source is classified by its determinism level (Class A/B/C/D).
 * 
 * @param T The type of state this source provides.
 */
interface StateSource<T> {
    
    /**
     * Unique identifier for this state source.
     * Used for correlation across recordings and replays.
     */
    val sourceId: String
    
    /**
     * Human-readable name for display in the Studio UI.
     */
    val displayName: String
        get() = sourceId
    
    /**
     * Returns the current state value.
     * This method should be side-effect free for Class A/B sources.
     */
    fun captureState(): T
    
    /**
     * Returns the determinism classification of this source.
     */
    fun getDeterminismClass(): DeterminismClass
}

/**
 * Determinism classification as per the Chronos design document.
 */
enum class DeterminismClass {
    /**
     * Class A: Guaranteed Safe
     * Pure data classes, sealed classes without logic, primitives, immutable collections.
     */
    CLASS_A_GUARANTEED,
    
    /**
     * Class B: Verifiable Safe
     * ViewModel state (StateFlow), Compose MutableState, sources verified at runtime.
     */
    CLASS_B_VERIFIABLE,
    
    /**
     * Class C: Conditionally Safe
     * Isolated subsystems, developer-verified components. Requires explicit override.
     */
    CLASS_C_CONDITIONAL,
    
    /**
     * Class D: Unsafe
     * Network, Database, File I/O, System time, Randomness, etc.
     * Replay is ALWAYS blocked for Class D sources.
     */
    CLASS_D_UNSAFE
}
