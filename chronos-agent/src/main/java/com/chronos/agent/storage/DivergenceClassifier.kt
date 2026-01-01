package com.chronos.agent.storage

import com.chronos.agent.verification.RuntimeVerifier

/**
 * Divergence classification for replay verification.
 * 
 * Types:
 * - Structural: Different values (CRITICAL - halt replay)
 * - Temporal: Different ordering, same values (WARNING)
 * - Identity: Different object identity, same values (INFO)
 */
enum class DivergenceType {
    NONE,
    STRUCTURAL,
    TEMPORAL,
    IDENTITY
}

data class DivergenceReport(
    val type: DivergenceType,
    val sequenceNumber: Long,
    val sourceId: String?,
    val expectedHash: String?,
    val actualHash: String?,
    val description: String
)

/**
 * Classifies divergences detected during replay.
 */
object DivergenceClassifier {
    
    /**
     * Classifies a verification result into a divergence type.
     */
    fun classify(
        result: RuntimeVerifier.VerificationResult,
        sequenceNumber: Long,
        sourceId: String?
    ): DivergenceReport {
        return when (result.divergenceType) {
            RuntimeVerifier.DivergenceType.NONE -> DivergenceReport(
                type = DivergenceType.NONE,
                sequenceNumber = sequenceNumber,
                sourceId = sourceId,
                expectedHash = null,
                actualHash = null,
                description = "No divergence"
            )
            
            RuntimeVerifier.DivergenceType.STRUCTURAL -> DivergenceReport(
                type = DivergenceType.STRUCTURAL,
                sequenceNumber = sequenceNumber,
                sourceId = sourceId,
                expectedHash = null,
                actualHash = null,
                description = "CRITICAL: ${result.message}"
            )
            
            RuntimeVerifier.DivergenceType.TEMPORAL -> DivergenceReport(
                type = DivergenceType.TEMPORAL,
                sequenceNumber = sequenceNumber,
                sourceId = sourceId,
                expectedHash = null,
                actualHash = null,
                description = "WARNING: ${result.message}"
            )
            
            RuntimeVerifier.DivergenceType.IDENTITY -> DivergenceReport(
                type = DivergenceType.IDENTITY,
                sequenceNumber = sequenceNumber,
                sourceId = sourceId,
                expectedHash = null,
                actualHash = null,
                description = "INFO: ${result.message}"
            )
            
            null -> DivergenceReport(
                type = DivergenceType.STRUCTURAL,
                sequenceNumber = sequenceNumber,
                sourceId = sourceId,
                expectedHash = null,
                actualHash = null,
                description = "Unknown divergence: ${result.message}"
            )
        }
    }
    
    /**
     * Determines if a divergence should halt replay.
     */
    fun shouldHalt(type: DivergenceType): Boolean {
        return type == DivergenceType.STRUCTURAL
    }
    
    /**
     * Determines if a divergence should show a warning.
     */
    fun shouldWarn(type: DivergenceType): Boolean {
        return type == DivergenceType.TEMPORAL
    }
}
