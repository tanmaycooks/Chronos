package com.chronos.agent.verification

import com.chronos.agent.recording.DeterminismClass
import com.chronos.agent.recording.StateSource
import com.chronos.agent.recording.StateSourceRegistry
import java.lang.reflect.ParameterizedType

/**
 * Calculates a determinism score for a state source or the entire session.
 * 
 * Score ranges:
 * - 100: Perfect determinism (Class A only)
 * - 80-99: High confidence (Class A + verified Class B)
 * - 50-79: Conditional (includes Class C with acknowledgment)
 * - 0-49: Unsafe (Class D present)
 */
object DeterminismScorer {
    
    data class Score(
        val value: Int,
        val level: Level,
        val replayEligible: Boolean,
        val breakdown: Breakdown
    )
    
    data class Breakdown(
        val classACount: Int,
        val classBCount: Int,
        val classCCount: Int,
        val classDCount: Int,
        val staticAnalysisScore: Int,
        val contractScore: Int
    )
    
    enum class Level {
        PERFECT,        // 100
        HIGH,           // 80-99
        CONDITIONAL,    // 50-79
        UNSAFE          // 0-49
    }
    
    /**
     * Gets the actual state type from a StateSource via reflection.
     * This avoids calling captureState() which may have side effects.
     */
    private fun getStateType(source: StateSource<*>): Class<*>? {
        return try {
            // Try to get the generic type parameter from the StateSource interface
            val sourceClass = source::class.java
            
            // Check all interfaces for StateSource<T>
            for (iface in sourceClass.genericInterfaces) {
                if (iface is ParameterizedType && 
                    iface.rawType == StateSource::class.java) {
                    val typeArg = iface.actualTypeArguments.firstOrNull()
                    if (typeArg is Class<*>) {
                        return typeArg
                    }
                }
            }
            
            // Check superclass chain
            var current: Class<*>? = sourceClass.superclass
            while (current != null) {
                val superType = current.genericSuperclass
                if (superType is ParameterizedType) {
                    val typeArg = superType.actualTypeArguments.firstOrNull()
                    if (typeArg is Class<*>) {
                        return typeArg
                    }
                }
                current = current.superclass
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculates the determinism score for a single source.
     */
    fun scoreSource(source: StateSource<*>): Score {
        val staticResult = StaticAnalyzer.analyze(source)
        
        // Get the actual state type via reflection (no side effects)
        val stateType = getStateType(source)
        val contractResult = if (stateType != null) {
            DeveloperContract.checkAnnotations(stateType)
        } else {
            DeveloperContract.ContractResult(null, DeveloperContract.ContractSource.NONE, null)
        }
        
        // Calculate base score
        var baseScore = when (source.getDeterminismClass()) {
            DeterminismClass.CLASS_A_GUARANTEED -> 100
            DeterminismClass.CLASS_B_VERIFIABLE -> 85
            DeterminismClass.CLASS_C_CONDITIONAL -> 60
            DeterminismClass.CLASS_D_UNSAFE -> 0
        }
        
        // Adjust based on static analysis
        baseScore = minOf(baseScore, staticResult.score)
        
        // Boost if developer has explicit contract
        if (contractResult.classification == DeterminismClass.CLASS_A_GUARANTEED) {
            baseScore = minOf(100, baseScore + 10)
        }
        
        val level = when {
            baseScore == 100 -> Level.PERFECT
            baseScore >= 80 -> Level.HIGH
            baseScore >= 50 -> Level.CONDITIONAL
            else -> Level.UNSAFE
        }
        
        return Score(
            value = baseScore,
            level = level,
            replayEligible = baseScore >= 80,
            breakdown = Breakdown(
                classACount = if (source.getDeterminismClass() == DeterminismClass.CLASS_A_GUARANTEED) 1 else 0,
                classBCount = if (source.getDeterminismClass() == DeterminismClass.CLASS_B_VERIFIABLE) 1 else 0,
                classCCount = if (source.getDeterminismClass() == DeterminismClass.CLASS_C_CONDITIONAL) 1 else 0,
                classDCount = if (source.getDeterminismClass() == DeterminismClass.CLASS_D_UNSAFE) 1 else 0,
                staticAnalysisScore = staticResult.score,
                contractScore = if (contractResult.source != DeveloperContract.ContractSource.NONE) 10 else 0
            )
        )
    }
    
    /**
     * Calculates the overall session score based on all registered sources.
     */
    fun scoreSession(): Score {
        val sources = StateSourceRegistry.getAllSources()
        
        if (sources.isEmpty()) {
            return Score(100, Level.PERFECT, true, Breakdown(0, 0, 0, 0, 100, 0))
        }
        
        var classACount = 0
        var classBCount = 0
        var classCCount = 0
        var classDCount = 0
        var totalStaticScore = 0
        var totalContractScore = 0
        
        sources.forEach { source ->
            when (source.getDeterminismClass()) {
                DeterminismClass.CLASS_A_GUARANTEED -> classACount++
                DeterminismClass.CLASS_B_VERIFIABLE -> classBCount++
                DeterminismClass.CLASS_C_CONDITIONAL -> classCCount++
                DeterminismClass.CLASS_D_UNSAFE -> classDCount++
            }
            
            val staticResult = StaticAnalyzer.analyze(source)
            totalStaticScore += staticResult.score
            
            // Get the actual state type via reflection (no side effects)
            val stateType = getStateType(source)
            val contractResult = if (stateType != null) {
                DeveloperContract.checkAnnotations(stateType)
            } else {
                DeveloperContract.ContractResult(null, DeveloperContract.ContractSource.NONE, null)
            }
            if (contractResult.source != DeveloperContract.ContractSource.NONE) {
                totalContractScore += 10
            }
        }
        
        // Class D presence immediately drops score to unsafe
        val sessionScore = if (classDCount > 0) {
            0
        } else {
            val avgStaticScore = totalStaticScore / sources.size
            val classWeight = (classACount * 100 + classBCount * 85 + classCCount * 60) / sources.size
            (avgStaticScore + classWeight) / 2
        }
        
        val level = when {
            sessionScore == 100 -> Level.PERFECT
            sessionScore >= 80 -> Level.HIGH
            sessionScore >= 50 -> Level.CONDITIONAL
            else -> Level.UNSAFE
        }
        
        return Score(
            value = sessionScore,
            level = level,
            replayEligible = classDCount == 0 && sessionScore >= 80,
            breakdown = Breakdown(
                classACount = classACount,
                classBCount = classBCount,
                classCCount = classCCount,
                classDCount = classDCount,
                staticAnalysisScore = if (sources.isNotEmpty()) totalStaticScore / sources.size else 100,
                contractScore = totalContractScore
            )
        )
    }
}
