package com.chronos.agent.recording

import android.util.Log
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all state sources in the application.
 * 
 * State sources must be explicitly registered - no implicit global instrumentation.
 */
object StateSourceRegistry {
    
    private val sources = ConcurrentHashMap<String, StateSource<*>>()
    private val listeners = Collections.synchronizedList(mutableListOf<RegistryListener>())
    
    interface RegistryListener {
        fun onSourceRegistered(source: StateSource<*>)
        fun onSourceUnregistered(sourceId: String)
    }
    
    /**
     * Registers a state source for recording.
     * 
     * @param source The state source to register.
     * @throws IllegalArgumentException if a source with the same ID is already registered.
     */
    fun register(source: StateSource<*>) {
        val existing = sources.putIfAbsent(source.sourceId, source)
        if (existing != null) {
            throw IllegalArgumentException(
                "StateSource with ID '${source.sourceId}' is already registered."
            )
        }
        
        Log.i("Chronos", "Registered StateSource: ${source.sourceId} [${source.getDeterminismClass()}]")
        synchronized(listeners) {
            listeners.forEach { it.onSourceRegistered(source) }
        }
    }
    
    /**
     * Unregisters a state source.
     */
    fun unregister(sourceId: String) {
        sources.remove(sourceId)
        Log.i("Chronos", "Unregistered StateSource: $sourceId")
        synchronized(listeners) {
            listeners.forEach { it.onSourceUnregistered(sourceId) }
        }
    }
    
    /**
     * Returns all registered sources.
     */
    fun getAllSources(): Collection<StateSource<*>> = sources.values.toList()
    
    /**
     * Returns a specific source by ID.
     */
    fun getSource(sourceId: String): StateSource<*>? = sources[sourceId]
    
    /**
     * Returns sources filtered by determinism class.
     */
    fun getSourcesByClass(vararg classes: DeterminismClass): List<StateSource<*>> {
        val classSet = classes.toSet()
        return sources.values.filter { it.getDeterminismClass() in classSet }
    }
    
    /**
     * Checks if any registered source is Class D (unsafe).
     * If true, replay must be blocked.
     */
    fun hasUnsafeSources(): Boolean {
        return sources.values.any { it.getDeterminismClass() == DeterminismClass.CLASS_D_UNSAFE }
    }
    
    fun addListener(listener: RegistryListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: RegistryListener) {
        listeners.remove(listener)
    }
    
    /**
     * Clears all registered sources. For testing only.
     */
    internal fun clear() {
        sources.clear()
    }
}
