package com.chronos.agent

import com.chronos.agent.replay.ReplaySandbox
import com.chronos.agent.replay.ReplayController
import com.chronos.agent.recording.DeterminismClass
import com.chronos.agent.recording.StateSource
import com.chronos.agent.recording.StateSourceRegistry
import com.chronos.agent.timeline.TimelineEvent
import com.chronos.agent.determinism.DeterminismViolationException
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

/**
 * Tests for replay correctness and sandbox isolation.
 */
class ReplayTest {
    
    private lateinit var controller: ReplayController
    
    @Before
    fun setup() {
        controller = ReplayController()
        StateSourceRegistry.clear()
        ReplaySandbox.deactivate()
    }
    
    @After
    fun teardown() {
        StateSourceRegistry.clear()
        ReplaySandbox.deactivate()
    }
    
    // ============ Sandbox Tests ============
    
    @Test
    fun `sandbox blocks network requests when active`() {
        ReplaySandbox.activate()
        
        val blocked = ReplaySandbox.NetworkGuard.checkRequest("https://api.example.com")
        
        assertTrue(blocked)
        assertEquals(1, ReplaySandbox.getBlockedOperations().size)
    }
    
    @Test
    fun `sandbox blocks database writes when active`() {
        ReplaySandbox.activate()
        
        val blocked = ReplaySandbox.DatabaseGuard.checkWrite("users")
        
        assertTrue(blocked)
    }
    
    @Test
    fun `sandbox allows database reads when active`() {
        ReplaySandbox.activate()
        
        val blocked = ReplaySandbox.DatabaseGuard.checkRead("users")
        
        assertFalse(blocked)
    }
    
    @Test
    fun `sandbox does not block when inactive`() {
        val blocked = ReplaySandbox.NetworkGuard.checkRequest("https://api.example.com")
        
        assertFalse(blocked)
    }
    
    // ============ Replay Gate Tests ============
    
    @Test(expected = DeterminismViolationException::class)
    fun `replay throws exception when Class D sources present`() {
        // Register a Class D source
        StateSourceRegistry.register(object : StateSource<Any> {
            override val sourceId = "unsafe_source"
            override fun captureState() = Any()
            override fun getDeterminismClass() = DeterminismClass.CLASS_D_UNSAFE
        })
        
        controller.startReplay(emptyList())
    }
    
    @Test
    fun `replay succeeds with only Class A sources`() {
        // Register only Class A sources
        StateSourceRegistry.register(object : StateSource<String> {
            override val sourceId = "safe_source"
            override fun captureState() = "safe"
            override fun getDeterminismClass() = DeterminismClass.CLASS_A_GUARANTEED
        })
        
        val events = listOf(
            TimelineEvent.Snapshot(
                timestamp = System.currentTimeMillis(),
                sequenceNumber = 1,
                threadName = "main",
                sourceId = "safe_source",
                determinismClass = DeterminismClass.CLASS_A_GUARANTEED,
                valueType = "String",
                valueBytes = "safe".toByteArray(),
                checkpointHash = null
            )
        )
        
        val result = controller.startReplay(events)
        
        assertTrue(result.success)
        assertEquals(1, result.eventsReplayed)
    }
}
