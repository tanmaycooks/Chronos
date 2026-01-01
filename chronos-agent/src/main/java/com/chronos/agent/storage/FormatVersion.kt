package com.chronos.agent.storage

import android.os.Build

/**
 * Recording format versioning for compatibility.
 * 
 * Rules:
 * - Backward compatibility for N-1 minor versions
 * - Patch versions always compatible
 * - Breaking changes only in major versions
 */
data class FormatVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) {
    companion object {
        val CURRENT = FormatVersion(1, 0, 0)
        
        fun parse(versionString: String): FormatVersion? {
            val parts = versionString.split(".")
            if (parts.size != 3) return null
            return try {
                FormatVersion(
                    major = parts[0].toInt(),
                    minor = parts[1].toInt(),
                    patch = parts[2].toInt()
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
    
    override fun toString(): String = "$major.$minor.$patch"
    
    fun isCompatibleWith(other: FormatVersion): Boolean {
        // Same major version required
        if (major != other.major) return false
        
        // Within N-1 minor versions
        if (kotlin.math.abs(minor - other.minor) > 1) return false
        
        // Patch versions always compatible
        return true
    }
}

/**
 * Recording header containing all version information.
 */
data class RecordingHeader(
    val formatVersion: FormatVersion,
    val chronosVersion: String,
    val androidSdkVersion: Int,
    val kotlinVersion: String,
    val createdAt: Long,
    val appPackage: String,
    val processName: String,
    val checksum: String?
) {
    companion object {
        fun create(appPackage: String, processName: String): RecordingHeader {
            return RecordingHeader(
                formatVersion = FormatVersion.CURRENT,
                chronosVersion = "0.1.0",
                androidSdkVersion = Build.VERSION.SDK_INT,
                kotlinVersion = KotlinVersion.CURRENT.toString(),
                createdAt = System.currentTimeMillis(),
                appPackage = appPackage,
                processName = processName,
                checksum = null
            )
        }
    }
}

/**
 * Compatibility checker for recordings.
 */
object CompatibilityChecker {
    
    data class CompatibilityResult(
        val isCompatible: Boolean,
        val warnings: List<String>,
        val errors: List<String>
    )
    
    fun check(header: RecordingHeader): CompatibilityResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        // Check format version
        if (!FormatVersion.CURRENT.isCompatibleWith(header.formatVersion)) {
            errors.add("Incompatible format version: ${header.formatVersion} (current: ${FormatVersion.CURRENT})")
        }
        
        // Check SDK version
        val currentSdk = Build.VERSION.SDK_INT
        if (header.androidSdkVersion > currentSdk) {
            warnings.add("Recording from newer SDK (${header.androidSdkVersion}) than current ($currentSdk)")
        }
        
        // Check Kotlin version (informational)
        if (header.kotlinVersion != KotlinVersion.CURRENT.toString()) {
            warnings.add("Kotlin version mismatch: ${header.kotlinVersion} vs ${KotlinVersion.CURRENT}")
        }
        
        return CompatibilityResult(
            isCompatible = errors.isEmpty(),
            warnings = warnings,
            errors = errors
        )
    }
}
