package com.ezzy.vault.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Turns an export password into an AES-256 key and seals or opens a `.ezzy` file with it.
 *
 * Unlike every other key in the app, this one is never hardware-bound — the whole point of an
 * export is that it can be opened on a different phone, and an Android Keystore key can never
 * leave the device it was made on. Security instead comes from PBKDF2 (a deliberately slow
 * hash, so guessing passwords is expensive) and AES-256-GCM, whose authentication tag is what
 * tells a wrong password apart from a right one — and a damaged file apart from a whole one —
 * without ever needing to store the password itself anywhere.
 */
object BackupCrypto {

    /** The password did not open the file — wrong password, most likely. */
    class WrongPasswordException : Exception("Incorrect password")

    /** The file's header does not look like an EZZY export at all. */
    class InvalidFileException : Exception("This is not an EZZY backup file")

    /** The password was right, but what came out was not a genuine backup. */
    class CorruptDataException : Exception("This backup file's contents look damaged")

    private val MAGIC = byteArrayOf('E'.code.toByte(), 'Z'.code.toByte(), 'Z'.code.toByte(), '1'.code.toByte())
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** True once the header is legible — checked before the password is even asked for. */
    fun looksLikeBackup(fileBytes: ByteArray): Boolean =
        fileBytes.size > MAGIC.size + SALT_LENGTH + IV_LENGTH &&
            fileBytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** @return `MAGIC || salt || iv || ciphertext` — everything [decrypt] needs is in the file. */
    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt))
        val iv = cipher.iv
        val body = cipher.doFinal(plain)

        return ByteBuffer.allocate(MAGIC.size + salt.size + iv.size + body.size).apply {
            put(MAGIC)
            put(salt)
            put(iv)
            put(body)
        }.array()
    }

    fun decrypt(fileBytes: ByteArray, password: String): ByteArray {
        if (!looksLikeBackup(fileBytes)) throw InvalidFileException()

        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_LENGTH
        val bodyStart = ivStart + IV_LENGTH
        val salt = fileBytes.copyOfRange(saltStart, ivStart)
        val iv = fileBytes.copyOfRange(ivStart, bodyStart)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        return try {
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(fileBytes, bodyStart, fileBytes.size - bodyStart)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: BadPaddingException) {
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
