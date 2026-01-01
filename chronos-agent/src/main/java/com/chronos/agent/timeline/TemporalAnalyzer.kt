package com.chronos.agent.timeline

/**
 * Temporal analysis metadata for understanding state relationships.
 * 
 * Important: Chronos shows temporal patterns, NOT causality.
 * "A happened after B" is NOT the same as "B caused A".
 */
data class TemporalMetadata(
    val windowId: String,
    val startTime: Long,
    val endTime: Long,
    val events: List<TemporalEvent>
)

data class TemporalEvent(
    val sequenceNumber: Long,
    val timestamp: Long,
    val sourceId: String,
    val relativeOffsetMs: Long  // Offset from window start
)

/**
 * Analyzes temporal relationships between events.
 */
object TemporalAnalyzer {
    
    data class TemporalRelation(
        val eventA: Long,  // sequence number
        val eventB: Long,  // sequence number
        val relation: Relation,
        val deltaMs: Long
    )
    
    enum class Relation {
        BEFORE,         // A happened before B
        AFTER,          // A happened after B
        CONCURRENT,     // A and B happened within threshold
        SAME_THREAD,    // A and B on same thread
        DIFFERENT_THREAD // A and B on different threads
    }
    
    private const val CONCURRENT_THRESHOLD_MS = 5L
    
    /**
     * Analyzes the temporal relationship between two events.
     */
    fun analyzeRelation(eventA: TimelineEvent, eventB: TimelineEvent): TemporalRelation {
        val delta = eventB.timestamp - eventA.timestamp
        
        val relation = when {
            kotlin.math.abs(delta) <= CONCURRENT_THRESHOLD_MS -> Relation.CONCURRENT
            delta > 0 -> Relation.BEFORE
            else -> Relation.AFTER
        }
        
        return TemporalRelation(
            eventA = eventA.sequenceNumber,
            eventB = eventB.sequenceNumber,
            relation = relation,
            deltaMs = delta
        )
    }
    
    /**
     * Groups events into temporal windows for analysis.
     */
    fun groupIntoWindows(events: List<TimelineEvent>, windowSizeMs: Long = 100): List<TemporalMetadata> {
        if (events.isEmpty()) return emptyList()
        
        val windows = mutableListOf<TemporalMetadata>()
        var windowStart = events.first().timestamp
        var windowEvents = mutableListOf<TemporalEvent>()
        var windowId = 0
        
        events.forEach { event ->
            if (event.timestamp - windowStart > windowSizeMs) {
                // Close current window
                if (windowEvents.isNotEmpty()) {
                    windows.add(TemporalMetadata(
                        windowId = "window_$windowId",
                        startTime = windowStart,
                        endTime = windowStart + windowSizeMs,
                        events = windowEvents.toList()
                    ))
                }
                // Start new window
                windowId++
                windowStart = event.timestamp
                windowEvents = mutableListOf()
            }
            
            windowEvents.add(TemporalEvent(
                sequenceNumber = event.sequenceNumber,
                timestamp = event.timestamp,
                sourceId = when (event) {
                    is TimelineEvent.Snapshot -> event.sourceId
                    is TimelineEvent.Checkpoint -> event.checkpointId
                    is TimelineEvent.Gap -> "gap"
                    is TimelineEvent.Log -> event.tag
                },
                relativeOffsetMs = event.timestamp - windowStart
            ))
        }
        
        // Close final window
        if (windowEvents.isNotEmpty()) {
            windows.add(TemporalMetadata(
                windowId = "window_$windowId",
                startTime = windowStart,
                endTime = events.last().timestamp,
                events = windowEvents.toList()
            ))
        }
        
        return windows
    }
}
