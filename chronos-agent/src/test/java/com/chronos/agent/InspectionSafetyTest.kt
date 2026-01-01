package com.chronos.agent

import com.chronos.agent.inspection.InspectionMode
import com.chronos.agent.inspection.safety.SafeObjectReader
import com.chronos.agent.inspection.safety.ReadSafetyAnalyzer
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for inspection safety - ensuring no side effects.
 */
class InspectionSafetyTest {
    
    // ============ Safe Object Reader Tests ============
    
    @Test
    fun `SafeObjectReader reads field values correctly`() {
        data class TestObject(val name: String, val value: Int)
        val obj = TestObject("test", 42)
        
        val name = SafeObjectReader.readField(obj, "name")
        val value = SafeObjectReader.readField(obj, "value")
        
        assertEquals("test", name)
        assertEquals(42, value)
    }
    
    @Test
    fun `SafeObjectReader returns MISSING for nonexistent fields`() {
        data class TestObject(val name: String)
        val obj = TestObject("test")
        
        val result = SafeObjectReader.readField(obj, "nonexistent")
        
        assertEquals("<MISSING>", result)
    }
    
    @Test
    fun `SafeObjectReader blocks Thread internals`() {
        val thread = Thread.currentThread()
        
        val result = SafeObjectReader.readField(thread, "name")
        
        assertEquals("<BLOCKED: SYSTEM FIELD>", result)
    }
    
    // ============ Read Safety Analyzer Tests ============
    
    @Test
    fun `analyzer flags synchronized getters`() {
        class SyncGetter {
            @Synchronized
            fun getValue(): Int = 42
        }
        
        val report = ReadSafetyAnalyzer.analyze(SyncGetter::class.java)
        
        assertFalse(report.isSafe)
        assertTrue(report.risks.any { it.contains("synchronized") })
    }
    
    @Test
    fun `analyzer flags Lazy fields`() {
        class LazyHolder {
            val lazyValue: Lazy<String> = lazy { "computed" }
        }
        
        val report = ReadSafetyAnalyzer.analyze(LazyHolder::class.java)
        
        assertFalse(report.isSafe)
        assertTrue(report.risks.any { it.contains("lazy", ignoreCase = true) })
    }
    
    @Test
    fun `analyzer passes for simple data class`() {
        data class SimpleData(val id: Int, val name: String)
        
        val report = ReadSafetyAnalyzer.analyze(SimpleData::class.java)
        
        assertTrue(report.isSafe)
        assertTrue(report.risks.isEmpty())
    }
    
    // ============ Side Effect Tests ============
    
    @Test
    fun `reading field does not modify object state`() {
        class Counter {
            var readCount = 0
            val value: Int
                get() {
                    readCount++  // This would be a side effect if called
                    return 42
                }
        }
        
        val counter = Counter()
        
        // Using SafeObjectReader should NOT increment readCount
        // because it accesses the backing field directly
        SafeObjectReader.readField(counter, "value")
        
        // readCount should still be 0 because we didn't call the getter
        assertEquals(0, counter.readCount)
    }
}
