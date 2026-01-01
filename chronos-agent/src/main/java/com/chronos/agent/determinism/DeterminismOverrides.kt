package com.chronos.agent.determinism

import android.util.Log

/**
 * Annotation to explicitly opt-in a class for Class C (Conditionally Safe) recording.
 * 
 * Usage:
 * ```
 * @ChronosConditionalSafe(
 *     reason = "This subsystem is isolated and has no external dependencies"
 * )
 * class MyCustomStateHolder { ... }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosConditionalSafe(
    val reason: String
)

/**
 * Annotation to mark a class as explicitly unsafe (Class D).
 * This prevents accidental inclusion in replay-safe sources.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosUnsafe(
    val reason: String = "Contains non-deterministic behavior"
)

/**
 * Annotation to mark a class as guaranteed deterministic (Class A).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosDeterministic

/**
 * Manages opt-in for Class C sources and blocking of Class D sources.
 */
object DeterminismOverrides {
    
    private val classCAcknowledgments = mutableSetOf<String>()
    private val classDBlocks = mutableMapOf<String, String>() // className -> reason
    
    /**
     * Explicitly acknowledges a Class C source for recording.
     * This is required before Class C sources can be replayed.
     */
    fun acknowledgeConditionalSource(source: Any) {
        val className = source.javaClass.name
        val annotation = source.javaClass.getAnnotation(ChronosConditionalSafe::class.java)
        
        if (annotation != null) {
            Log.w("Chronos", """
                ⚠️ Class C Source Acknowledged: $className
                Reason: ${annotation.reason}
                Warning: Replay correctness depends on developer verification.
            """.trimIndent())
            classCAcknowledgments.add(className)
        } else {
            Log.w("Chronos", """
                ⚠️ Class C Source Acknowledged (no annotation): $className
                Warning: This source has no @ChronosConditionalSafe annotation.
                Consider adding one to document why this is safe.
            """.trimIndent())
            classCAcknowledgments.add(className)
        }
    }
    
    /**
     * Checks if a Class C source has been acknowledged.
     */
    fun isConditionalSourceAcknowledged(className: String): Boolean {
        return classCAcknowledgments.contains(className)
    }
    
    /**
     * Records a Class D block reason.
     */
    fun recordClassDBlock(className: String, reason: String) {
        classDBlocks[className] = reason
        Log.e("Chronos", "🚫 Class D Source Blocked: $className - $reason")
    }
    
    /**
     * Gets all blocked Class D sources and their reasons.
     */
    fun getBlockedSources(): Map<String, String> = classDBlocks.toMap()
    
    /**
     * Clears all acknowledgments. For testing only.
     */
    internal fun clearAcknowledgments() {
        classCAcknowledgments.clear()
        classDBlocks.clear()
    }
}

/**
 * Exception thrown when attempting to replay with Class D sources present.
 */
class DeterminismViolationException(
    val blockedSources: Map<String, String>
) : SecurityException(buildMessage(blockedSources)) {
    
    companion object {
        private fun buildMessage(sources: Map<String, String>): String {
            val details = sources.entries.joinToString("\n") { (name, reason) ->
                "  - $name: $reason"
            }
            return """
                CHRONOS REFUSAL: Replay Blocked
                
                The following Class D (Unsafe) sources are present:
                $details
                
                Replay cannot proceed because determinism cannot be guaranteed.
                
                Mitigation:
                1. Remove these sources before replay, OR
                2. Use Snapshot/Safe-Read inspection instead of replay
            """.trimIndent()
        }
    }
}
