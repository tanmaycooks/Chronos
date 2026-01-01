package com.chronos.agent.contract

import com.chronos.agent.recording.DeterminismClass

/**
 * Chronos Annotation Contracts
 * 
 * These annotations allow developers to explicitly declare the determinism
 * characteristics of their state classes, enabling Chronos to make informed
 * decisions about recording and replay safety.
 */

/**
 * Marks a class as Class A: Guaranteed Deterministic.
 * 
 * Use for:
 * - Pure data classes with immutable properties
 * - Sealed classes without logic
 * - Primitive wrappers
 * - Immutable collections
 * 
 * Requirements:
 * - Must be a data class, sealed class, or enum
 * - All properties must be `val` (immutable)
 * - No side-effect-producing methods
 * 
 * Example:
 * ```kotlin
 * @ChronosDeterministic
 * data class UserState(
 *     val id: String,
 *     val name: String,
 *     val isLoggedIn: Boolean
 * )
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosDeterministic

/**
 * Marks a class as Class B: Runtime Verifiable.
 * 
 * Use for:
 * - StateFlow/MutableStateFlow wrappers
 * - LiveData holders
 * - Compose MutableState
 * - Any state that can be verified at runtime
 * 
 * Requirements:
 * - Must implement StateSource<T>
 * - State must be observable and verifiable
 * 
 * Example:
 * ```kotlin
 * @ChronosVerifiable
 * class ViewModelState(private val flow: StateFlow<UiState>) : StateSource<UiState> {
 *     override fun captureState() = flow.value
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosVerifiable

/**
 * Marks a class as Class C: Conditionally Safe.
 * 
 * USE WITH CAUTION. This annotation requires developer attestation that
 * the state source is safe for replay despite not meeting Class A/B criteria.
 * 
 * Chronos will:
 * - Log warnings when replaying with Class C sources
 * - Include in replay with reduced confidence score
 * - Track this annotation in audit logs
 * 
 * Requirements:
 * - Must provide a clear reason
 * - Should include author and review date for accountability
 * 
 * Example:
 * ```kotlin
 * @ChronosConditionalSafe(
 *     reason = "Legacy module with no external dependencies",
 *     author = "developer@company.com",
 *     reviewedDate = "2026-02-05"
 * )
 * class LegacyStateAdapter : StateSource<LegacyState> { ... }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosConditionalSafe(
    /** Explanation of why this source is considered safe. */
    val reason: String,
    
    /** Email or identifier of the developer who verified this. */
    val author: String = "",
    
    /** Date when this was reviewed (YYYY-MM-DD format). */
    val reviewedDate: String = ""
)

/**
 * Explicitly marks a class as Class D: Unsafe.
 * 
 * Use for:
 * - Network operations
 * - Database access
 * - File I/O
 * - System time/random values
 * - Hardware sensors
 * - Any non-deterministic data source
 * 
 * Effect:
 * - Replay is ALWAYS blocked when this source is present
 * - Recording continues but state is marked as non-replayable
 * 
 * Example:
 * ```kotlin
 * @ChronosUnsafe(reason = "Makes network calls")
 * class NetworkRepository : StateSource<NetworkState> { ... }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosUnsafe(
    /** Explanation of why this source is unsafe. */
    val reason: String
)

/**
 * Prevents sensitive data from being recorded.
 * 
 * When applied to a property, Chronos will replace the value with "[REDACTED]"
 * in all recordings.
 * 
 * Use for:
 * - Passwords
 * - Auth tokens
 * - API keys
 * - Personal identifiable information (PII)
 * - Any sensitive data
 * 
 * Example:
 * ```kotlin
 * data class UserCredentials(
 *     val username: String,
 *     
 *     @ChronosRedact
 *     val password: String,
 *     
 *     @ChronosRedact
 *     val authToken: String
 * )
 * // Recorded as: UserCredentials(username="john", password="[REDACTED]", authToken="[REDACTED]")
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosRedact

/**
 * Excludes a property from Chronos recording entirely.
 * 
 * Unlike @ChronosRedact which records "[REDACTED]", this annotation
 * prevents the property from appearing in recordings at all.
 * 
 * Use for:
 * - Debug-only caches
 * - Transient state
 * - High-frequency counters that would bloat recordings
 * - Non-serializable objects
 * 
 * Example:
 * ```kotlin
 * data class DebugState(
 *     val visibleItems: List<Item>,
 *     
 *     @ChronosIgnore
 *     val debugCache: MutableMap<String, Any>  // Not recorded at all
 * )
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ChronosIgnore

/**
 * Utility object for processing Chronos annotations.
 */
object ChronosAnnotations {
    
    /**
     * Gets the determinism class from annotations on a class.
     * Returns null if no Chronos annotation is present.
     */
    fun getDeterminismClass(clazz: Class<*>): DeterminismClass? {
        return when {
            clazz.isAnnotationPresent(ChronosDeterministic::class.java) -> 
                DeterminismClass.CLASS_A_GUARANTEED
            clazz.isAnnotationPresent(ChronosVerifiable::class.java) -> 
                DeterminismClass.CLASS_B_VERIFIABLE
            clazz.isAnnotationPresent(ChronosConditionalSafe::class.java) -> 
                DeterminismClass.CLASS_C_CONDITIONAL
            clazz.isAnnotationPresent(ChronosUnsafe::class.java) -> 
                DeterminismClass.CLASS_D_UNSAFE
            else -> null
        }
    }
    
    /**
     * Checks if a property should be redacted.
     */
    fun shouldRedact(clazz: Class<*>, propertyName: String): Boolean {
        return try {
            val field = clazz.getDeclaredField(propertyName)
            field.isAnnotationPresent(ChronosRedact::class.java)
        } catch (e: NoSuchFieldException) {
            false
        }
    }
    
    /**
     * Checks if a property should be ignored.
     */
    fun shouldIgnore(clazz: Class<*>, propertyName: String): Boolean {
        return try {
            val field = clazz.getDeclaredField(propertyName)
            field.isAnnotationPresent(ChronosIgnore::class.java)
        } catch (e: NoSuchFieldException) {
            false
        }
    }
    
    /**
     * Gets the conditional safe annotation details, if present.
     */
    fun getConditionalSafeDetails(clazz: Class<*>): ConditionalSafeDetails? {
        val annotation = clazz.getAnnotation(ChronosConditionalSafe::class.java)
            ?: return null
        return ConditionalSafeDetails(
            reason = annotation.reason,
            author = annotation.author,
            reviewedDate = annotation.reviewedDate
        )
    }
    
    data class ConditionalSafeDetails(
        val reason: String,
        val author: String,
        val reviewedDate: String
    )
}
