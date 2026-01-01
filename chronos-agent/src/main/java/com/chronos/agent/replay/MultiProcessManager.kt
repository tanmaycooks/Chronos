package com.chronos.agent.replay

import android.os.Process
import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Handles multi-process replay scenarios with authentication.
 * 
 * Android apps can have multiple processes (e.g., :remote, :service).
 * This manager coordinates replay across process boundaries.
 * 
 * SECURITY: Uses HMAC-based authentication to prevent PID spoofing.
 */
object MultiProcessManager {
    
    private val processStates = ConcurrentHashMap<Int, ProcessState>()
    private var coordinatorPid: Int = -1
    
    // Shared secret for this app instance (generated once at startup)
    private val sharedSecret: ByteArray by lazy {
        ByteArray(32).also { SecureRandom().nextBytes(it) }
    }
    
    // Process authentication tokens
    private val processTokens = ConcurrentHashMap<Int, ByteArray>()
    
    data class ProcessState(
        val pid: Int,
        val processName: String,
        val isReplaying: Boolean,
        val lastSequenceNumber: Long,
        val authToken: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ProcessState) return false
            return pid == other.pid && processName == other.processName
        }
        
        override fun hashCode(): Int = 31 * pid + processName.hashCode()
    }
    
    /**
     * Generates an authentication token for a process.
     * Uses HMAC-SHA256 with the shared secret.
     */
    private fun generateAuthToken(pid: Int, processName: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
        val message = "$pid:$processName:${System.currentTimeMillis() / 60000}" // 1-minute granularity
        return mac.doFinal(message.toByteArray())
    }
    
    /**
     * Verifies an authentication token for a process.
     */
    private fun verifyAuthToken(pid: Int, token: ByteArray): Boolean {
        val storedToken = processTokens[pid] ?: return false
        return storedToken.contentEquals(token)
    }
    
    /**
     * Registers this process for replay coordination with authentication.
     */
    fun registerProcess(processName: String): ByteArray {
        val pid = Process.myPid()
        val authToken = generateAuthToken(pid, processName)
        
        processStates[pid] = ProcessState(
            pid = pid,
            processName = processName,
            isReplaying = false,
            lastSequenceNumber = 0,
            authToken = authToken
        )
        processTokens[pid] = authToken
        
        Log.i("Chronos", "Process registered with authentication: $processName (PID: $pid)")
        return authToken
    }
    
    /**
     * Sets this process as the replay coordinator.
     * Returns the coordinator token for verification.
     */
    fun becomeCoordinator(): ByteArray? {
        val pid = Process.myPid()
        val state = processStates[pid] ?: run {
            Log.w("Chronos", "Cannot become coordinator: process not registered")
            return null
        }
        
        coordinatorPid = pid
        Log.i("Chronos", "Process $pid is now replay coordinator")
        return state.authToken
    }
    
    /**
     * Checks if this process is the coordinator.
     */
    fun isCoordinator(): Boolean = coordinatorPid == Process.myPid()
    
    /**
     * Updates the replay state for this process with token verification.
     */
    fun updateReplayState(isReplaying: Boolean, sequenceNumber: Long, authToken: ByteArray): Boolean {
        val pid = Process.myPid()
        
        // Verify authentication
        if (!verifyAuthToken(pid, authToken)) {
            Log.w("Chronos", "Replay state update rejected: invalid auth token for PID $pid")
            return false
        }
        
        processStates[pid]?.let { state ->
            processStates[pid] = state.copy(
                isReplaying = isReplaying,
                lastSequenceNumber = sequenceNumber
            )
            return true
        }
        return false
    }
    
    /**
     * Checks if all registered processes are synchronized.
     */
    fun areProcessesSynchronized(): Boolean {
        val states = processStates.values.toList()
        if (states.size <= 1) return true
        
        val minSeq = states.minOfOrNull { it.lastSequenceNumber } ?: 0
        val maxSeq = states.maxOfOrNull { it.lastSequenceNumber } ?: 0
        
        // Allow up to 100 events of drift
        return maxSeq - minSeq <= 100
    }
    
    /**
     * Gets all registered processes.
     */
    fun getProcesses(): List<ProcessState> = processStates.values.toList()
    
    /**
     * Gets the shared secret for IPC encryption.
     * This should only be called from within the same app.
     */
    internal fun getSharedSecret(): ByteArray = sharedSecret.copyOf()
    
    /**
     * Clears all process registrations. For testing.
     */
    internal fun clear() {
        processStates.clear()
        processTokens.clear()
        coordinatorPid = -1
    }
}

/**
 * Handles replay of events from other processes with authentication.
 */
object CrossProcessReplayHandler {
    
    data class CrossProcessEvent(
        val sourcePid: Int,
        val sequenceNumber: Long,
        val eventType: String,
        val data: ByteArray,
        val authSignature: ByteArray  // HMAC signature for verification
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CrossProcessEvent) return false
            return sequenceNumber == other.sequenceNumber && sourcePid == other.sourcePid
        }
        
        override fun hashCode(): Int = 31 * sourcePid + sequenceNumber.toInt()
        
        /**
         * Verifies the event's authenticity.
         */
        fun verify(sharedSecret: ByteArray): Boolean {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
            val message = "$sourcePid:$sequenceNumber:$eventType"
            val expectedSignature = mac.doFinal(message.toByteArray())
            return expectedSignature.contentEquals(authSignature)
        }
    }
    
    private val pendingEvents = java.util.Collections.synchronizedList(mutableListOf<CrossProcessEvent>())
    
    /**
     * Creates a signed event for cross-process communication.
     */
    fun createSignedEvent(
        sequenceNumber: Long,
        eventType: String,
        data: ByteArray
    ): CrossProcessEvent {
        val pid = android.os.Process.myPid()
        val sharedSecret = MultiProcessManager.getSharedSecret()
        
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
        val message = "$pid:$sequenceNumber:$eventType"
        val signature = mac.doFinal(message.toByteArray())
        
        return CrossProcessEvent(
            sourcePid = pid,
            sequenceNumber = sequenceNumber,
            eventType = eventType,
            data = data,
            authSignature = signature
        )
    }
    
    /**
     * Receives an event from another process with verification.
     */
    fun receiveEvent(event: CrossProcessEvent): Boolean {
        // Verify the event's authenticity
        val sharedSecret = MultiProcessManager.getSharedSecret()
        if (!event.verify(sharedSecret)) {
            Log.w("Chronos", "Rejected cross-process event: invalid signature from PID ${event.sourcePid}")
            return false
        }
        
        pendingEvents.add(event)
        Log.d("Chronos", "Received verified cross-process event from PID ${event.sourcePid}")
        return true
    }
    
    /**
     * Gets pending events for replay.
     */
    fun getPendingEvents(): List<CrossProcessEvent> {
        val events = pendingEvents.sortedBy { it.sequenceNumber }
        pendingEvents.clear()
        return events
    }
}
