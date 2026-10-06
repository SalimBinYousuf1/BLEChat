package com.example.salim.core.crypto

import android.content.Context
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom

object CryptoManager {
    private const val IDENTITY_FILE_NAME = "salim_identity.enc"
    private val secureRandom = SecureRandom()

    /**
     * Generates a fresh long-term identity (Ed25519 + X25519 + Short Peer ID)
     * and saves it wrapped via Android Keystore.
     */
    fun createIdentity(nickname: String): IdentityKeys {
        // 1. Generate Ed25519 keypair for signing
        val edGen = Ed25519KeyPairGenerator()
        edGen.init(Ed25519KeyGenerationParameters(secureRandom))
        val edPair = edGen.generateKeyPair()
        val edPriv = (edPair.private as Ed25519PrivateKeyParameters).encoded
        val edPub = (edPair.public as Ed25519PublicKeyParameters).encoded

        // 2. Generate X25519 keypair for key agreement
        val xGen = X25519KeyPairGenerator()
        xGen.init(X25519KeyGenerationParameters(secureRandom))
        val xPair = xGen.generateKeyPair()
        val xPriv = (xPair.private as X25519PrivateKeyParameters).encoded
        val xPub = (xPair.public as X25519PublicKeyParameters).encoded

        // 3. Derive 8-byte short peer ID from SHA256(edPub)
        val digest = SHA256Digest()
        digest.update(edPub, 0, edPub.size)
        val hash = ByteArray(digest.digestSize)
        digest.doFinal(hash, 0)
        val peerId = ByteArray(8)
        System.arraycopy(hash, 0, peerId, 0, 8)

        return IdentityKeys(
            peerId = peerId,
            nickname = nickname.trim().ifEmpty { "Salim Peer" },
            ed25519PublicKey = edPub,
            ed25519PrivateKey = edPriv,
            x25519PublicKey = xPub,
            x25519PrivateKey = xPriv
        )
    }

    /**
     * Signs data using Ed25519 private key.
     * Returns 64-byte signature.
     */
    fun sign(data: ByteArray, privateKeyEncoded: ByteArray): ByteArray {
        val privKey = Ed25519PrivateKeyParameters(privateKeyEncoded, 0)
        val signer = Ed25519Signer()
        signer.init(true, privKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    /**
     * Verifies 64-byte Ed25519 signature against data and public key.
     */
    fun verify(data: ByteArray, signature: ByteArray, publicKeyEncoded: ByteArray): Boolean {
        if (signature.size != 64 || publicKeyEncoded.size != 32) return false
        return try {
            val pubKey = Ed25519PublicKeyParameters(publicKeyEncoded, 0)
            val verifier = Ed25519Signer()
            verifier.init(false, pubKey)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Performs X25519 ECDH agreement between my private key and peer's public key.
     * Returns 32-byte shared secret.
     */
    fun calculateX25519SharedSecret(myPrivEncoded: ByteArray, peerPubEncoded: ByteArray): ByteArray {
        require(myPrivEncoded.size == 32 && peerPubEncoded.size == 32) { "Keys must be 32 bytes" }
        val myPriv = X25519PrivateKeyParameters(myPrivEncoded, 0)
        val peerPub = X25519PublicKeyParameters(peerPubEncoded, 0)
        val agreement = X25519Agreement()
        agreement.init(myPriv)
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(peerPub, secret, 0)
        return secret
    }

    /**
     * Derives a symmetric encryption key from X25519 shared secret using HKDF-SHA256.
     */
    fun deriveSessionKey(sharedSecret: ByteArray, info: ByteArray = "Salim-v1-Session".toByteArray()): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, null, info))
        val derivedKey = ByteArray(32) // 256-bit key
        hkdf.generateBytes(derivedKey, 0, derivedKey.size)
        return derivedKey
    }

    /**
     * Encrypts plaintext with ChaCha20-Poly1305 AEAD.
     * Output format: [12 bytes nonce] + [ciphertext + 16 bytes tag]
     */
    fun encryptChaCha20Poly1305(
        plaintext: ByteArray,
        key: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes" }
        val nonce = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = ChaCha20Poly1305()
        val params = AEADParameters(KeyParameter(key), 128, nonce, associatedData)
        cipher.init(true, params)

        val outSize = cipher.getOutputSize(plaintext.size)
        val out = ByteArray(12 + outSize)
        System.arraycopy(nonce, 0, out, 0, 12)

        var len = cipher.processBytes(plaintext, 0, plaintext.size, out, 12)
        len += cipher.doFinal(out, 12 + len)

        return if (12 + len == out.size) out else out.copyOf(12 + len)
    }

    /**
     * Decrypts [12 bytes nonce] + [ciphertext + 16 bytes tag] with ChaCha20-Poly1305.
     */
    fun decryptChaCha20Poly1305(
        cipherWithNonce: ByteArray,
        key: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray? {
        if (cipherWithNonce.size < 12 + 16) return null
        return try {
            val nonce = ByteArray(12)
            System.arraycopy(cipherWithNonce, 0, nonce, 0, 12)
            val ciphertextLen = cipherWithNonce.size - 12

            val cipher = ChaCha20Poly1305()
            val params = AEADParameters(KeyParameter(key), 128, nonce, associatedData)
            cipher.init(false, params)

            val out = ByteArray(cipher.getOutputSize(ciphertextLen))
            val len1 = cipher.processBytes(cipherWithNonce, 12, ciphertextLen, out, 0)
            val len2 = cipher.doFinal(out, len1)
            val total = len1 + len2
            if (total == out.size) out else out.copyOf(total)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes safety number (32 digits formatted as 8 groups of 4) from two Ed25519 public keys.
     * Deterministic regardless of order of keys.
     */
    fun computeSafetyNumber(pubKey1: ByteArray, pubKey2: ByteArray): String {
        val sorted = listOf(pubKey1, pubKey2).sortedWith { a, b ->
            for (i in 0 until minOf(a.size, b.size)) {
                val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
                if (diff != 0) return@sortedWith diff
            }
            a.size - b.size
        }

        val digest = SHA256Digest()
        digest.update(sorted[0], 0, sorted[0].size)
        digest.update(sorted[1], 0, sorted[1].size)
        val hash = ByteArray(digest.digestSize)
        digest.doFinal(hash, 0)

        // Convert first 16 bytes of hash into 32 numeric digits
        val sb = StringBuilder()
        for (i in 0 until 16) {
            val num = (hash[i].toInt() and 0xFF) % 100
            sb.append("%02d".format(num))
        }
        val rawDigits = sb.toString().padEnd(32, '0').substring(0, 32)
        // Group into 8 blocks of 4 digits
        return rawDigits.chunked(4).joinToString(" ")
    }

    /**
     * Persists encrypted identity to app-private storage.
     */
    fun saveIdentity(context: Context, identity: IdentityKeys) {
        val nicknameBytes = identity.nickname.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(
            8 + 2 + nicknameBytes.size + 32 + identity.ed25519PrivateKey.size + 32 + 32
        )
        buffer.put(identity.peerId)
        buffer.putShort(nicknameBytes.size.toShort())
        buffer.put(nicknameBytes)
        buffer.put(identity.ed25519PublicKey)
        buffer.put(identity.ed25519PrivateKey)
        buffer.put(identity.x25519PublicKey)
        buffer.put(identity.x25519PrivateKey)

        val encrypted = CryptoKeystore.encrypt(buffer.array())
        val file = File(context.filesDir, IDENTITY_FILE_NAME)
        file.writeBytes(encrypted)
    }

    /**
     * Loads identity if already generated and saved.
     */
    fun loadIdentity(context: Context): IdentityKeys? {
        val file = File(context.filesDir, IDENTITY_FILE_NAME)
        if (!file.exists()) return null

        return try {
            val encrypted = file.readBytes()
            val plaintext = CryptoKeystore.decrypt(encrypted)
            val buffer = ByteBuffer.wrap(plaintext)

            val peerId = ByteArray(8).also { buffer.get(it) }
            val nicknameLen = buffer.short.toInt() and 0xFFFF
            val nicknameBytes = ByteArray(nicknameLen).also { buffer.get(it) }
            val nickname = String(nicknameBytes, Charsets.UTF_8)

            val edPub = ByteArray(32).also { buffer.get(it) }
            val edPriv = ByteArray(buffer.remaining() - 32 - 32).also { buffer.get(it) }
            val xPub = ByteArray(32).also { buffer.get(it) }
            val xPriv = ByteArray(32).also { buffer.get(it) }

            IdentityKeys(
                peerId = peerId,
                nickname = nickname,
                ed25519PublicKey = edPub,
                ed25519PrivateKey = edPriv,
                x25519PublicKey = xPub,
                x25519PrivateKey = xPriv
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Panic wipe helper: zeroes and deletes the identity file and Keystore master key.
     */
    fun wipeAllKeys(context: Context) {
        try {
            val file = File(context.filesDir, IDENTITY_FILE_NAME)
            if (file.exists()) {
                val zeroes = ByteArray(file.length().toInt())
                file.writeBytes(zeroes)
                file.delete()
            }
            CryptoKeystore.deleteMasterKey()
        } catch (_: Exception) {
        }
    }
}
