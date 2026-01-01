package com.chronos.agent.inspection.safety

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Reads state from live objects without triggering side effects.
 * 
 * Rules:
 * 1. ONLY access fields directly (setAccessible true).
 * 2. NEVER call methods (getters/setters).
 * 3. NEVER trigger static initialization of unknown classes.
 */
object SafeObjectReader {

    /**
     * Safely extracts the value of a field from a target object.
     */
    fun readField(target: Any, fieldName: String): Any? {
        val clazz = target.javaClass
        return try {
            val field = findFieldRecursively(clazz, fieldName) ?: return "<MISSING>"
            
            // SECURITY CHECK: Ensure we are not triggering side-effects
            if (isUnsafeSystemField(field)) {
                return "<BLOCKED: SYSTEM FIELD>"
            }

            field.isAccessible = true
            field.get(target)
        } catch (e: Exception) {
            "<ERROR: ${e.javaClass.simpleName}>"
        }
    }

    private fun findFieldRecursively(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun isUnsafeSystemField(field: Field): Boolean {
        // Prevent touching sensitive Thread/ThreadGroup internals that might lock
        if (field.declaringClass == Thread::class.java) return true
        if (field.declaringClass == ThreadGroup::class.java) return true
        
        // Prevent touching Binder/IPC internals
        if (field.declaringClass.name.startsWith("android.os.Binder")) return true
        
        return false
    }
}
