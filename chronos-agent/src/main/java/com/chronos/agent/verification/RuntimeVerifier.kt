package com.chronos.agent.verification

import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Tier 2: Runtime Verification
 * 
 * Performs runtime checks to verify determinism:
 * - Snapshot hashing for comparison
 * - Checkpoint comparison during replay
 * - Divergence detection
 */
object RuntimeVerifier {
    
    private val checkpoints = ConcurrentHashMap<Long, Checkpoint>()
    private var messageDigest: MessageDigest = MessageDigest.getInstance("SHA-256")
    
    data class Checkpoint(
        val sequenceNumber: Long,
        val stateHash: ByteArray,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Checkpoint) return false
            return sequenceNumber == other.sequenceNumber && stateHash.contentEquals(other.stateHash)
        }
        
        override fun hashCode(): Int {
            return 31 * sequenceNumber.hashCode() + stateHash.contentHashCode()
        }
    }
    
    data class VerificationResult(
        val isValid: Boolean,
        val divergenceType: DivergenceType?,
        val message: String
    )
    
    enum class DivergenceType {
        NONE,           // No divergence
        STRUCTURAL,     // Different values - CRITICAL
        TEMPORAL,       // Different ordering, same values - WARNING
        IDENTITY        // Different object identity, same values - INFO
    }
    
    /**
     * Creates a checkpoint for a given state.
     */
    fun createCheckpoint(sequenceNumber: Long, state: Any?): Checkpoint {
        val hash = computeHash(state)
        val checkpoint = Checkpoint(
            sequenceNumber = sequenceNumber,
            stateHash = hash,
            timestamp = System.currentTimeMillis()
        )
        checkpoints[sequenceNumber] = checkpoint
        return checkpoint
    }
    
    /**
     * Verifies that a replayed state matches the recorded checkpoint.
     */
    fun verifyAgainstCheckpoint(sequenceNumber: Long, replayedState: Any?): VerificationResult {
        val recorded = checkpoints[sequenceNumber]
            ?: return VerificationResult(
                isValid = false,
                divergenceType = DivergenceType.STRUCTURAL,
                message = "No checkpoint found for sequence $sequenceNumber"
            )
        
        val replayedHash = computeHash(replayedState)
        
        return if (recorded.stateHash.contentEquals(replayedHash)) {
            VerificationResult(
                isValid = true,
                divergenceType = DivergenceType.NONE,
                message = "Checkpoint verified successfully"
            )
        } else {
            Log.e("Chronos", "🚨 DIVERGENCE at sequence $sequenceNumber")
            VerificationResult(
                isValid = false,
                divergenceType = DivergenceType.STRUCTURAL,
                message = "State hash mismatch at sequence $sequenceNumber"
            )
        }
    }
    
    /**
     * Computes a hash of the given state for comparison.
     */
    private fun computeHash(state: Any?): ByteArray {
        if (state == null) {
            return messageDigest.digest("null".toByteArray())
        }
        
        val representation = buildStateRepresentation(state)
        return messageDigest.digest(representation.toByteArray())
    }
    
    private fun buildStateRepresentation(state: Any): String {
        return try {
            // For data classes, use toString() which includes all properties
            if (state::class.isData) {
                state.toString()
            } else {
                // For other types, use class name + hashCode as fallback
                "${state::class.qualifiedName}@${state.hashCode()}"
            }
        } catch (e: Exception) {
            "${state::class.qualifiedName}@unknown"
        }
    }
    
    /**
     * Clears all checkpoints. For testing.
     */
    internal fun clearCheckpoints() {
        checkpoints.clear()
    }
    
    /**
     * Gets checkpoint count. For diagnostics.
     */
    fun getCheckpointCount(): Int = checkpoints.size
}
