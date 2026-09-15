package com.example.data.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    const val SECRET_KEY = "OceanClient_Secure_Key_2024"
    private val SALTED_HEADER = "Salted__".toByteArray(StandardCharsets.US_ASCII)

    fun encrypt(text: String, passphrase: String = SECRET_KEY): String {
        try {
            val salt = ByteArray(8)
            SecureRandom().nextBytes(salt)
            val (key, iv) = deriveKeyAndIv(passphrase.toByteArray(StandardCharsets.UTF_8), salt, 32, 16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val cipherText = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))

            val combined = ByteArray(SALTED_HEADER.size + salt.size + cipherText.size)
            System.arraycopy(SALTED_HEADER, 0, combined, 0, SALTED_HEADER.size)
            System.arraycopy(salt, 0, combined, SALTED_HEADER.size, salt.size)
            System.arraycopy(cipherText, 0, combined, SALTED_HEADER.size + salt.size, cipherText.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return text
        }
    }

    fun decrypt(encryptedText: String, passphrase: String = SECRET_KEY): String {
        try {
            val decoded = Base64.decode(encryptedText, Base64.DEFAULT)
            if (decoded.size < 16) return encryptedText

            val header = Arrays.copyOfRange(decoded, 0, 8)
            if (!header.contentEquals(SALTED_HEADER)) {
                return encryptedText
            }

            val salt = Arrays.copyOfRange(decoded, 8, 16)
            val cipherText = Arrays.copyOfRange(decoded, 16, decoded.size)

            val (key, iv) = deriveKeyAndIv(passphrase.toByteArray(StandardCharsets.UTF_8), salt, 32, 16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val plainBytes = cipher.doFinal(cipherText)

            return String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return encryptedText
        }
    }

    private fun deriveKeyAndIv(password: ByteArray, salt: ByteArray, keyLen: Int, ivLen: Int): Pair<ByteArray, ByteArray> {
        val totalLen = keyLen + ivLen
        val derived = ByteArray(totalLen)
        var generated = 0
        var currentHash = ByteArray(0)
        val md = MessageDigest.getInstance("MD5")

        while (generated < totalLen) {
            md.reset()
            if (currentHash.isNotEmpty()) {
                md.update(currentHash)
            }
            md.update(password)
            md.update(salt)
            currentHash = md.digest()

            val toCopy = minOf(currentHash.size, totalLen - generated)
            System.arraycopy(currentHash, 0, derived, generated, toCopy)
            generated += toCopy
        }

        val key = Arrays.copyOfRange(derived, 0, keyLen)
        val iv = Arrays.copyOfRange(derived, keyLen, keyLen + ivLen)
        return Pair(key, iv)
    }
}
