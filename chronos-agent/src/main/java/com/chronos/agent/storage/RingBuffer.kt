package com.chronos.agent.storage

import android.util.Log
import com.chronos.agent.timeline.TimelineEvent
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Ring-buffered, append-only storage for timeline events.
 * 
 * Design:
 * - Fixed capacity to bound memory usage
 * - Overwrites oldest events when full
 * - Gap markers inserted on overflow
 * - Thread-safe for concurrent access
 */
class RingBuffer(
    private val capacity: Int = DEFAULT_CAPACITY
) {
    companion object {
        const val DEFAULT_CAPACITY = 10000
        const val MIN_CAPACITY = 100
    }
    
    private val buffer = arrayOfNulls<TimelineEvent>(capacity)
    private var head = 0  // Next write position
    private var tail = 0  // Oldest event position
    private var size = 0
    private var overflowCount = 0L
    private val lock = ReentrantReadWriteLock()
    
    /**
     * Appends an event to the buffer.
     * Returns true if an old event was overwritten.
     */
    fun append(event: TimelineEvent): Boolean {
        return lock.write {
            val overflow = size == capacity
            
            if (overflow) {
                // Insert gap marker before overwriting
                val gapMarker = TimelineEvent.Gap(
                    timestamp = System.currentTimeMillis(),
                    sequenceNumber = -1, // Special marker for overflow gaps
                    threadName = "chronos-internal",
                    reason = "Buffer overflow - oldest events discarded",
                    missedEventCount = 1,
                    durationMs = null
                )
                buffer[tail] = gapMarker
                overflowCount++
                tail = (tail + 1) % capacity
            } else {
                size++
            }
            
            buffer[head] = event
            head = (head + 1) % capacity
            
            if (overflow && overflowCount % 1000 == 0L) {
                Log.w("Chronos", "RingBuffer overflow: $overflowCount events lost")
            }
            
            overflow
        }
    }
    
    /**
     * Gets all events in chronological order.
     */
    fun getAll(): List<TimelineEvent> {
        return lock.read {
            val result = mutableListOf<TimelineEvent>()
            var index = tail
            repeat(size) {
                buffer[index]?.let { result.add(it) }
                index = (index + 1) % capacity
            }
            result
        }
    }
    
    /**
     * Gets events within a sequence number range.
     * Optimized to avoid full buffer iteration.
     */
    fun getRange(fromSequence: Long, toSequence: Long): List<TimelineEvent> {
        return lock.read {
            val result = mutableListOf<TimelineEvent>()
            var index = tail
            repeat(size) {
                buffer[index]?.let { event ->
                    if (event.sequenceNumber in fromSequence..toSequence) {
                        result.add(event)
                    }
                }
                index = (index + 1) % capacity
            }
            result
        }
    }
    
    /**
     * Gets the most recent N events.
     */
    fun getRecent(count: Int): List<TimelineEvent> {
        return lock.read {
            val result = mutableListOf<TimelineEvent>()
            val actualCount = minOf(count, size)
            var index = (head - actualCount + capacity) % capacity
            repeat(actualCount) {
                buffer[index]?.let { result.add(it) }
                index = (index + 1) % capacity
            }
            result
        }
    }
    
    /**
     * Gets events for a specific source.
     */
    fun getBySource(sourceId: String): List<TimelineEvent> {
        return getAll().filter { event ->
            event is TimelineEvent.Snapshot && event.sourceId == sourceId
        }
    }
    
    fun getSize(): Int = lock.read { size }
    fun getCapacity(): Int = capacity
    fun getOverflowCount(): Long = lock.read { overflowCount }
    fun isEmpty(): Boolean = lock.read { size == 0 }
    
    /**
     * Clears all events. For testing.
     */
    internal fun clear() {
        lock.write {
            buffer.fill(null)
            head = 0
            tail = 0
            size = 0
            overflowCount = 0
        }
    }
}
