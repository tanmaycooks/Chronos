package com.chronos.agent.ipc

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles secure IPC between the Agent and the Studio Plugin via ADB forwarding.
 * Uses an abstract namespace LocalSocket.
 *
 * Security Model:
 * 1. Randomly generated AUTH_TOKEN per session.
 * 2. Token retrieved via secure API (not logged).
 * 3. Incoming connections must send the AUTH_TOKEN immediately.
 * 4. All subsequent communication is encrypted with AES-GCM.
 * 
 * Performance:
 * - Cipher instances are cached per-thread for high throughput
 * - Rate limiting prevents DoS attacks
 */
class SecureIPCServer(private val socketName: String = "chronos_ipc") {

    private val running = AtomicBoolean(false)
    private var serverSocket: LocalServerSocket? = null
    
    // Authenticated Token for this session - NEVER log this directly
    private val authToken = UUID.randomUUID().toString()
    
    // Session encryption key - generated after successful authentication
    private val sessionKey: SecretKey by lazy {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, SecureRandom())
        keyGen.generateKey()
    }
    
    // Cached SecureRandom for IV generation (thread-safe)
    private val secureRandom = SecureRandom()
    
    // Thread-local cipher instances for performance
    private val encryptCipherCache = ThreadLocal<Cipher>()
    private val decryptCipherCache = ThreadLocal<Cipher>()
    
    /**
     * Returns the auth token for this session.
     * The Studio plugin should retrieve this via ADB or other secure channel.
     * 
     * SECURITY: This token should never be logged to Logcat.
     */
    fun getAuthToken(): String = authToken
    
    /**
     * Returns the session key bytes for the Studio plugin.
     * This should be transmitted securely (e.g., via ADB).
     */
    fun getSessionKeyBytes(): ByteArray = sessionKey.encoded

    fun start() {
        if (running.getAndSet(true)) return

        Log.i("Chronos", "Starting Secure IPC Server on: $socketName")
        Log.i("Chronos", "Auth token available via Chronos.getIPCAuthToken()")
        Log.i("Chronos", "IPC encryption: AES-256-GCM enabled")

        Thread {
            try {
                serverSocket = LocalServerSocket(socketName)
                while (running.get()) {
                    val clientSocket = serverSocket?.accept()
                    if (clientSocket != null) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e("Chronos", "IPC Server failed", e)
            }
        }.start()
    }
    
    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("Chronos", "Error closing IPC server", e)
        }
    }

    private fun handleClient(socket: LocalSocket) {
        Thread {
            try {
                val input = DataInputStream(socket.inputStream)
                val output = DataOutputStream(socket.outputStream)

                // 1. Handshake: Verify Token
                val receivedToken = input.readUTF()
                if (receivedToken != authToken) {
                    Log.w("Chronos", "IPC Auth Failed. Closing connection.")
                    socket.close()
                    return@Thread
                }

                // 2. Send session key (encrypted with derived key from auth token)
                val derivedKey = deriveKeyFromToken(authToken)
                val encryptedSessionKey = encryptWithKey(sessionKey.encoded, derivedKey)
                output.writeInt(encryptedSessionKey.size)
                output.write(encryptedSessionKey)
                
                // 3. Acknowledge
                output.writeUTF("OK")
                output.flush()
                
                Log.i("Chronos", "Studio connected with encrypted channel.")
                
                // 4. Handle encrypted communication
                handleEncryptedSession(input, output, socket)
                
            } catch (e: Exception) {
                Log.e("Chronos", "Client handshake error", e)
            }
        }.start()
    }
    
    // Rate limiting for DoS protection
    private val messageCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val lastResetTime = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
    private val maxMessagesPerMinute = 1000
    
    private fun handleEncryptedSession(
        input: DataInputStream, 
        output: DataOutputStream,
        socket: LocalSocket
    ) {
        try {
            while (running.get() && !socket.isClosed) {
                // Rate limiting: max 1000 messages per minute
                val now = System.currentTimeMillis()
                if (now - lastResetTime.get() > 60000) {
                    messageCount.set(0)
                    lastResetTime.set(now)
                }
                
                if (messageCount.incrementAndGet() > maxMessagesPerMinute) {
                    Log.w("Chronos", "Rate limit exceeded, closing connection")
                    socket.close()
                    return
                }
                
                // Read encrypted message length
                val length = input.readInt()
                if (length <= 0 || length > 1024 * 1024) { // Max 1MB
                    Log.w("Chronos", "Invalid message length: $length")
                    break
                }
                
                // Read encrypted message
                val encryptedData = ByteArray(length)
                input.readFully(encryptedData)
                
                // Decrypt and process
                val decryptedData = decrypt(encryptedData)
                val response = processMessage(decryptedData)
                
                // Encrypt and send response
                val encryptedResponse = encrypt(response)
                output.writeInt(encryptedResponse.size)
                output.write(encryptedResponse)
                output.flush()
            }
        } catch (e: Exception) {
            Log.d("Chronos", "Session ended: ${e.message}")
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }
    
    private fun processMessage(data: ByteArray): ByteArray {
        // Placeholder - would process protobuf messages
        return "ACK".toByteArray()
    }
    
    /**
     * Gets or creates a cached Cipher for encryption.
     */
    private fun getEncryptCipher(): Cipher {
        var cipher = encryptCipherCache.get()
        if (cipher == null) {
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
            encryptCipherCache.set(cipher)
        }
        return cipher
    }
    
    /**
     * Gets or creates a cached Cipher for decryption.
     */
    private fun getDecryptCipher(): Cipher {
        var cipher = decryptCipherCache.get()
        if (cipher == null) {
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipherCache.set(cipher)
        }
        return cipher
    }
    
    /**
     * Encrypts data using AES-GCM with cached cipher.
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = getEncryptCipher()
        val iv = ByteArray(12) // 96-bit IV for GCM
        secureRandom.nextBytes(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        
        // Prepend IV to ciphertext
        return iv + ciphertext
    }
    
    /**
     * Decrypts data using AES-GCM with cached cipher.
     */
    fun decrypt(ciphertext: ByteArray): ByteArray {
        val cipher = getDecryptCipher()
        
        // Extract IV from first 12 bytes
        val iv = ciphertext.sliceArray(0 until 12)
        val actualCiphertext = ciphertext.sliceArray(12 until ciphertext.size)
        
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(actualCiphertext)
    }
    
    /**
     * Derives an AES key from the auth token for initial key exchange.
     */
    private fun deriveKeyFromToken(token: String): SecretKey {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
        return SecretKeySpec(hash, "AES")
    }
    
    /**
     * Encrypts data with a specific key using cached cipher.
     */
    private fun encryptWithKey(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = getEncryptCipher()
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        
        return iv + ciphertext
    }
}
