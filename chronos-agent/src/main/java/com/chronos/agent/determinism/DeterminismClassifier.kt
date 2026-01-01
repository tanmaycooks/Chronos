package com.chronos.agent.determinism

import com.chronos.agent.recording.DeterminismClass
import kotlin.reflect.KClass

/**
 * Classifies types into determinism classes based on their characteristics.
 * 
 * This is the core of Chronos' trust model:
 * - Class A: Guaranteed safe (immutable, pure data)
 * - Class B: Verifiable safe (observable state holders)
 * - Class C: Conditionally safe (developer-verified)
 * - Class D: Unsafe (IO, randomness, time)
 */
object DeterminismClassifier {
    
    // Known Class D (unsafe) type patterns
    private val unsafePatterns = listOf(
        "java.util.Random",
        "kotlin.random.Random",
        "java.security.SecureRandom",
        "java.util.Date",
        "java.time.Instant",
        "java.time.LocalDateTime",
        "java.net.",
        "java.io.",
        "java.nio.file.",
        "android.database.",
        "android.content.SharedPreferences",
        "okhttp3.",
        "retrofit2.",
        "kotlinx.coroutines.flow.MutableSharedFlow", // Can have external emissions
    )
    
    // Known Class B (verifiable) type patterns
    private val verifiablePatterns = listOf(
        "kotlinx.coroutines.flow.StateFlow",
        "kotlinx.coroutines.flow.MutableStateFlow",
        "androidx.lifecycle.LiveData",
        "androidx.lifecycle.MutableLiveData",
        "androidx.compose.runtime.MutableState",
        "androidx.compose.runtime.State",
    )
    
    // Known Class A (guaranteed safe) type patterns
    private val guaranteedSafePatterns = listOf(
        "kotlin.String",
        "java.lang.String",
        "kotlin.Int",
        "kotlin.Long",
        "kotlin.Float",
        "kotlin.Double",
        "kotlin.Boolean",
        "kotlin.collections.List",
        "kotlin.collections.Set",
        "kotlin.collections.Map",
        "kotlinx.collections.immutable.",
    )
    
    /**
     * Classifies a type into a determinism class.
     */
    fun classify(type: KClass<*>): DeterminismClass {
        val typeName = type.qualifiedName ?: return DeterminismClass.CLASS_D_UNSAFE
        
        // Check for Class D (unsafe) first - most restrictive
        if (unsafePatterns.any { typeName.startsWith(it) }) {
            return DeterminismClass.CLASS_D_UNSAFE
        }
        
        // Check for Class B (verifiable)
        if (verifiablePatterns.any { typeName.startsWith(it) }) {
            return DeterminismClass.CLASS_B_VERIFIABLE
        }
        
        // Check for Class A (guaranteed safe)
        if (guaranteedSafePatterns.any { typeName.startsWith(it) }) {
            return DeterminismClass.CLASS_A_GUARANTEED
        }
        
        // Check if it's a data class (Kotlin)
        if (type.isData) {
            return DeterminismClass.CLASS_A_GUARANTEED
        }
        
        // Check if it's a sealed class
        if (type.isSealed) {
            return DeterminismClass.CLASS_A_GUARANTEED
        }
        
        // Default: Conditional (requires developer verification)
        return DeterminismClass.CLASS_C_CONDITIONAL
    }
    
    /**
     * Classifies a Java class.
     */
    fun classify(type: Class<*>): DeterminismClass = classify(type.kotlin)
    
    /**
     * Checks if a type is safe for replay (Class A or B only).
     */
    fun isReplaySafe(type: KClass<*>): Boolean {
        val classification = classify(type)
        return classification == DeterminismClass.CLASS_A_GUARANTEED ||
               classification == DeterminismClass.CLASS_B_VERIFIABLE
    }
}
