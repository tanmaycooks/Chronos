package com.chronos.agent.contract

import com.google.protobuf.ByteString

/**
 * Defines the contract for converting objects into Protobuf-compatible bytes.
 */
interface SerializationStrategy {
    
    /**
     * Serializes a given object into bytes.
     * 
     * @param value The object to serialize.
     * @return The serialized bytes.
     * @throws Exception if serialization fails (should be caught by recorder).
     */
    fun serialize(value: Any?): ByteString
    
    /**
     * Returns the fully qualified type name of the serializer format (e.g., "json", "proto", "java-serial").
     */
    val formatId: String
}
