package com.chronos.agent

import android.content.Context

/**
 * No-op implementation of Chronos for release builds.
 * This ensures that if Chronos code is accidentally called in release, it does nothing.
 * Ideally, this artifact is used via 'releaseImplementation'.
 */
object Chronos {
    
    fun initialize(context: Context) {
        // No-op
    }
}
