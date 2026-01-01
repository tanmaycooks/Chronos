package com.chronos.agent.inspection.safety

import android.util.Log
import java.util.Collections

/**
 * Interface for detecting side effects during object inspection.
 * Used in MONITORED mode to detect and potentially rollback state changes.
 */
interface SideEffectMonitor {
    fun onFieldRead(target: Any, fieldName: String)
    fun onMethodEnter(target: Any, methodName: String)
    fun onMethodExit(target: Any, methodName: String)
    fun hasDetectedSideEffects(): Boolean
    fun getDetectedEffects(): List<String>
    fun reset()
}

/**
 * No-op implementation for when monitoring is disabled.
 */
object NoOpSideEffectMonitor : SideEffectMonitor {
    override fun onFieldRead(target: Any, fieldName: String) {}
    override fun onMethodEnter(target: Any, methodName: String) {}
    override fun onMethodExit(target: Any, methodName: String) {}
    override fun hasDetectedSideEffects() = false
    override fun getDetectedEffects() = emptyList<String>()
    override fun reset() {}
}

/**
 * Active monitoring implementation that tracks potential side effects.
 * Uses deep hash comparison to detect state mutations during inspection.
 */
class ActiveSideEffectMonitor : SideEffectMonitor {
    
    private val detectedEffects = Collections.synchronizedList(mutableListOf<String>())
    private val fieldSnapshots = Collections.synchronizedMap(mutableMapOf<String, Int>())
    
    override fun onFieldRead(target: Any, fieldName: String) {
        // Capture a deep hash of the object state before read
        val key = "${System.identityHashCode(target)}.$fieldName"
        fieldSnapshots[key] = computeDeepHash(target)
    }
    
    override fun onMethodEnter(target: Any, methodName: String) {
        // Capture state before method invocation
        val key = "${System.identityHashCode(target)}.$methodName"
        fieldSnapshots[key] = computeDeepHash(target)
        
        // Flag any method invocation as a potential side effect
        // Methods should NOT be called in SAFE_READ mode
        val effect = "Method invoked: ${target.javaClass.simpleName}.$methodName()"
        detectedEffects.add(effect)
        Log.w("Chronos", "⚠️ Side-effect detected: $effect")
    }
    
    override fun onMethodExit(target: Any, methodName: String) {
        // Check if object state changed after method call
        val key = "${System.identityHashCode(target)}.$methodName"
        val previousHash = fieldSnapshots[key]
        val currentHash = computeDeepHash(target)
        
        if (previousHash != null && previousHash != currentHash) {
            val effect = "State mutated after: ${target.javaClass.simpleName}.$methodName()"
            detectedEffects.add(effect)
            Log.e("Chronos", "🚨 State mutation detected: $effect")
        }
    }
    
    /**
     * Computes a deep hash for state comparison.
     * 
     * Uses multiple signals to detect changes:
     * 1. System identity hash (object reference)
     * 2. toString representation (if available)
     * 3. Fallback to hashCode
     */
    private fun computeDeepHash(target: Any): Int {
        return try {
            // Combine multiple signals for better mutation detection
            var hash = System.identityHashCode(target)
            
            // Try to get toString for content-based hash
            val stringRep = try {
                target.toString()
            } catch (e: Exception) {
                ""
            }
            
            if (stringRep.isNotEmpty() && !stringRep.contains("@")) {
                // toString appears to be overridden with meaningful content
                hash = 31 * hash + stringRep.hashCode()
            } else {
                // Fallback: use object's own hashCode
                hash = 31 * hash + target.hashCode()
            }
            
            hash
        } catch (e: Exception) {
            System.identityHashCode(target)
        }
    }
    
    override fun hasDetectedSideEffects(): Boolean = detectedEffects.isNotEmpty()
    
    override fun getDetectedEffects(): List<String> = detectedEffects.toList()
    
    override fun reset() {
        detectedEffects.clear()
        fieldSnapshots.clear()
    }
}
