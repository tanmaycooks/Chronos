package com.chronos.agent.inspection

import android.util.Log

/**
 * Manages the explicit opt-in requirement for Live Mode.
 * Ensures developers are warned before enabling side-effect prone inspection.
 */
object LiveModeGuard {

    private val explicitlyAllowedClasses = mutableSetOf<String>()
    
    /**
     * Attempts to enable Live Mode for a specific target.
     * Throws [SecurityException] if explicit opt-in is missing.
     */
    fun assertLiveModeAllowed(target: Any) {
        val className = target.javaClass.name
        if (!explicitlyAllowedClasses.contains(className)) {
            val warning = """
                CHRONOS REFUSAL: Live Mode Blocked for $className
                
                Reason: Live inspection can cause side effects (triggering lazy loaders, 
                modifying caches, network I/O in getters).
                
                Mitigation: You must explicitly opt-in this class using:
                Chronos.allowLiveInspection(this)
            """.trimIndent()
            
            Log.e("Chronos", warning)
            throw SecurityException(warning)
        }
        
        Log.w("Chronos", "⚠️ LIVE INSPECTION ACTIVE: Side-effects possible for $className")
    }

    fun allow(clazz: Class<*>) {
        explicitlyAllowedClasses.add(clazz.name)
    }
}
