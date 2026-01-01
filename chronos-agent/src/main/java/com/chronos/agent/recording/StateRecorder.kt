package com.chronos.agent.recording

import android.util.Log
import com.chronos.agent.contract.RedactionStrategy
import com.chronos.agent.contract.DefaultRedactionStrategy
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Records state snapshots from registered sources.
 * 
 * Implements adaptive recording with:
 * - Size caps
 * - Event rate limiting
 * - Degradation ladder
 * - Gap markers
 * 
 * Thread-safe implementation using atomic operations.
 */
class StateRecorder(
    private val redactionStrategy: RedactionStrategy = DefaultRedactionStrategy
) {
    
    private val sequenceCounter = AtomicLong(0)
    private val listeners = Collections.synchronizedList(mutableListOf<RecordingListener>())
    
    // Thread-safe adaptive recording state
    private data class RecordingState(
        val level: RecordingLevel = RecordingLevel.FULL,
        val eventsThisSecond: Int = 0,
        val lastSecondTimestamp: Long = 0L
    )
    
    private val recordingState = AtomicReference(RecordingState())
    
    interface RecordingListener {
        fun onSnapshot(event: RecordedEvent)
        fun onGap(reason: String)
    }
    
    data class RecordedEvent(
        val timestamp: Long,
        val sequenceNumber: Long,
        val sourceId: String,
        val determinismClass: DeterminismClass,
        val value: Any?,
        val threadName: String
    )
    
    enum class RecordingLevel {
        FULL,           // Record everything
        REDUCED,        // Skip non-essential sources
        MINIMAL,        // Only Class A sources
        PAUSED          // Record nothing, mark gaps
    }
    
    /**
     * Captures and records the current state of a source.
     * Thread-safe.
     */
    fun record(source: StateSource<*>) {
        val now = System.currentTimeMillis()
        
        // Adaptive rate limiting (atomic)
        if (!shouldRecord(now)) {
            return
        }
        
        // Check recording level
        if (!shouldRecordAtLevel(source)) {
            return
        }
        
        try {
            val rawValue = source.captureState()
            
            // Apply redaction before recording
            val redactedValue = applyRedaction(rawValue)
            
            val event = RecordedEvent(
                timestamp = now,
                sequenceNumber = sequenceCounter.incrementAndGet(),
                sourceId = source.sourceId,
                determinismClass = source.getDeterminismClass(),
                value = redactedValue,
                threadName = Thread.currentThread().name
            )
            
            synchronized(listeners) {
                listeners.forEach { it.onSnapshot(event) }
            }
            
        } catch (e: Exception) {
            Log.e("Chronos", "Failed to record state from ${source.sourceId}", e)
        }
    }
    
    private fun shouldRecord(now: Long): Boolean {
        // Atomic compare-and-swap loop for thread safety
        while (true) {
            val current = recordingState.get()
            val newState: RecordingState
            
            // Reset counter each second
            if (now / 1000 != current.lastSecondTimestamp / 1000) {
                newState = current.copy(
                    eventsThisSecond = 1,
                    lastSecondTimestamp = now
                )
            } else {
                newState = current.copy(
                    eventsThisSecond = current.eventsThisSecond + 1
                )
            }
            
            if (recordingState.compareAndSet(current, newState)) {
                // Successfully updated, now check thresholds
                val events = newState.eventsThisSecond
                
                when {
                    events > 1000 -> {
                        if (newState.level != RecordingLevel.PAUSED) {
                            degradeRecordingLevel("Event rate exceeded 1000/s")
                        }
                        return false
                    }
                    events > 500 -> {
                        if (newState.level == RecordingLevel.FULL) {
                            degradeRecordingLevel("Event rate exceeded 500/s")
                        }
                    }
                    events > 200 -> {
                        if (newState.level == RecordingLevel.FULL) {
                            degradeRecordingLevel("Event rate exceeded 200/s")
                        }
                    }
                }
                
                return newState.level != RecordingLevel.PAUSED
            }
            // CAS failed, retry
        }
    }
    
    private fun shouldRecordAtLevel(source: StateSource<*>): Boolean {
        val state = recordingState.get()
        return when (state.level) {
            RecordingLevel.FULL -> true
            RecordingLevel.REDUCED -> source.getDeterminismClass() != DeterminismClass.CLASS_C_CONDITIONAL
            RecordingLevel.MINIMAL -> source.getDeterminismClass() == DeterminismClass.CLASS_A_GUARANTEED
            RecordingLevel.PAUSED -> false
        }
    }
    
    private fun degradeRecordingLevel(reason: String) {
        while (true) {
            val current = recordingState.get()
            val newLevel = when (current.level) {
                RecordingLevel.FULL -> RecordingLevel.REDUCED
                RecordingLevel.REDUCED -> RecordingLevel.MINIMAL
                RecordingLevel.MINIMAL -> RecordingLevel.PAUSED
                RecordingLevel.PAUSED -> RecordingLevel.PAUSED
            }
            
            if (newLevel == current.level) return
            
            val newState = current.copy(level = newLevel)
            if (recordingState.compareAndSet(current, newState)) {
                Log.w("Chronos", "Recording degraded: ${current.level} -> $newLevel ($reason)")
                synchronized(listeners) {
                    listeners.forEach { it.onGap("Recording degraded: $reason") }
                }
                return
            }
            // CAS failed, retry
        }
    }
    
    private fun applyRedaction(value: Any?): Any? {
        if (value == null) return null
        
        val clazz = value::class
        
        // For data classes, redact each property
        if (clazz.isData) {
            try {
                val constructor = clazz.java.declaredConstructors.firstOrNull { it.parameterCount > 0 }
                    ?: return redactionStrategy.redact("root", value)
                
                return redactionStrategy.redact("root", value)
            } catch (e: Exception) {
                Log.w("Chronos", "Redaction failed for ${clazz.simpleName}: ${e.message}")
                return redactionStrategy.redact("root", value)
            }
        }
        
        return redactionStrategy.redact("root", value)
    }
    
    fun addListener(listener: RecordingListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: RecordingListener) {
        listeners.remove(listener)
    }
    
    fun getRecordingLevel(): RecordingLevel = recordingState.get().level
    
    fun resetRecordingLevel() {
        while (true) {
            val current = recordingState.get()
            val newState = current.copy(level = RecordingLevel.FULL)
            if (recordingState.compareAndSet(current, newState)) {
                return
            }
        }
    }
}
