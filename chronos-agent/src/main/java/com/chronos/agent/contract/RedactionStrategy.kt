package com.chronos.agent.contract

/**
 * Defines how to redact sensitive data from state snapshots before serialization.
 * 
 * Users can implement this to provide custom redaction logic.
 */
interface RedactionStrategy {
    
    /**
     * Called before a property value is serialized.
     * 
     * @param propertyName The name of the property.
     * @param value The raw value.
     * @return The sanitized value to record, or the original value if safe.
     */
    fun redact(propertyName: String, value: Any?): Any?
}

/**
 * Default redaction strategy with enhanced coverage.
 * 
 * Redacts:
 * - Properties with sensitive names (password, token, secret, key, etc.)
 * - String values that look like tokens or API keys
 */
object DefaultRedactionStrategy : RedactionStrategy {
    
    private val sensitivePatterns = listOf(
        "password", "token", "secret", "key", "auth",
        "credential", "api_key", "apikey", "access_token",
        "refresh_token", "bearer", "private", "session"
    )
    
    // Pattern for Base64-like strings (potential tokens)
    private val base64Pattern = Regex("^[A-Za-z0-9+/=]{20,}$")
    
    // Pattern for JWT tokens
    private val jwtPattern = Regex("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
    
    override fun redact(propertyName: String, value: Any?): Any? {
        if (value == null) return null
        
        val lowerName = propertyName.lowercase()
        
        // Check property name against sensitive patterns
        if (sensitivePatterns.any { lowerName.contains(it) }) {
            return "[REDACTED]"
        }
        
        // Check string values for sensitive data patterns
        if (value is String && value.length > 16) {
            // Check for Base64-like strings that might be tokens
            if (base64Pattern.matches(value)) {
                return "[POTENTIAL_TOKEN_REDACTED]"
            }
            
            // Check for JWT tokens
            if (jwtPattern.matches(value)) {
                return "[JWT_REDACTED]"
            }
            
            // Check for common secret prefixes
            val lowerValue = value.lowercase()
            if (lowerValue.startsWith("sk_") || 
                lowerValue.startsWith("pk_") ||
                lowerValue.startsWith("bearer ") ||
                lowerValue.startsWith("basic ")) {
                return "[API_KEY_REDACTED]"
            }
        }
        
        return value
    }
}
