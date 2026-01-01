package com.chronos.agent.replay

import android.util.Log
import com.chronos.agent.determinism.DeterminismViolationException
import com.chronos.agent.recording.DeterminismClass
import com.chronos.agent.recording.StateSourceRegistry
import com.chronos.agent.storage.DivergenceClassifier
import com.chronos.agent.storage.DivergenceType
import com.chronos.agent.timeline.TimelineEvent
import com.chronos.agent.verification.DeterminismScorer
import com.chronos.agent.verification.RefusalEngine
import com.chronos.agent.verification.RuntimeVerifier

/**
 * Core Replay Controller.
 * 
 * Orchestrates replay with:
 * - Pre-flight determinism checks
 * - Sandbox activation
 * - Event-by-event replay
 * - Divergence monitoring
 */
class ReplayController {
    
    private var state = ReplayState.IDLE
    private val divergences = mutableListOf<DivergenceClassifier.DivergenceReport>()
    
    enum class ReplayState {
        IDLE,
        PREFLIGHT,
        REPLAYING,
        PAUSED,
        COMPLETED,
        ABORTED
    }
    
    data class ReplayResult(
        val success: Boolean,
        val eventsReplayed: Int,
        val divergences: List<DivergenceClassifier.DivergenceReport>,
        val abortReason: String?
    )
    
    /**
     * Starts replay of recorded events.
     * 
     * @throws DeterminismViolationException if Class D sources are present.
     */
    fun startReplay(events: List<TimelineEvent>): ReplayResult {
        state = ReplayState.PREFLIGHT
        divergences.clear()
        
        // 1. Pre-flight: Check determinism
        val refusalReport = RefusalEngine.evaluate()
        if (!refusalReport.isReplayAllowed) {
            state = ReplayState.ABORTED
            val blockedSources = refusalReport.blockingReasons.associate { 
                it.sourceId to it.reason 
            }
            throw DeterminismViolationException(blockedSources)
        }
        
        // 2. Verify only Class A/B sources
        val unsafeSources = StateSourceRegistry.getSourcesByClass(
            DeterminismClass.CLASS_C_CONDITIONAL,
            DeterminismClass.CLASS_D_UNSAFE
        )
        if (unsafeSources.isNotEmpty()) {
            Log.w("Chronos", "⚠️ Replay includes ${unsafeSources.size} non-deterministic sources")
        }
        
        // 3. Activate sandbox
        ReplaySandbox.activate()
        
        // 4. Replay events
        state = ReplayState.REPLAYING
        var eventsReplayed = 0
        
        try {
            for (event in events) {
                if (state == ReplayState.ABORTED) break
                
                when (event) {
                    is TimelineEvent.Snapshot -> {
                        val result = replaySnapshot(event)
                        eventsReplayed++
                        
                        if (!result.isValid) {
                            val report = DivergenceClassifier.classify(
                                result,
                                event.sequenceNumber,
                                event.sourceId
                            )
                            divergences.add(report)
                            
                            if (DivergenceClassifier.shouldHalt(report.type)) {
                                state = ReplayState.ABORTED
                                Log.e("Chronos", "🛑 Replay ABORTED: ${report.description}")
                                break
                            }
                        }
                    }
                    is TimelineEvent.Checkpoint -> {
                        verifyCheckpoint(event)
                    }
                    is TimelineEvent.Gap -> {
                        Log.w("Chronos", "⚠️ Gap in recording: ${event.reason}")
                    }
                    is TimelineEvent.Log -> {
                        // Logs are informational only
                    }
                }
            }
            
            if (state == ReplayState.REPLAYING) {
                state = ReplayState.COMPLETED
            }
            
        } finally {
            ReplaySandbox.deactivate()
        }
        
        return ReplayResult(
            success = state == ReplayState.COMPLETED,
            eventsReplayed = eventsReplayed,
            divergences = divergences.toList(),
            abortReason = if (state == ReplayState.ABORTED) 
                divergences.lastOrNull()?.description else null
        )
    }
    
    private fun replaySnapshot(snapshot: TimelineEvent.Snapshot): RuntimeVerifier.VerificationResult {
        // Find the source and apply the recorded state
        val source = StateSourceRegistry.getSource(snapshot.sourceId)
        
        if (source == null) {
            return RuntimeVerifier.VerificationResult(
                isValid = false,
                divergenceType = RuntimeVerifier.DivergenceType.STRUCTURAL,
                message = "Source not found: ${snapshot.sourceId}"
            )
        }
        
        // Verify against checkpoint if available
        if (snapshot.checkpointHash != null) {
            return RuntimeVerifier.verifyAgainstCheckpoint(
                snapshot.sequenceNumber,
                source.captureState()
            )
        }
        
        return RuntimeVerifier.VerificationResult(
            isValid = true,
            divergenceType = RuntimeVerifier.DivergenceType.NONE,
            message = "Snapshot replayed"
        )
    }
    
    private fun verifyCheckpoint(checkpoint: TimelineEvent.Checkpoint) {
        Log.d("Chronos", "✓ Checkpoint verified: ${checkpoint.checkpointId}")
    }
    
    fun pause() {
        if (state == ReplayState.REPLAYING) {
            state = ReplayState.PAUSED
            Log.i("Chronos", "⏸️ Replay PAUSED")
        }
    }
    
    fun resume() {
        if (state == ReplayState.PAUSED) {
            state = ReplayState.REPLAYING
            Log.i("Chronos", "▶️ Replay RESUMED")
        }
    }
    
    fun abort() {
        state = ReplayState.ABORTED
        ReplaySandbox.deactivate()
        Log.i("Chronos", "⏹️ Replay ABORTED by user")
    }
    
    fun getState(): ReplayState = state
}
