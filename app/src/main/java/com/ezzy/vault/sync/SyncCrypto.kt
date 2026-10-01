package com.ezzy.vault.sync

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The few primitives the browser link is built from, chosen so the Chrome extension can do the
 * exact same thing with nothing but WebCrypto: P-256 ECDH to agree a key, HKDF-SHA256 to shape
 * it, AES-256-GCM to seal every message, HMAC-SHA256 to prove both sides saw the same pairing
 * code. Public keys travel as SPKI DER, which both Java and WebCrypto read and write natively.
 */
object SyncCrypto {

    private val random = SecureRandom()

    fun randomBytes(length: Int): ByteArray = ByteArray(length).also { random.nextBytes(it) }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    /** RFC 5869, one-shot. WebCrypto's HKDF produces the same bytes for the same inputs. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val prk = hmac(salt, ikm)
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val take = minOf(previous.size, length - written)
            previous.copyInto(out, written, 0, take)
            written += take
            counter++
        }
        return out
    }

    fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"), random)
            generateKeyPair()
        }

    fun publicKeyFromSpki(spki: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))

    /** The raw x-coordinate of the shared point — what WebCrypto's ECDH deriveBits(256) gives. */
    fun ecdh(own: PrivateKey, peer: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(own)
            doPhase(peer, true)
            generateSecret()
        }

    /** @return the 12-byte IV and `ciphertext || tag`, the layout WebCrypto expects. */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = randomBytes(IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return iv to cipher.doFinal(plain)
    }

    /** Throws if the message was not sealed with [key] and [aad] — tampered, replayed elsewhere, or wrong key. */
    fun open(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
}
