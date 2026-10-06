package com.example

import com.example.salim.core.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class CryptoManagerTest {

    @Test
    fun testIdentityCreationAndSigning() {
        val identity = CryptoManager.createIdentity("Alice")
        assertEquals("Alice", identity.nickname)
        assertEquals(8, identity.peerId.size)
        assertEquals(32, identity.ed25519PublicKey.size)
        assertEquals(32, identity.x25519PublicKey.size)

        val message = "Salim mesh packet test payload".toByteArray(StandardCharsets.UTF_8)
        val signature = CryptoManager.sign(message, identity.ed25519PrivateKey)
        assertEquals(64, signature.size)

        val isValid = CryptoManager.verify(message, signature, identity.ed25519PublicKey)
        assertTrue("Signature should be valid", isValid)

        // Test tampered payload fails verification
        val tampered = "Tampered payload".toByteArray(StandardCharsets.UTF_8)
        val isTamperedValid = CryptoManager.verify(tampered, signature, identity.ed25519PublicKey)
        assertFalse("Tampered payload signature must be invalid", isTamperedValid)
    }

    @Test
    fun testX25519KeyExchangeAndChaCha20Poly1305() {
        val alice = CryptoManager.createIdentity("Alice")
        val bob = CryptoManager.createIdentity("Bob")

        // Alice computes secret with Bob's public key
        val aliceSecret = CryptoManager.calculateX25519SharedSecret(alice.x25519PrivateKey, bob.x25519PublicKey)
        // Bob computes secret with Alice's public key
        val bobSecret = CryptoManager.calculateX25519SharedSecret(bob.x25519PrivateKey, alice.x25519PublicKey)

        assertArrayEquals("ECDH shared secrets must match", aliceSecret, bobSecret)

        val aliceSessionKey = CryptoManager.deriveSessionKey(aliceSecret)
        val bobSessionKey = CryptoManager.deriveSessionKey(bobSecret)
        assertArrayEquals("Derived session keys must match", aliceSessionKey, bobSessionKey)

        val plaintext = "Top secret mesh message".toByteArray(StandardCharsets.UTF_8)
        val ciphertext = CryptoManager.encryptChaCha20Poly1305(plaintext, aliceSessionKey)

        val decrypted = CryptoManager.decryptChaCha20Poly1305(ciphertext, bobSessionKey)
        assertNotNull(decrypted)
        assertEquals("Top secret mesh message", String(decrypted!!, StandardCharsets.UTF_8))
    }

    @Test
    fun testSafetyNumberDeterminism() {
        val alice = CryptoManager.createIdentity("Alice")
        val bob = CryptoManager.createIdentity("Bob")

        val safetyNumber1 = CryptoManager.computeSafetyNumber(alice.ed25519PublicKey, bob.ed25519PublicKey)
        val safetyNumber2 = CryptoManager.computeSafetyNumber(bob.ed25519PublicKey, alice.ed25519PublicKey)

        assertEquals("Safety number must be symmetric and identical", safetyNumber1, safetyNumber2)
        assertEquals(8, safetyNumber1.split(" ").size) // 8 blocks of 4 digits
    }
}
