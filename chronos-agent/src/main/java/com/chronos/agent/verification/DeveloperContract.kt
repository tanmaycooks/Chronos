package com.chronos.agent.verification

import android.util.Log
import com.chronos.agent.determinism.ChronosConditionalSafe
import com.chronos.agent.determinism.ChronosDeterministic
import com.chronos.agent.determinism.ChronosUnsafe
import com.chronos.agent.recording.DeterminismClass

/**
 * Tier 3: Developer Contract
 * 
 * Manages developer-provided determinism assertions:
 * - Annotations (@ChronosDeterministic, @ChronosConditionalSafe, @ChronosUnsafe)
 * - Runtime assertions
 * - Explicit overrides with acknowledgment
 */
object DeveloperContract {
    
    private val overrides = mutableMapOf<String, Override>()
    private val assertions = mutableListOf<Assertion>()
    
    data class Override(
        val className: String,
        val declaredClass: DeterminismClass,
        val reason: String,
        val acknowledgedAt: Long
    )
    
    data class Assertion(
        val id: String,
        val condition: () -> Boolean,
        val failureMessage: String
    )
    
    data class ContractResult(
        val classification: DeterminismClass?,
        val source: ContractSource,
        val reason: String?
    )
    
    enum class ContractSource {
        ANNOTATION,
        OVERRIDE,
        ASSERTION,
        NONE
    }
    
    /**
     * Checks annotations on a class to determine its contract.
     */
    fun checkAnnotations(clazz: Class<*>): ContractResult {
        // Check for @ChronosUnsafe
        clazz.getAnnotation(ChronosUnsafe::class.java)?.let { annotation ->
            return ContractResult(
                classification = DeterminismClass.CLASS_D_UNSAFE,
                source = ContractSource.ANNOTATION,
                reason = annotation.reason
            )
        }
        
        // Check for @ChronosDeterministic
        clazz.getAnnotation(ChronosDeterministic::class.java)?.let {
            return ContractResult(
                classification = DeterminismClass.CLASS_A_GUARANTEED,
                source = ContractSource.ANNOTATION,
                reason = "Marked as deterministic by developer"
            )
        }
        
        // Check for @ChronosConditionalSafe
        clazz.getAnnotation(ChronosConditionalSafe::class.java)?.let { annotation ->
            return ContractResult(
                classification = DeterminismClass.CLASS_C_CONDITIONAL,
                source = ContractSource.ANNOTATION,
                reason = annotation.reason
            )
        }
        
        return ContractResult(null, ContractSource.NONE, null)
    }
    
    /**
     * Registers an explicit override for a class.
     */
    fun registerOverride(
        className: String,
        declaredClass: DeterminismClass,
        reason: String
    ) {
        val override = Override(
            className = className,
            declaredClass = declaredClass,
            reason = reason,
            acknowledgedAt = System.currentTimeMillis()
        )
        overrides[className] = override
        
        Log.w("Chronos", """
            ⚠️ Developer Override Registered
            Class: $className
            Declared: $declaredClass
            Reason: $reason
        """.trimIndent())
    }
    
    /**
     * Checks if an override exists for a class.
     */
    fun getOverride(className: String): Override? = overrides[className]
    
    /**
     * Registers a runtime assertion that must pass for replay.
     */
    fun registerAssertion(id: String, condition: () -> Boolean, failureMessage: String) {
        assertions.add(Assertion(id, condition, failureMessage))
    }
    
    /**
     * Verifies all registered assertions.
     */
    fun verifyAssertions(): List<String> {
        val failures = mutableListOf<String>()
        assertions.forEach { assertion ->
            try {
                if (!assertion.condition()) {
                    failures.add("${assertion.id}: ${assertion.failureMessage}")
                }
            } catch (e: Exception) {
                failures.add("${assertion.id}: Exception during check - ${e.message}")
            }
        }
        return failures
    }
    
    /**
     * Clears all overrides and assertions. For testing.
     */
    internal fun clear() {
        overrides.clear()
        assertions.clear()
    }
}
