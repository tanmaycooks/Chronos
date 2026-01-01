package com.chronos.agent

import com.chronos.agent.determinism.DeterminismClassifier
import com.chronos.agent.recording.DeterminismClass
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DeterminismClassifier.
 */
class DeterminismClassifierTest {
    
    // ============ Class A Tests ============
    
    @Test
    fun `primitives are classified as Class A`() {
        assertEquals(DeterminismClass.CLASS_A_GUARANTEED, DeterminismClassifier.classify(Int::class))
        assertEquals(DeterminismClass.CLASS_A_GUARANTEED, DeterminismClassifier.classify(String::class))
        assertEquals(DeterminismClass.CLASS_A_GUARANTEED, DeterminismClassifier.classify(Boolean::class))
    }
    
    @Test
    fun `data classes are classified as Class A`() {
        data class UserState(val id: Int, val name: String)
        assertEquals(DeterminismClass.CLASS_A_GUARANTEED, DeterminismClassifier.classify(UserState::class))
    }
    
    @Test
    fun `sealed classes are classified as Class A`() {
        sealed class UiState {
            object Loading : UiState()
            data class Success(val data: String) : UiState()
        }
        assertEquals(DeterminismClass.CLASS_A_GUARANTEED, DeterminismClassifier.classify(UiState::class))
    }
    
    // ============ Class D Tests ============
    
    @Test
    fun `Random is classified as Class D`() {
        assertEquals(DeterminismClass.CLASS_D_UNSAFE, DeterminismClassifier.classify(java.util.Random::class))
    }
    
    @Test
    fun `Date is classified as Class D`() {
        assertEquals(DeterminismClass.CLASS_D_UNSAFE, DeterminismClassifier.classify(java.util.Date::class))
    }
    
    @Test
    fun `File is classified as Class D`() {
        assertEquals(DeterminismClass.CLASS_D_UNSAFE, DeterminismClassifier.classify(java.io.File::class))
    }
    
    // ============ Replay Safety Tests ============
    
    @Test
    fun `Class A is replay safe`() {
        assertTrue(DeterminismClassifier.isReplaySafe(String::class))
    }
    
    @Test
    fun `Class D is not replay safe`() {
        assertFalse(DeterminismClassifier.isReplaySafe(java.util.Random::class))
    }
}
