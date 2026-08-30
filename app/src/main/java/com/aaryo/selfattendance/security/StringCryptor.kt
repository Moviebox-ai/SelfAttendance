package com.aaryo.selfattendance.security

import android.util.Base64
import java.nio.charset.StandardCharsets

/**
 * StringCryptor — Bytecode String Encryption & Obfuscation Helper.
 *
 * Prevents decompilers (JADX, APKTool, Bytecode Viewer) from searching
 * or reading sensitive strings, API endpoints, encryption keys, and database
 * collection names in plaintext.
 */
object StringCryptor {

    private val RUNTIME_KEY = byteArrayOf(
        0x5A.toByte(), 0x3F.toByte(), 0x7E.toByte(), 0x12.toByte(),
        0x9B.toByte(), 0x4C.toByte(), 0x2D.toByte(), 0x88.toByte(),
        0x33.toByte(), 0xA1.toByte(), 0x6E.toByte(), 0xF0.toByte(),
        0x1B.toByte(), 0x77.toByte(), 0x4A.toByte(), 0x93.toByte()
    )

    /**
     * Decrypts an encrypted string at runtime using dynamic key-stream XOR.
     */
    fun decrypt(base64Payload: String): String {
        return try {
            val encryptedBytes = Base64.decode(base64Payload, Base64.DEFAULT)
            val result = ByteArray(encryptedBytes.size)
            for (i in encryptedBytes.indices) {
                val keyByte = RUNTIME_KEY[i % RUNTIME_KEY.size]
                result[i] = (encryptedBytes[i].toInt() xor keyByte.toInt()).toByte()
            }
            String(result, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Encrypts a string (utility for developers during build-time / asset preparation).
     */
    fun encrypt(plainText: String): String {
        val inputBytes = plainText.toByteArray(StandardCharsets.UTF_8)
        val result = ByteArray(inputBytes.size)
        for (i in inputBytes.indices) {
            val keyByte = RUNTIME_KEY[i % RUNTIME_KEY.size]
            result[i] = (inputBytes[i].toInt() xor keyByte.toInt()).toByte()
        }
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }
}
