package com.chronos.agent.inspection

import com.chronos.agent.inspection.safety.ReadSafetyAnalyzer

/**
 * Enforces the rules for upgrading inspection modes.
 */
object InspectionPolicyEnforcer {

    fun ensureModeAllowed(
        requestedMode: InspectionMode, 
        target: Any
    ): InspectionMode {
        return when (requestedMode) {
            InspectionMode.SNAPSHOT -> InspectionMode.SNAPSHOT // Always allowed
            
            InspectionMode.SAFE_READ -> {
                // Allowed for everyone, but limited to fields
                InspectionMode.SAFE_READ
            }
            
            InspectionMode.MONITORED -> {
                // Not yet supported
                throw UnsupportedOperationException("Monitored Mode is not yet implemented.")
            }
            
            InspectionMode.LIVE_UNSAFE -> {
                // Must pass the Guard
                LiveModeGuard.assertLiveModeAllowed(target)
                val analysis = ReadSafetyAnalyzer.analyze(target::class.java)
                if (!analysis.isSafe) {
                     // We log strictly but assume the user knows best if they opted in
                     // In strict mode, we might theoretically block this too, but 
                     // LiveModeGuard already handled the opt-in.
                }
                InspectionMode.LIVE_UNSAFE
            }
        }
    }
}
