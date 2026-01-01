package com.chronos.agent.inspection.safety

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Analyzes classes to detect potential risks during inspection.
 * Flags:
 * - Synchronized getters (Deadlock risk)
 * - Non-standard getters (void return, arguments)
 * - Known unsafe types
 */
object ReadSafetyAnalyzer {

    data class RiskReport(
        val isSafe: Boolean,
        val risks: List<String>
    )

    fun analyze(clazz: Class<*>): RiskReport {
        val risks = mutableListOf<String>()

        // 1. Check for synchronization risks
        clazz.declaredMethods.forEach { method ->
            if (isGetter(method)) {
                if (Modifier.isSynchronized(method.modifiers)) {
                    risks.add("Blocking Risk: Getter '${method.name}' is synchronized.")
                }
            }
        }

        // 2. Check for known unsafe patterns (Lazy delegates often use synchronized)
        clazz.declaredFields.forEach { field ->
            if (field.type.name.contains("Lazy") || field.type.name.contains("Synchronized")) {
                risks.add("Lazy Initialization Risk: Field '${field.name}' might trigger lazy initialization/locking.")
            }
        }

        return RiskReport(
            isSafe = risks.isEmpty(),
            risks = risks
        )
    }

    private fun isGetter(method: Method): Boolean {
        return method.name.startsWith("get") && 
               method.parameterCount == 0 && 
               method.returnType != Void.TYPE
    }
}
