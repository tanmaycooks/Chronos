package com.chronos.agent.verification

import com.chronos.agent.recording.DeterminismClass
import com.chronos.agent.recording.StateSource
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Tier 1: Static Analysis
 * 
 * Performs compile-time-like analysis on types to detect:
 * - Known unsafe type patterns
 * - Dependency graph risks
 * - Unsafe field types within otherwise safe classes
 */
object StaticAnalyzer {
    
    data class AnalysisResult(
        val classification: DeterminismClass,
        val risks: List<Risk>,
        val score: Int // 0-100, higher is safer
    )
    
    data class Risk(
        val severity: Severity,
        val description: String,
        val fieldPath: String? = null
    )
    
    enum class Severity {
        CRITICAL,   // Blocks replay
        WARNING,    // Allows replay with warning
        INFO        // Informational only
    }
    
    // Patterns that immediately flag Class D
    private val criticalUnsafePatterns = listOf(
        "java.util.Random" to "Uses random number generation",
        "kotlin.random.Random" to "Uses random number generation",
        "java.security.SecureRandom" to "Uses cryptographic randomness",
        "java.util.Date" to "Captures system time",
        "java.time.Instant" to "Captures system time",
        "java.time.LocalDateTime" to "Captures system time",
        "System.currentTimeMillis" to "Captures system time",
        "System.nanoTime" to "Captures system time",
    )
    
    private val networkPatterns = listOf(
        "java.net." to "Network I/O",
        "okhttp3." to "HTTP client",
        "retrofit2." to "HTTP client",
        "io.ktor." to "HTTP client",
    )
    
    private val storagePatterns = listOf(
        "java.io.File" to "File I/O",
        "java.nio.file." to "File I/O",
        "android.database." to "Database access",
        "androidx.room." to "Database access",
        "android.content.SharedPreferences" to "SharedPreferences access",
    )
    
    /**
     * Analyzes a state source for determinism risks.
     * 
     * IMPORTANT: This method does NOT call captureState() to avoid side effects.
     * It analyzes the declared return type instead.
     */
    fun analyze(source: StateSource<*>): AnalysisResult {
        // Analyze the StateSource interface's type parameter without calling captureState()
        // This avoids triggering any side effects during analysis
        val sourceClass = source::class.java
        
        // Try to get the type parameter from the interface
        val stateType = try {
            sourceClass.methods
                .find { it.name == "captureState" && it.parameterCount == 0 }
                ?.returnType?.kotlin
        } catch (e: Exception) {
            null
        }
        
        return if (stateType != null && stateType != Any::class) {
            analyzeType(stateType)
        } else {
            // Fallback: use declared determinism class
            AnalysisResult(source.getDeterminismClass(), emptyList(), 100)
        }
    }
    
    /**
     * Analyzes a type for determinism risks.
     */
    fun analyzeType(type: KClass<*>): AnalysisResult {
        val risks = mutableListOf<Risk>()
        var worstClass = DeterminismClass.CLASS_A_GUARANTEED
        
        val typeName = type.qualifiedName ?: "Unknown"
        
        // Check type itself against patterns
        checkPatterns(typeName, null, risks)
        
        // Check all fields recursively
        try {
            type.memberProperties.forEach { prop ->
                val fieldType = prop.returnType.classifier as? KClass<*>
                if (fieldType != null) {
                    val fieldTypeName = fieldType.qualifiedName ?: "Unknown"
                    checkPatterns(fieldTypeName, prop.name, risks)
                }
            }
        } catch (e: Exception) {
            // Reflection might fail for some types
            risks.add(Risk(Severity.WARNING, "Could not analyze all fields: ${e.message}"))
        }
        
        // Determine worst classification
        risks.forEach { risk ->
            when (risk.severity) {
                Severity.CRITICAL -> worstClass = DeterminismClass.CLASS_D_UNSAFE
                Severity.WARNING -> if (worstClass != DeterminismClass.CLASS_D_UNSAFE) {
                    worstClass = DeterminismClass.CLASS_C_CONDITIONAL
                }
                Severity.INFO -> {} // No change
            }
        }
        
        // Calculate score
        val score = calculateScore(risks)
        
        return AnalysisResult(worstClass, risks, score)
    }
    
    private fun checkPatterns(typeName: String, fieldPath: String?, risks: MutableList<Risk>) {
        // Check critical unsafe patterns
        criticalUnsafePatterns.forEach { (pattern, reason) ->
            if (typeName.contains(pattern)) {
                risks.add(Risk(Severity.CRITICAL, reason, fieldPath))
            }
        }
        
        // Check network patterns
        networkPatterns.forEach { (pattern, reason) ->
            if (typeName.startsWith(pattern)) {
                risks.add(Risk(Severity.CRITICAL, reason, fieldPath))
            }
        }
        
        // Check storage patterns
        storagePatterns.forEach { (pattern, reason) ->
            if (typeName.startsWith(pattern)) {
                risks.add(Risk(Severity.CRITICAL, reason, fieldPath))
            }
        }
    }
    
    private fun calculateScore(risks: List<Risk>): Int {
        var score = 100
        risks.forEach { risk ->
            when (risk.severity) {
                Severity.CRITICAL -> score -= 50
                Severity.WARNING -> score -= 20
                Severity.INFO -> score -= 5
            }
        }
        return maxOf(0, score)
    }
}
