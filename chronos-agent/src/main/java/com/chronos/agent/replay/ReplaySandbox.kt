package com.chronos.agent.replay

import android.util.Log

/**
 * Replay Sandbox - Blocks all external I/O during replay.
 * 
 * The sandbox ensures replay is hermetic by blocking:
 * - Network access
 * - Database operations
 * - File system writes
 * - System services
 */
object ReplaySandbox {
    
    @Volatile
    private var isActive = false
    private val blockedOperations = java.util.Collections.synchronizedList(mutableListOf<BlockedOperation>())
    
    data class BlockedOperation(
        val type: OperationType,
        val description: String,
        val timestamp: Long
    )
    
    enum class OperationType {
        NETWORK,
        DATABASE,
        FILE_SYSTEM,
        SYSTEM_SERVICE,
        IPC
    }
    
    /**
     * Activates the sandbox. All I/O operations will be blocked.
     */
    fun activate() {
        if (isActive) return
        isActive = true
        blockedOperations.clear()
        Log.i("Chronos", "🔒 Replay Sandbox ACTIVATED - All I/O is blocked")
    }
    
    /**
     * Deactivates the sandbox. Normal I/O operations resume.
     */
    fun deactivate() {
        if (!isActive) return
        isActive = false
        Log.i("Chronos", "🔓 Replay Sandbox DEACTIVATED - I/O restored")
        
        if (blockedOperations.isNotEmpty()) {
            Log.i("Chronos", "   Blocked ${blockedOperations.size} operations during replay")
        }
    }
    
    fun isActive(): Boolean = isActive
    
    /**
     * Checks if an operation should be blocked.
     * Call this before any I/O operation during replay.
     */
    fun shouldBlock(type: OperationType, description: String): Boolean {
        if (!isActive) return false
        
        blockedOperations.add(BlockedOperation(type, description, System.currentTimeMillis()))
        Log.d("Chronos", "🚫 Blocked: [$type] $description")
        return true
    }
    
    /**
     * Gets all operations that were blocked during replay.
     */
    fun getBlockedOperations(): List<BlockedOperation> = blockedOperations.toList()
    
    /**
     * Network interceptor for OkHttp/Retrofit.
     */
    object NetworkGuard {
        fun checkRequest(url: String): Boolean {
            return shouldBlock(OperationType.NETWORK, "HTTP request to: $url")
        }
    }
    
    /**
     * Database guard for Room/SQLite.
     */
    object DatabaseGuard {
        fun checkWrite(table: String): Boolean {
            return shouldBlock(OperationType.DATABASE, "Write to table: $table")
        }
        
        fun checkRead(table: String): Boolean {
            // Reads are allowed but logged
            if (isActive) {
                Log.d("Chronos", "📖 DB Read allowed: $table")
            }
            return false
        }
    }
    
    /**
     * File system guard.
     */
    object FileSystemGuard {
        fun checkWrite(path: String): Boolean {
            return shouldBlock(OperationType.FILE_SYSTEM, "File write: $path")
        }
        
        fun checkRead(path: String): Boolean {
            // Reads are allowed but logged
            if (isActive) {
                Log.d("Chronos", "📖 File read allowed: $path")
            }
            return false
        }
    }
    
    /**
     * System service guard.
     */
    object SystemServiceGuard {
        fun checkCall(serviceName: String, methodName: String): Boolean {
            return shouldBlock(OperationType.SYSTEM_SERVICE, "$serviceName.$methodName()")
        }
    }
}
