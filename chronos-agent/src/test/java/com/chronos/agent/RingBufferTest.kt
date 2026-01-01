package com.chronos.agent

import com.chronos.agent.storage.RingBuffer
import com.chronos.agent.timeline.TimelineEvent
import com.chronos.agent.recording.DeterminismClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for RingBuffer storage.
 */
class RingBufferTest {
    
    private lateinit var buffer: RingBuffer
    
    @Before
    fun setup() {
        buffer = RingBuffer(capacity = 10)
    }
    
    @Test
    fun `append adds events to buffer`() {
        val event = createSnapshot(1)
        buffer.append(event)
        
        assertEquals(1, buffer.getSize())
        assertFalse(buffer.isEmpty())
    }
    
    @Test
    fun `buffer wraps around when full`() {
        // Fill buffer beyond capacity
        for (i in 1..15) {
            buffer.append(createSnapshot(i.toLong()))
        }
        
        assertEquals(10, buffer.getSize())
        assertEquals(5L, buffer.getOverflowCount())
    }
    
    @Test
    fun `getRecent returns most recent events`() {
        for (i in 1..10) {
            buffer.append(createSnapshot(i.toLong()))
        }
        
        val recent = buffer.getRecent(3)
        assertEquals(3, recent.size)
        assertEquals(10L, (recent.last() as TimelineEvent.Snapshot).sequenceNumber)
    }
    
    @Test
    fun `getRange filters by sequence number`() {
        for (i in 1..10) {
            buffer.append(createSnapshot(i.toLong()))
        }
        
        val range = buffer.getRange(3, 7)
        assertEquals(5, range.size)
    }
    
    @Test
    fun `clear resets buffer state`() {
        buffer.append(createSnapshot(1))
        buffer.clear()
        
        assertTrue(buffer.isEmpty())
        assertEquals(0, buffer.getSize())
        assertEquals(0L, buffer.getOverflowCount())
    }
    
    private fun createSnapshot(sequence: Long): TimelineEvent.Snapshot {
        return TimelineEvent.Snapshot.create(
            timestamp = System.currentTimeMillis(),
            sequenceNumber = sequence,
            threadName = "main",
            sourceId = "test",
            determinismClass = DeterminismClass.CLASS_A_GUARANTEED,
            valueType = "String",
            valueBytes = "test".toByteArray(),
            checkpointHash = null
        )
    }
}
