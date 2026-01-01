package com.chronos.agent.verification

import android.util.Log
import com.chronos.agent.recording.DeterminismClass
import com.chronos.agent.recording.StateSource
import com.chronos.agent.recording.StateSourceRegistry

/**
 * The Refusal Engine - Core implementation of Chronos' "Refuse early, explain always" philosophy.
 * 
 * When replay is blocked, this engine:
 * 1. Identifies the blocking sources
 * 2. Generates human-readable explanations
 * 3. Suggests mitigations
 */
object RefusalEngine {
    
    data class RefusalReport(
        val isReplayAllowed: Boolean,
        val score: DeterminismScorer.Score,
        val blockingReasons: List<BlockingReason>,
        val mitigations: List<Mitigation>
    )
    
    data class BlockingReason(
        val sourceId: String,
        val classification: DeterminismClass,
        val reason: String,
        val risks: List<StaticAnalyzer.Risk>
    )
    
    data class Mitigation(
        val action: String,
        val description: String,
        val effort: Effort
    )
    
    enum class Effort {
        LOW,    // Simple annotation or config change
        MEDIUM, // Code refactoring needed
        HIGH    // Architectural change required
    }
    
    /**
     * Evaluates whether replay is allowed and generates a full report.
     */
    fun evaluate(): RefusalReport {
        val sources = StateSourceRegistry.getAllSources()
        val score = DeterminismScorer.scoreSession()
        val blockingReasons = mutableListOf<BlockingReason>()
        val mitigations = mutableListOf<Mitigation>()
        
        // Collect all blocking reasons
        sources.forEach { source ->
            if (source.getDeterminismClass() == DeterminismClass.CLASS_D_UNSAFE) {
                val analysis = StaticAnalyzer.analyze(source)
                blockingReasons.add(BlockingReason(
                    sourceId = source.sourceId,
                    classification = DeterminismClass.CLASS_D_UNSAFE,
                    reason = "Contains non-deterministic dependencies",
                    risks = analysis.risks
                ))
            }
        }
        
        // Generate mitigations
        if (blockingReasons.isNotEmpty()) {
            mitigations.addAll(generateMitigations(blockingReasons))
        }
        
        // Log refusal if applicable
        if (!score.replayEligible) {
            logRefusal(score, blockingReasons, mitigations)
        }
        
        return RefusalReport(
            isReplayAllowed = score.replayEligible,
            score = score,
            blockingReasons = blockingReasons,
            mitigations = mitigations
        )
    }
    
    private fun generateMitigations(reasons: List<BlockingReason>): List<Mitigation> {
        val mitigations = mutableListOf<Mitigation>()
        
        reasons.forEach { reason ->
            reason.risks.forEach { risk ->
                when {
                    risk.description.contains("random", ignoreCase = true) -> {
                        mitigations.add(Mitigation(
                            action = "Inject random seed",
                            description = "Inject a fixed Random seed during replay for ${reason.sourceId}",
                            effort = Effort.MEDIUM
                        ))
                    }
                    risk.description.contains("time", ignoreCase = true) -> {
                        mitigations.add(Mitigation(
                            action = "Use TimeProvider",
                            description = "Replace direct time access with injectable TimeProvider in ${reason.sourceId}",
                            effort = Effort.MEDIUM
                        ))
                    }
                    risk.description.contains("Network", ignoreCase = true) -> {
                        mitigations.add(Mitigation(
                            action = "Exclude network source",
                            description = "Unregister ${reason.sourceId} before replay and use cached data",
                            effort = Effort.LOW
                        ))
                    }
                    risk.description.contains("Database", ignoreCase = true) -> {
                        mitigations.add(Mitigation(
                            action = "Use in-memory DB",
                            description = "Switch to in-memory database for replay in ${reason.sourceId}",
                            effort = Effort.HIGH
                        ))
                    }
                }
            }
        }
        
        // Add general mitigation
        if (mitigations.isEmpty() && reasons.isNotEmpty()) {
            mitigations.add(Mitigation(
                action = "Use Snapshot Mode",
                description = "Use Snapshot inspection instead of replay to avoid determinism requirements",
                effort = Effort.LOW
            ))
        }
        
        return mitigations.distinctBy { it.action }
    }
    
    private fun logRefusal(
        score: DeterminismScorer.Score,
        reasons: List<BlockingReason>,
        mitigations: List<Mitigation>
    ) {
        val reasonsText = reasons.joinToString("\n") { reason ->
            "  • ${reason.sourceId}: ${reason.reason}"
        }
        
        val mitigationsText = mitigations.joinToString("\n") { mitigation ->
            "  → [${mitigation.effort}] ${mitigation.action}: ${mitigation.description}"
        }
        
        // SECURITY: Log generic message, don't expose source IDs in logs
        Log.e("Chronos", "Replay refused. Score: ${score.value}/100. " +
            "Use Chronos.getRefusalReport() for details.")
        
        // Detailed output only for debug builds with verbose logging enabled
        if (Log.isLoggable("Chronos", Log.DEBUG)) {
            Log.d("Chronos", """
                Blocking sources: ${reasons.size}
                Suggested mitigations: ${mitigations.size}
                Score: ${score.value}/100 (${score.level})
            """.trimIndent())
        }
    }
    
    /**
     * Generates a user-friendly explanation string for UI display.
     */
    fun getExplanation(report: RefusalReport): String {
        if (report.isReplayAllowed) {
            return "✅ Replay is available. Determinism score: ${report.score.value}/100"
        }
        
        val reasons = report.blockingReasons.joinToString("\n") { 
            "• ${it.sourceId}: ${it.reason}" 
        }
        
        val mitigations = report.mitigations.take(3).joinToString("\n") { 
            "→ ${it.action}" 
        }
        
        return """
            🚫 Replay Blocked
            
            Score: ${report.score.value}/100
            
            Blocking Sources:
            $reasons
            
            Try:
            $mitigations
        """.trimIndent()
    }
}
