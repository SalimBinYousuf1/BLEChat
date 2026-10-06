package com.example.salim.core

import com.example.salim.core.crypto.CryptoManager
import com.example.salim.core.model.PacketFlags
import com.example.salim.core.model.PacketType
import com.example.salim.core.model.SalimPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class SalimCoreTest {

    @Test
    fun packet_serialization_roundtrip() {
        val senderId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val recipientId = byteArrayOf(9, 10, 11, 12, 13, 14, 15, 16)
        val payload = "Hello Salim Mesh Network!".toByteArray(StandardCharsets.UTF_8)
        val signature = ByteArray(64) { (it * 3).toByte() }
        val flags = (PacketFlags.ENCRYPTED.toInt() or PacketFlags.SIGNED.toInt()).toByte()

        val packet = SalimPacket(
            type = PacketType.MESSAGE,
            flags = flags,
            ttl = 5,
            messageId = SalimPacket.randomMessageId(),
            senderId = senderId,
            recipientId = recipientId,
            timestamp = System.currentTimeMillis(),
            payload = payload,
            signature = signature
        )

        val encoded = packet.encode()
        assertTrue(encoded.isNotEmpty())

        val decoded = SalimPacket.decode(encoded)
        assertNotNull(decoded)
        assertEquals(PacketType.MESSAGE, decoded!!.type)
        assertEquals(packet.flags, decoded.flags)
        assertEquals(5.toByte(), decoded.ttl)
        assertArrayEquals(packet.messageId, decoded.messageId)
        assertArrayEquals(senderId, decoded.senderId)
        assertArrayEquals(recipientId, decoded.recipientId)
        assertArrayEquals(payload, decoded.payload)
        assertArrayEquals(signature, decoded.signature)
    }

    @Test
    fun crypto_key_generation_and_signatures() {
        val keysAlice = CryptoManager.createIdentity("Alice")
        assertEquals(8, keysAlice.peerId.size)
        assertEquals(32, keysAlice.ed25519PublicKey.size)
        assertEquals(32, keysAlice.x25519PublicKey.size)

        val testMessage = "Authentic mesh broadcast verification".toByteArray(StandardCharsets.UTF_8)
        val signature = CryptoManager.sign(testMessage, keysAlice.ed25519PrivateKey)
        assertEquals(64, signature.size)

        val verified = CryptoManager.verify(testMessage, signature, keysAlice.ed25519PublicKey)
        assertTrue(verified)
    }

    @Test
    fun crypto_key_exchange_and_aead_encryption() {
        val alice = CryptoManager.createIdentity("Alice")
        val bob = CryptoManager.createIdentity("Bob")

        // Alice derives shared secret using Bob's X25519 pubkey
        val sharedSecretAlice = CryptoManager.calculateX25519SharedSecret(alice.x25519PrivateKey, bob.x25519PublicKey)

        // Bob derives shared secret using Alice's X25519 pubkey
        val sharedSecretBob = CryptoManager.calculateX25519SharedSecret(bob.x25519PrivateKey, alice.x25519PublicKey)

        assertArrayEquals(sharedSecretAlice, sharedSecretBob)

        val salt = "SalimSaltTest".toByteArray()
        val keyAlice = CryptoManager.deriveSessionKey(sharedSecretAlice, salt)
        val keyBob = CryptoManager.deriveSessionKey(sharedSecretBob, salt)
        assertArrayEquals(keyAlice, keyBob)

        // Encrypt with ChaCha20-Poly1305 AEAD
        val plaintext = "Top secret mesh packet across 3 hops".toByteArray(StandardCharsets.UTF_8)
        val ciphertext = CryptoManager.encryptChaCha20Poly1305(plaintext, keyAlice)
        assertTrue(ciphertext.isNotEmpty())

        // Decrypt
        val decrypted = CryptoManager.decryptChaCha20Poly1305(ciphertext, keyBob)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun crypto_safety_number_calculation() {
        val alice = CryptoManager.createIdentity("Alice")
        val bob = CryptoManager.createIdentity("Bob")

        // Safety number must be symmetric regardless of who computes it
        val numAlice = CryptoManager.computeSafetyNumber(alice.ed25519PublicKey, bob.ed25519PublicKey)
        val numBob = CryptoManager.computeSafetyNumber(bob.ed25519PublicKey, alice.ed25519PublicKey)

        assertEquals(numAlice, numBob)
        assertEquals(8, numAlice.split(" ").size) // 8 blocks of 4 digits = 32 digits
    }
}
