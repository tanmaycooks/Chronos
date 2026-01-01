package com.chronos.agent.monitoring

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitors system memory pressure and adjusts recording behavior.
 * 
 * When memory is low, Chronos will:
 * - Pause recording to reduce memory usage
 * - Resume when memory pressure decreases
 * - Log metrics about memory-related pauses
 */
object MemoryPressureMonitor : ComponentCallbacks2 {
    
    private val isInitialized = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    
    // Thresholds (as percentage of available memory)
    private const val PAUSE_THRESHOLD = 0.15f  // Pause when <15% memory available
    private const val RESUME_THRESHOLD = 0.25f // Resume when >25% memory available
    
    // Metrics
    private val pauseCount = AtomicLong(0)
    private val totalPauseDurationMs = AtomicLong(0)
    private var lastPauseStartTime: Long = 0L
    
    // Listeners
    private val listeners = mutableListOf<MemoryPressureListener>()
    
    interface MemoryPressureListener {
        fun onRecordingPaused(reason: String)
        fun onRecordingResumed()
    }
    
    /**
     * Initializes the memory pressure monitor.
     * Should be called from Application.onCreate().
     */
    fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return
        
        context.applicationContext.registerComponentCallbacks(this)
        Log.i("Chronos", "Memory pressure monitor initialized")
    }
    
    /**
     * Checks current memory state and decides if recording should be paused.
     */
    fun checkMemoryState(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val availableRatio = memInfo.availMem.toFloat() / memInfo.totalMem.toFloat()
        
        if (!isPaused.get() && availableRatio < PAUSE_THRESHOLD) {
            pauseRecording("Low memory: ${(availableRatio * 100).toInt()}% available")
            return true
        } else if (isPaused.get() && availableRatio > RESUME_THRESHOLD) {
            resumeRecording()
        }
        
        return isPaused.get()
    }
    
    private fun pauseRecording(reason: String) {
        if (isPaused.getAndSet(true)) return
        
        lastPauseStartTime = System.currentTimeMillis()
        pauseCount.incrementAndGet()
        
        Log.w("Chronos", "⏸️ Recording PAUSED: $reason")
        RecordingMetrics.recordMemoryPause()
        
        synchronized(listeners) {
            listeners.forEach { it.onRecordingPaused(reason) }
        }
    }
    
    private fun resumeRecording() {
        if (!isPaused.getAndSet(false)) return
        
        val pauseDuration = System.currentTimeMillis() - lastPauseStartTime
        totalPauseDurationMs.addAndGet(pauseDuration)
        
        Log.i("Chronos", "▶️ Recording RESUMED after ${pauseDuration}ms pause")
        
        synchronized(listeners) {
            listeners.forEach { it.onRecordingResumed() }
        }
    }
    
    /**
     * Returns true if recording is currently paused due to memory pressure.
     */
    fun isPaused(): Boolean = isPaused.get()
    
    fun addListener(listener: MemoryPressureListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: MemoryPressureListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }
    
    // ComponentCallbacks2 implementation
    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                pauseRecording("System memory trim: level $level")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.d("Chronos", "Memory pressure detected: level $level")
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // App went to background - could reduce recording fidelity
                Log.d("Chronos", "App went to background")
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        // Not used
    }
    
    override fun onLowMemory() {
        pauseRecording("System low memory callback")
    }
    
    /**
     * Returns memory pressure statistics.
     */
    fun getStats(): MemoryStats {
        return MemoryStats(
            pauseCount = pauseCount.get(),
            totalPauseDurationMs = totalPauseDurationMs.get(),
            currentlyPaused = isPaused.get()
        )
    }
    
    data class MemoryStats(
        val pauseCount: Long,
        val totalPauseDurationMs: Long,
        val currentlyPaused: Boolean
    )
}

/**
 * Recording metrics and performance monitoring.
 * 
 * Tracks:
 * - Event counts by type
 * - Recording errors
 * - Memory pauses
 * - Serialization times
 */
object RecordingMetrics {
    
    // Event counters
    private val snapshotCount = AtomicLong(0)
    private val gapCount = AtomicLong(0)
    private val checkpointCount = AtomicLong(0)
    
    // Error counters
    private val serializationErrors = AtomicLong(0)
    private val captureErrors = AtomicLong(0)
    private val ipcErrors = AtomicLong(0)
    
    // Memory-related
    private val memoryPauses = AtomicLong(0)
    
    // Performance tracking
    private val totalSerializationTimeNs = AtomicLong(0)
    private val totalCaptureTimeNs = AtomicLong(0)
    
    // Session info
    private var sessionStartTime: Long = 0L
    
    /**
     * Starts a new metrics session.
     */
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        resetCounters()
        Log.i("Chronos", "Metrics session started")
    }
    
    private fun resetCounters() {
        snapshotCount.set(0)
        gapCount.set(0)
        checkpointCount.set(0)
        serializationErrors.set(0)
        captureErrors.set(0)
        ipcErrors.set(0)
        memoryPauses.set(0)
        totalSerializationTimeNs.set(0)
        totalCaptureTimeNs.set(0)
    }
    
    // Event recording
    fun recordSnapshot() = snapshotCount.incrementAndGet()
    fun recordGap() = gapCount.incrementAndGet()
    fun recordCheckpoint() = checkpointCount.incrementAndGet()
    
    // Error recording
    fun recordSerializationError() = serializationErrors.incrementAndGet()
    fun recordCaptureError() = captureErrors.incrementAndGet()
    fun recordIpcError() = ipcErrors.incrementAndGet()
    
    // Memory
    fun recordMemoryPause() = memoryPauses.incrementAndGet()
    
    // Performance timing
    fun addSerializationTime(nanos: Long) = totalSerializationTimeNs.addAndGet(nanos)
    fun addCaptureTime(nanos: Long) = totalCaptureTimeNs.addAndGet(nanos)
    
    /**
     * Measures and records the time taken by a block.
     */
    inline fun <T> measureCapture(block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            addCaptureTime(System.nanoTime() - start)
        }
    }
    
    inline fun <T> measureSerialization(block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            addSerializationTime(System.nanoTime() - start)
        }
    }
    
    /**
     * Returns a summary of all metrics.
     */
    fun getSummary(): MetricsSummary {
        val sessionDurationMs = if (sessionStartTime > 0) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            0L
        }
        
        val snapshots = snapshotCount.get()
        val avgCaptureNs = if (snapshots > 0) totalCaptureTimeNs.get() / snapshots else 0
        val avgSerializationNs = if (snapshots > 0) totalSerializationTimeNs.get() / snapshots else 0
        
        return MetricsSummary(
            sessionDurationMs = sessionDurationMs,
            snapshotCount = snapshots,
            gapCount = gapCount.get(),
            checkpointCount = checkpointCount.get(),
            eventsPerSecond = if (sessionDurationMs > 0) {
                (snapshots * 1000.0 / sessionDurationMs)
            } else 0.0,
            serializationErrors = serializationErrors.get(),
            captureErrors = captureErrors.get(),
            ipcErrors = ipcErrors.get(),
            totalErrors = serializationErrors.get() + captureErrors.get() + ipcErrors.get(),
            errorRate = if (snapshots > 0) {
                (serializationErrors.get() + captureErrors.get()).toDouble() / snapshots
            } else 0.0,
            memoryPauses = memoryPauses.get(),
            avgCaptureTimeUs = avgCaptureNs / 1000,
            avgSerializationTimeUs = avgSerializationNs / 1000
        )
    }
    
    /**
     * Logs the current metrics summary.
     */
    fun logSummary() {
        val summary = getSummary()
        Log.i("Chronos", """
            📊 Recording Metrics Summary:
            ├─ Duration: ${summary.sessionDurationMs}ms
            ├─ Snapshots: ${summary.snapshotCount} (${String.format("%.1f", summary.eventsPerSecond)}/s)
            ├─ Gaps: ${summary.gapCount}
            ├─ Checkpoints: ${summary.checkpointCount}
            ├─ Errors: ${summary.totalErrors} (${String.format("%.2f", summary.errorRate * 100)}%)
            ├─ Memory Pauses: ${summary.memoryPauses}
            └─ Avg Capture Time: ${summary.avgCaptureTimeUs}μs
        """.trimIndent())
    }
    
    data class MetricsSummary(
        val sessionDurationMs: Long,
        val snapshotCount: Long,
        val gapCount: Long,
        val checkpointCount: Long,
        val eventsPerSecond: Double,
        val serializationErrors: Long,
        val captureErrors: Long,
        val ipcErrors: Long,
        val totalErrors: Long,
        val errorRate: Double,
        val memoryPauses: Long,
        val avgCaptureTimeUs: Long,
        val avgSerializationTimeUs: Long
    )
}
