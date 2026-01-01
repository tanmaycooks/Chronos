package com.chronos.agent.timeline

import com.chronos.agent.recording.DeterminismClass

/**
 * Core timeline data model for Chronos.
 * 
 * A Timeline is a sequence of events capturing state evolution over time.
 * It is designed for:
 * - Temporal analysis (not causality)
 * - Deterministic replay (when eligible)
 * - Gap-tolerant recording
 */
data class Timeline(
    val id: String,
    val metadata: TimelineMetadata,
    val events: List<TimelineEvent>
) {
    fun getEventCount(): Int = events.size
    fun getGapCount(): Int = events.count { it is TimelineEvent.Gap }
    fun getSpanMs(): Long = if (events.isEmpty()) 0 else events.last().timestamp - events.first().timestamp
}

data class TimelineMetadata(
    val formatVersion: Int,
    val chronosVersion: String,
    val androidSdkVersion: Int,
    val kotlinVersion: String,
    val createdAt: Long,
    val appPackage: String,
    val processName: String
)

/**
 * Sealed class representing all possible timeline events.
 */
sealed class TimelineEvent {
    abstract val timestamp: Long
    abstract val sequenceNumber: Long
    abstract val threadName: String
    
    /**
     * A state snapshot captured from a StateSource.
     * 
     * SECURITY: ByteArrays are defensively copied to prevent external modification.
     */
    data class Snapshot(
        override val timestamp: Long,
        override val sequenceNumber: Long,
        override val threadName: String,
        val sourceId: String,
        val determinismClass: DeterminismClass,
        val valueType: String,
        private val _valueBytes: ByteArray,
        private val _checkpointHash: ByteArray?
    ) : TimelineEvent() {
        
        // Defensive copies on access
        val valueBytes: ByteArray get() = _valueBytes.copyOf()
        val checkpointHash: ByteArray? get() = _checkpointHash?.copyOf()
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Snapshot) return false
            return sequenceNumber == other.sequenceNumber && 
                   sourceId == other.sourceId &&
                   _valueBytes.contentEquals(other._valueBytes)
        }
        
        override fun hashCode(): Int {
            var result = sequenceNumber.hashCode()
            result = 31 * result + sourceId.hashCode()
            result = 31 * result + _valueBytes.contentHashCode()
            return result
        }
        
        companion object {
            /**
             * Factory method that creates a Snapshot with defensive copies.
             */
            fun create(
                timestamp: Long,
                sequenceNumber: Long,
                threadName: String,
                sourceId: String,
                determinismClass: DeterminismClass,
                valueType: String,
                valueBytes: ByteArray,
                checkpointHash: ByteArray?
            ): Snapshot = Snapshot(
                timestamp = timestamp,
                sequenceNumber = sequenceNumber,
                threadName = threadName,
                sourceId = sourceId,
                determinismClass = determinismClass,
                valueType = valueType,
                _valueBytes = valueBytes.copyOf(),
                _checkpointHash = checkpointHash?.copyOf()
            )
        }
    }
    
    /**
     * A checkpoint marker for verification during replay.
     */
    data class Checkpoint(
        override val timestamp: Long,
        override val sequenceNumber: Long,
        override val threadName: String,
        val checkpointId: String,
        private val _stateHash: ByteArray,
        val sourceCount: Int
    ) : TimelineEvent() {
        
        val stateHash: ByteArray get() = _stateHash.copyOf()
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Checkpoint) return false
            return checkpointId == other.checkpointId &&
                   _stateHash.contentEquals(other._stateHash)
        }
        
        override fun hashCode(): Int {
            var result = checkpointId.hashCode()
            result = 31 * result + _stateHash.contentHashCode()
            return result
        }
    }
    
    /**
     * A gap marker indicating missed events due to adaptive recording.
     */
    data class Gap(
        override val timestamp: Long,
        override val sequenceNumber: Long,
        override val threadName: String,
        val reason: String,
        val missedEventCount: Int?,
        val durationMs: Long?
    ) : TimelineEvent()
    
    /**
     * A log event for debugging context.
     */
    data class Log(
        override val timestamp: Long,
        override val sequenceNumber: Long,
        override val threadName: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) : TimelineEvent()
    
    enum class LogLevel { VERBOSE, DEBUG, INFO, WARNING, ERROR }
}
